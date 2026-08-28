import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, Observable, of } from 'rxjs';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { CrmApiService, DeliveriesApiService, NotificationsApiService, PaymentsApiService, StockApiService, SubscriptionsApiService, UsersApiService } from '../../core/api';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

interface OverviewMetric { label: string; value: number | string; note: string; tone: string; route: string; }
interface WorkflowStep { label: string; detail: string; route: string; }

@Component({ selector: 'app-business-home', imports: [RouterLink, TranslatePipe], templateUrl: './business-home.component.html', styleUrl: './business-home.component.scss' })
export class BusinessHomeComponent implements OnInit {
  readonly i18n = inject(I18nService);
  private readonly session = inject(AuthSessionService);
  private readonly usersApi = inject(UsersApiService);
  private readonly crmApi = inject(CrmApiService);
  private readonly stockApi = inject(StockApiService);
  private readonly deliveriesApi = inject(DeliveriesApiService);
  private readonly paymentsApi = inject(PaymentsApiService);
  private readonly subscriptionsApi = inject(SubscriptionsApiService);
  private readonly notificationsApi = inject(NotificationsApiService);
  private readonly feedback = inject(UiFeedbackService);
  readonly user = this.session.currentUser;
  readonly canUseBi = ['ROLE_ADMIN', 'ROLE_CSM', 'ROLE_LOGISTIC'].includes(this.user()?.role ?? '');
  readonly workflow = this.workflowForRole(this.user()?.role);
  readonly metrics = signal<OverviewMetric[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.roleRequest().subscribe({
      next: (metrics) => { this.metrics.set(metrics); this.loading.set(false); },
      error: (error) => { this.loading.set(false); this.feedback.error(error, 'Workspace totals could not be loaded.'); },
    });
  }

  private roleRequest(): Observable<OverviewMetric[]> {
    const role = this.user()?.role;
    if (role === 'ROLE_ADMIN') {
      return new Observable((subscriber) => forkJoin({ staff: this.usersApi.getEnterpriseStaff(), subscriptions: this.subscriptionsApi.searchSubscriptions(), payments: this.paymentsApi.searchTransactions(), notifications: this.notificationsApi.search() }).subscribe({ next: (v) => { subscriber.next([
        { label: 'Team members', value: v.staff.length, note: 'Enterprise accounts', tone: 'indigo', route: '/app/team' },
        { label: 'Plan entitlement', value: v.subscriptions.content.some((item) => item.statut === 'ACTIF' || item.statut === 'SUSPENDU') ? 'Active' : 'None', note: 'Workspace access', tone: 'sky', route: '/app/subscriptions' },
        { label: 'Transactions', value: v.payments.totalElements, note: 'Recorded payments', tone: 'emerald', route: '/app/billing' },
        { label: 'Notifications', value: v.notifications.totalElements, note: 'Delivery history', tone: 'amber', route: '/app/notifications' },
      ]); subscriber.complete(); }, error: (e) => subscriber.error(e) }));
    }
    if (role === 'ROLE_CSM') {
      return new Observable((subscriber) => forkJoin({ leads: this.crmApi.searchLeads(), pendingOrders: this.crmApi.searchOrders(0, 100, 'EN_ATTENTE'), handedOrders: this.crmApi.searchOrders(0, 100, 'CONFIRMEE') }).subscribe({ next: (v) => { subscriber.next([
        { label: 'Leads', value: v.leads.totalElements, note: 'CRM pipeline', tone: 'indigo', route: '/app/leads' },
        { label: 'Needs confirmation', value: v.pendingOrders.totalElements, note: 'My pending customer orders', tone: 'amber', route: '/app/orders' },
        { label: 'Handed to logistics', value: v.handedOrders.totalElements, note: 'My confirmed orders', tone: 'emerald', route: '/app/orders' },
      ]); subscriber.complete(); }, error: (e) => subscriber.error(e) }));
    }
    if (role === 'ROLE_LOGISTIC') {
      return new Observable((subscriber) => forkJoin({ readyOrders: this.crmApi.searchOrders(0, 100, 'CONFIRMEE'), stores: this.stockApi.getStores(), products: this.stockApi.getProducts(), deliveries: this.deliveriesApi.search() }).subscribe({ next: (v) => { subscriber.next([
        { label: 'Ready for logistics', value: v.readyOrders.totalElements, note: 'Confirmed order handoffs', tone: 'amber', route: '/app/orders' },
        { label: 'Stores', value: v.stores.length, note: 'Connected commerce stores', tone: 'indigo', route: '/app/stock' },
        { label: 'Products', value: v.products.length, note: 'Product catalogue', tone: 'sky', route: '/app/stock' },
        { label: 'Deliveries', value: v.deliveries.totalElements, note: 'Shipment records', tone: 'emerald', route: '/app/deliveries' },
      ]); subscriber.complete(); }, error: (e) => subscriber.error(e) }));
    }
    if (role === 'ROLE_LIVREUR') {
      return new Observable((subscriber) => this.deliveriesApi.search().subscribe({ next: (deliveries) => { subscriber.next([
        { label: 'My deliveries', value: deliveries.totalElements, note: 'Shipments assigned to you', tone: 'emerald', route: '/app/deliveries' },
      ]); subscriber.complete(); }, error: (e) => subscriber.error(e) }));
    }
    return of([]);
  }

  private workflowForRole(role?: string): readonly WorkflowStep[] {
    if (role === 'ROLE_CSM') return [
      { label: 'Qualify assigned lead', detail: 'Record interactions until the lead is ready.', route: '/app/leads' },
      { label: 'Convert and reserve', detail: 'Create the pending order with real catalogue prices and stock.', route: '/app/leads' },
      { label: 'Confirm customer intent', detail: 'CONFIRMEE hands the order to the logistics queue.', route: '/app/orders' },
    ];
    if (role === 'ROLE_LOGISTIC') return [
      { label: 'Accept confirmed order', detail: 'The logistics queue starts at CONFIRMEE.', route: '/app/orders' },
      { label: 'Prepare fulfillment', detail: 'Move the order to PREPARATION after payment/COD checks.', route: '/app/orders' },
      { label: 'Create and assign shipment', detail: 'Choose an internal courier or external carrier.', route: '/app/deliveries' },
    ];
    if (role === 'ROLE_LIVREUR') return [
      { label: 'Open assigned delivery', detail: 'Only shipments assigned to your account are visible.', route: '/app/deliveries' },
      { label: 'Execute delivery', detail: 'Update the shipment through its allowed operational states.', route: '/app/deliveries' },
      { label: 'Close the attempt', detail: 'Mark it delivered, failed, or returned with an auditable status.', route: '/app/deliveries' },
    ];
    return [
      { label: 'Customer operations', detail: 'CSM owns qualification and order confirmation.', route: '/app/leads' },
      { label: 'Fulfillment operations', detail: 'Logistics owns preparation and carrier assignment.', route: '/app/orders' },
      { label: 'Delivery execution', detail: 'The assigned courier or carrier owns final execution.', route: '/app/deliveries' },
    ];
  }
}
