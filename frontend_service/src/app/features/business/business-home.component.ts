import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, Observable, of } from 'rxjs';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { CrmApiService, DeliveriesApiService, NotificationsApiService, PaymentsApiService, StockApiService, SubscriptionsApiService, UsersApiService } from '../../core/api';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';

interface OverviewMetric { label: string; value: number; note: string; tone: string; route: string; }

@Component({ selector: 'app-business-home', imports: [RouterLink], templateUrl: './business-home.component.html', styleUrl: './business-home.component.scss' })
export class BusinessHomeComponent implements OnInit {
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
        { label: 'Current workspace plan', value: v.subscriptions.content.some((item) => item.statut === 'ACTIF' || item.statut === 'SUSPENDU') ? 1 : 0, note: 'Enterprise entitlement', tone: 'sky', route: '/app/subscriptions' },
        { label: 'Transactions', value: v.payments.totalElements, note: 'Recorded payments', tone: 'emerald', route: '/app/billing' },
        { label: 'Notifications', value: v.notifications.totalElements, note: 'Delivery history', tone: 'amber', route: '/app/notifications' },
      ]); subscriber.complete(); }, error: (e) => subscriber.error(e) }));
    }
    if (role === 'ROLE_CSM') {
      return new Observable((subscriber) => forkJoin({ leads: this.crmApi.searchLeads(), orders: this.crmApi.searchOrders() }).subscribe({ next: (v) => { subscriber.next([
        { label: 'Leads', value: v.leads.totalElements, note: 'CRM pipeline', tone: 'indigo', route: '/app/leads' },
        { label: 'Orders', value: v.orders.totalElements, note: 'Converted customer orders', tone: 'sky', route: '/app/orders' },
      ]); subscriber.complete(); }, error: (e) => subscriber.error(e) }));
    }
    if (role === 'ROLE_LOGISTIC') {
      return new Observable((subscriber) => forkJoin({ stores: this.stockApi.getStores(), products: this.stockApi.getProducts(), deliveries: this.deliveriesApi.search() }).subscribe({ next: (v) => { subscriber.next([
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
}
