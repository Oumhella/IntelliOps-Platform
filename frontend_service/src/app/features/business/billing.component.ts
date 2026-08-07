import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import {
  InvoiceResponse,
  PAYMENT_CONTEXTS,
  PAYMENT_MODES,
  PAYMENT_STATUSES,
  PageResponse,
  PaymentContext,
  PaymentMode,
  PaymentStatus,
  PaymentTransactionResponse,
  PaymentPreparationResponse,
  PaymentsApiService,
  CrmApiService,
  OrderResponse,
} from '../../core/api';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';
import { StripePaymentElementComponent } from './stripe-payment-element.component';

const PENDING_BILLING_PAYMENT_KEY = 'intelliops.pending-billing-payment';

@Component({ selector: 'app-billing', imports: [FormsModule, DatePipe, StripePaymentElementComponent], templateUrl: './billing.component.html', styleUrl: './business-view.scss' })
export class BillingComponent implements OnInit {
  private readonly api = inject(PaymentsApiService);
  private readonly crmApi = inject(CrmApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly feedback = inject(UiFeedbackService);
  readonly statuses = PAYMENT_STATUSES;
  readonly contexts = PAYMENT_CONTEXTS;
  readonly modes = PAYMENT_MODES;
  readonly transactions = signal<PageResponse<PaymentTransactionResponse> | null>(null);
  readonly invoices = signal<PageResponse<InvoiceResponse> | null>(null);
  readonly selectedTransaction = signal<PaymentTransactionResponse | null>(null);
  readonly selectedInvoice = signal<InvoiceResponse | null>(null);
  readonly loading = signal(true);
  readonly paymentPreparation = signal<PaymentPreparationResponse | null>(null);
  readonly payableOrders = signal<readonly OrderResponse[]>([]);
  tab: 'transactions' | 'invoices' = 'transactions';
  panel: 'initiate' | 'transaction' | 'refund' | 'invoice' | null = null;
  statusFilter: PaymentStatus | '' = '';
  contextFilter: PaymentContext | '' = '';
  busy = false;
  lookupId: number | null = null;
  paymentForm = { idempotencyKey: '', orderId: null as number | null, mode: 'CASH_ON_DELIVERY' as PaymentMode };
  refundForm = { montant: 0, motif: '' };
  readonly paymentReturnUrl = `${globalThis.location?.origin ?? ''}/app/billing?stripe_return=1`;

  ngOnInit(): void { this.newKey(); this.loadTransactions(); this.loadPayableOrders(); if (this.route.snapshot.queryParamMap.get('stripe_return') === '1' || globalThis.sessionStorage?.getItem(PENDING_BILLING_PAYMENT_KEY)) this.recoverCardPayment(); }
  newKey(): void { this.paymentForm.idempotencyKey = globalThis.crypto?.randomUUID?.() ?? `payment-${Date.now()}`; }
  setTab(tab: 'transactions' | 'invoices'): void { this.tab = tab; tab === 'transactions' ? this.loadTransactions() : this.loadInvoices(); }
  loadTransactions(page = 0): void { this.loading.set(true); this.api.searchTransactions(page, 20, this.statusFilter || undefined, this.contextFilter || undefined).subscribe({ next: (value) => { this.transactions.set(value); this.loading.set(false); }, error: (error) => { this.loading.set(false); this.feedback.error(error); } }); }
  loadInvoices(page = 0): void { this.loading.set(true); this.api.getInvoices(page, 20).subscribe({ next: (value) => { this.invoices.set(value); this.loading.set(false); }, error: (error) => { this.loading.set(false); this.feedback.error(error); } }); }
  lookup(): void { if (!this.lookupId) return; if (this.tab === 'transactions') this.api.getTransaction(this.lookupId).subscribe({ next: (value) => { this.selectedTransaction.set(value); this.panel = 'transaction'; }, error: (error) => this.feedback.error(error) }); else this.api.getInvoice(this.lookupId).subscribe({ next: (value) => { this.selectedInvoice.set(value); this.panel = 'invoice'; }, error: (error) => this.feedback.error(error) }); }
  openTransaction(transaction: PaymentTransactionResponse, panel: 'transaction' | 'refund'): void { this.selectedTransaction.set(transaction); this.panel = panel; if (panel === 'transaction') this.api.getTransaction(transaction.id).subscribe({ next: (value) => this.selectedTransaction.set(value), error: (error) => this.feedback.error(error) }); }
  openInvoice(invoice: InvoiceResponse): void { this.selectedInvoice.set(invoice); this.panel = 'invoice'; this.api.getInvoice(invoice.id).subscribe({ next: (value) => this.selectedInvoice.set(value), error: (error) => this.feedback.error(error) }); }
  close(): void { this.panel = null; this.selectedTransaction.set(null); this.selectedInvoice.set(null); this.paymentPreparation.set(null); globalThis.sessionStorage?.removeItem(PENDING_BILLING_PAYMENT_KEY); }
  initiate(): void {
    const form = this.paymentForm;
    if (!form.orderId) return;
    this.busy = true;
    if (form.mode === 'CREDIT_CARD') {
      this.api.prepareOrderCard(form.orderId, { idempotencyKey: form.idempotencyKey })
        .pipe(finalize(() => this.busy = false)).subscribe({
          next: (prepared) => {
            this.paymentPreparation.set(prepared);
            globalThis.sessionStorage?.setItem(PENDING_BILLING_PAYMENT_KEY, String(prepared.paymentId));
          },
          error: (error) => this.feedback.error(error, 'Secure Stripe checkout could not be prepared.'),
        });
      return;
    }
    this.api.initiateOrderCod(form.orderId, { idempotencyKey: form.idempotencyKey })
      .pipe(finalize(() => this.busy = false)).subscribe({
        next: (value) => { this.feedback.success(`Cash-on-delivery payment #${value.id} attached to the order.`); this.close(); this.newKey(); this.loadTransactions(); this.loadPayableOrders(); },
        error: (error) => this.feedback.error(error),
      });
  }
  cardPaymentConfirmed(): void { const id = this.paymentPreparation()?.paymentId; if (id) this.finalizeCardPayment(id); }
  refund(): void { const transaction = this.selectedTransaction(); if (!transaction) return; this.busy = true; this.api.refund(transaction.id, this.refundForm).subscribe({ next: () => { this.busy = false; this.feedback.success('Refund recorded.'); this.close(); this.loadTransactions(); }, error: (error) => { this.busy = false; this.feedback.error(error); } }); }
  cancel(transaction: PaymentTransactionResponse): void { if (!confirm(`Cancel payment #${transaction.id}?`)) return; this.api.cancel(transaction.id).subscribe({ next: () => { this.feedback.success('Payment cancelled.'); this.loadTransactions(); }, error: (error) => this.feedback.error(error) }); }
  download(invoice: InvoiceResponse): void { this.api.getInvoiceDownloadUrl(invoice.id).subscribe({ next: (url) => { window.open(url, '_blank', 'noopener'); this.feedback.success('Invoice download opened.'); }, error: (error) => this.feedback.error(error) }); }
  label(value: string): string { return value.replaceAll('_', ' ').toLowerCase().replace(/^./, (character) => character.toUpperCase()); }
  tone(value: PaymentStatus): string { return value === 'COMPLETED' ? 'success' : value === 'FAILED' || value === 'CANCELLED' ? 'critical' : value === 'REFUNDED' || value === 'PARTIALLY_REFUNDED' ? 'info' : 'warning'; }

  private recoverCardPayment(): void {
    const id = Number(globalThis.sessionStorage?.getItem(PENDING_BILLING_PAYMENT_KEY));
    if (Number.isInteger(id) && id > 0) this.finalizeCardPayment(id);
    else this.clearStripeReturnQuery();
  }

  private finalizeCardPayment(id: number): void {
    if (this.busy) return;
    this.busy = true;
    this.api.finalize(id).pipe(finalize(() => this.busy = false)).subscribe({
      next: (payment) => {
        if (payment.statut === 'COMPLETED') {
          this.feedback.success(`Stripe payment #${payment.id} confirmed.`);
          globalThis.sessionStorage?.removeItem(PENDING_BILLING_PAYMENT_KEY);
          this.paymentPreparation.set(null);
          this.panel = null;
          this.newKey();
          this.loadTransactions();
          this.loadPayableOrders();
        } else {
          this.feedback.error(null, 'Stripe has not completed this payment. The transaction remains pending.');
        }
        this.clearStripeReturnQuery();
      },
      error: (error) => { this.feedback.error(error, 'Stripe payment verification failed.'); this.clearStripeReturnQuery(); },
    });
  }

  private clearStripeReturnQuery(): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { stripe_return: null, payment_intent: null, payment_intent_client_secret: null, redirect_status: null }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  private loadPayableOrders(): void {
    this.crmApi.searchOrders(0, 100, 'CONFIRMEE').subscribe({
      next: (result) => this.payableOrders.set(
        result.content.filter((order) => order.statutPaiement === 'UNPAID'),
      ),
      error: (error) => this.feedback.error(error, 'Confirmed orders could not be loaded.'),
    });
  }
}
