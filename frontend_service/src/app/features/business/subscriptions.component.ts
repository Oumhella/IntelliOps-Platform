import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, Observable } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import {
  CheckoutPreparationResponse,
  PageResponse,
  PlanResponse,
  SUBSCRIPTION_STATUSES,
  SubscriptionResponse,
  SubscriptionStatus,
  SubscriptionsApiService,
} from '../../core/api';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';
import { StripePaymentElementComponent } from './stripe-payment-element.component';

type SubscriptionPanel = 'subscribe' | 'details' | 'pause' | 'renew' | 'upgrade' | null;
type PendingCheckout = {
  readonly kind: 'subscribe' | 'renew' | 'upgrade';
  readonly paymentId: number;
  readonly subscriptionId?: number;
};

const PENDING_CHECKOUT_KEY = 'intelliops.pending-subscription-checkout';

@Component({
  selector: 'app-subscriptions',
  imports: [FormsModule, StripePaymentElementComponent],
  templateUrl: './subscriptions.component.html',
  styleUrl: './business-view.scss',
})
export class SubscriptionsComponent implements OnInit {
  private readonly api = inject(SubscriptionsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly feedback = inject(UiFeedbackService);
  readonly statuses = SUBSCRIPTION_STATUSES;
  readonly plans = signal<readonly PlanResponse[]>([]);
  readonly history = signal<PageResponse<SubscriptionResponse> | null>(null);
  readonly selected = signal<SubscriptionResponse | null>(null);
  readonly remainingDays = signal<number | null>(null);
  readonly checkoutPreparation = signal<CheckoutPreparationResponse | null>(null);
  readonly pendingCompletion = signal(false);
  private readonly currentSubscription = signal<SubscriptionResponse | null>(null);
  readonly current = computed(() => this.currentSubscription());
  readonly upgradePlans = computed(() => {
    const currentPrice = this.current()?.planAbonnement.prix ?? -1;
    return this.plans().filter((plan) => plan.estActif === 'ACTIF' && plan.prix > currentPrice);
  });

  panel: SubscriptionPanel = null;
  statusFilter: SubscriptionStatus | '' = '';
  busy = false;
  planId: number | null = null;
  checkoutKey = '';
  pauseReason = '';
  private pendingCheckout: PendingCheckout | null = null;
  readonly paymentReturnUrl = `${globalThis.location?.origin ?? ''}/app/subscriptions?stripe_return=1`;

  ngOnInit(): void {
    this.loadPlans();
    this.loadHistory();
    if (this.route.snapshot.queryParamMap.get('stripe_return') === '1' || this.readStoredCheckout()) {
      this.recoverStripeCheckout();
    }
  }

  loadPlans(): void {
    this.api.getPlans('ACTIF').subscribe({
      next: (plans) => this.plans.set(plans),
      error: (error) => this.feedback.error(error, 'Available plans could not be loaded.'),
    });
  }

  loadHistory(page = 0): void {
    this.api.searchSubscriptions(page, 20, this.statusFilter || undefined).subscribe({
      next: (history) => {
        this.history.set(history);
        if (!this.statusFilter) {
          const current = history.content.find((item) => item.statut === 'ACTIF' || item.statut === 'SUSPENDU') ?? null;
          this.currentSubscription.set(current);
          if (current) this.loadRemainingDays(current.idAbonnement);
        }
      },
      error: (error) => this.feedback.error(error, 'Workspace subscription history could not be loaded.'),
    });
  }

  open(panel: Exclude<SubscriptionPanel, null>, subscription?: SubscriptionResponse): void {
    this.selected.set(subscription ?? this.current());
    this.panel = panel;
    this.planId = null;
    this.checkoutPreparation.set(null);
    this.pendingCompletion.set(false);
    this.pendingCheckout = null;
    this.removeStoredCheckout();
    this.checkoutKey = globalThis.crypto?.randomUUID?.() ?? `subscription-${Date.now()}`;
    this.pauseReason = '';
    if (panel === 'details' && this.selected()) {
      this.api.getSubscriptionById(this.selected()!.idAbonnement).subscribe({
        next: (detail) => this.selected.set(detail),
        error: (error) => this.feedback.error(error),
      });
    }
  }

  close(): void {
    this.panel = null;
    this.selected.set(null);
    this.checkoutPreparation.set(null);
    this.pendingCheckout = null;
    this.pendingCompletion.set(false);
    this.removeStoredCheckout();
  }

  startActivation(): void {
    if (!this.planId) return;
    const plan = this.plans().find((candidate) => candidate.idPlan === this.planId);
    if (!plan) return;
    if (plan.prix <= 0) {
      this.run(this.api.subscribe({ planId: plan.idPlan }), 'Workspace subscription activated.');
      return;
    }
    this.preparePayment(
      this.api.prepareCheckout({ planId: plan.idPlan, idempotencyKey: this.checkoutKey }),
      { kind: 'subscribe', paymentId: 0 },
    );
  }

  pause(): void {
    const subscription = this.selected();
    if (!subscription || !this.pauseReason.trim()) return;
    this.run(this.api.suspend(subscription.idAbonnement, this.pauseReason.trim()), 'Workspace subscription paused.');
  }

  startRenewal(): void {
    const subscription = this.selected();
    if (!subscription) return;
    this.preparePayment(
      this.api.prepareRenewalCheckout(subscription.idAbonnement, { idempotencyKey: this.checkoutKey }),
      { kind: 'renew', paymentId: 0, subscriptionId: subscription.idAbonnement },
    );
  }

  startUpgrade(): void {
    const subscription = this.selected();
    if (!subscription || !this.planId) return;
    this.preparePayment(
      this.api.prepareUpgradeCheckout(subscription.idAbonnement, {
        newPlanId: this.planId,
        idempotencyKey: this.checkoutKey,
      }),
      { kind: 'upgrade', paymentId: 0, subscriptionId: subscription.idAbonnement },
    );
  }

  paymentConfirmed(): void {
    this.completePendingCheckout();
  }

  retryPaymentVerification(): void {
    this.completePendingCheckout();
  }

  label(value: string): string {
    return value.replaceAll('_', ' ').toLowerCase().replace(/^./, (character) => character.toUpperCase());
  }

  tone(status: SubscriptionStatus): string {
    if (status === 'ACTIF') return 'success';
    if (status === 'SUSPENDU' || status === 'EXPIRE' || status === 'EN_ATTENTE') return 'warning';
    return 'critical';
  }

  requiresPayment(): boolean {
    if (this.panel === 'upgrade') return true;
    const plan = this.plans().find((candidate) => candidate.idPlan === this.planId);
    return plan === undefined || plan.prix > 0;
  }

  private loadRemainingDays(id: number): void {
    this.api.getRemainingDays(id).subscribe({
      next: (days) => this.remainingDays.set(days),
      error: () => this.remainingDays.set(null),
    });
  }

  private preparePayment(
    operation: Observable<CheckoutPreparationResponse>,
    pending: PendingCheckout,
  ): void {
    this.busy = true;
    operation.pipe(finalize(() => this.busy = false)).subscribe({
      next: (preparation) => {
        this.checkoutPreparation.set(preparation);
        this.pendingCheckout = { ...pending, paymentId: preparation.paymentId };
        this.storeCheckout(this.pendingCheckout);
      },
      error: (error) => this.feedback.error(error, 'Secure Stripe checkout could not be prepared.'),
    });
  }

  private completePendingCheckout(): void {
    const pending = this.pendingCheckout ?? this.readStoredCheckout();
    if (!pending || this.busy) return;
    this.pendingCheckout = pending;
    const request = { paymentId: pending.paymentId };
    const operation = pending.kind === 'subscribe'
      ? this.api.completeCheckout(request)
      : pending.kind === 'renew' && pending.subscriptionId
        ? this.api.completeRenewalCheckout(pending.subscriptionId, request)
        : pending.kind === 'upgrade' && pending.subscriptionId
          ? this.api.completeUpgradeCheckout(pending.subscriptionId, request)
          : null;
    if (!operation) {
      this.feedback.error(null, 'The saved checkout could not be matched to a subscription operation.');
      return;
    }

    this.busy = true;
    operation.pipe(finalize(() => this.busy = false)).subscribe({
      next: () => {
        const message = pending.kind === 'subscribe'
          ? 'Payment confirmed and workspace subscription activated.'
          : pending.kind === 'renew'
            ? 'Payment confirmed and workspace subscription renewed.'
            : 'Payment confirmed and workspace plan upgraded.';
        this.feedback.success(message);
        this.removeStoredCheckout();
        this.pendingCheckout = null;
        this.pendingCompletion.set(false);
        this.checkoutPreparation.set(null);
        this.panel = null;
        this.selected.set(null);
        this.clearStripeReturnQuery();
        this.loadHistory();
        if (this.statusFilter) this.refreshCurrent();
      },
      error: (error) => {
        this.pendingCompletion.set(true);
        this.feedback.error(error, 'Stripe has not confirmed this payment yet. No subscription change was applied.');
        this.clearStripeReturnQuery();
      },
    });
  }

  private recoverStripeCheckout(): void {
    const pending = this.readStoredCheckout();
    if (!pending) {
      this.feedback.error(null, 'The Stripe return did not match a saved checkout. No subscription was changed.');
      this.clearStripeReturnQuery();
      return;
    }
    this.pendingCheckout = pending;
    this.pendingCompletion.set(true);
    this.completePendingCheckout();
  }

  private storeCheckout(pending: PendingCheckout): void {
    globalThis.sessionStorage?.setItem(PENDING_CHECKOUT_KEY, JSON.stringify(pending));
  }

  private readStoredCheckout(): PendingCheckout | null {
    try {
      const value = globalThis.sessionStorage?.getItem(PENDING_CHECKOUT_KEY);
      return value ? JSON.parse(value) as PendingCheckout : null;
    } catch {
      return null;
    }
  }

  private removeStoredCheckout(): void {
    globalThis.sessionStorage?.removeItem(PENDING_CHECKOUT_KEY);
  }

  private clearStripeReturnQuery(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { stripe_return: null, payment_intent: null, payment_intent_client_secret: null, redirect_status: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private run(operation: Observable<unknown>, message: string): void {
    this.busy = true;
    operation.pipe(finalize(() => this.busy = false)).subscribe({
      next: () => {
        this.feedback.success(message);
        this.close();
        this.loadHistory();
        if (this.statusFilter) this.refreshCurrent();
      },
      error: (error) => this.feedback.error(error),
    });
  }

  private refreshCurrent(): void {
    this.api.searchSubscriptions(0, 20).subscribe({
      next: (history) => {
        const current = history.content.find((item) => item.statut === 'ACTIF' || item.statut === 'SUSPENDU') ?? null;
        this.currentSubscription.set(current);
        if (current) this.loadRemainingDays(current.idAbonnement);
      },
      error: (error) => this.feedback.error(error, 'Current workspace entitlement could not be refreshed.'),
    });
  }
}
