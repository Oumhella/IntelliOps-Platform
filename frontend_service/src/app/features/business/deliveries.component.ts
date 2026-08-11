import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CARRIER_TYPES,
  CarrierType,
  DELIVERY_STATUSES,
  DeliveriesApiService,
  DeliveryResponse,
  DeliveryStatus,
  PageResponse,
  UserResponse,
  UsersApiService,
  CrmApiService,
  OrderResponse,
} from '../../core/api';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';

@Component({
  selector: 'app-deliveries',
  imports: [FormsModule, DatePipe],
  templateUrl: './deliveries.component.html',
  styleUrl: './business-view.scss',
})
export class DeliveriesComponent implements OnInit {
  private readonly api = inject(DeliveriesApiService);
  private readonly usersApi = inject(UsersApiService);
  private readonly crmApi = inject(CrmApiService);
  private readonly role = inject(AuthSessionService).currentUser()?.role;
  readonly feedback = inject(UiFeedbackService);
  readonly statuses = DELIVERY_STATUSES;
  readonly carriers = CARRIER_TYPES;
  readonly canShip = this.role === 'ROLE_ADMIN' || this.role === 'ROLE_LOGISTIC';
  readonly page = signal<PageResponse<DeliveryResponse> | null>(null);
  readonly selected = signal<DeliveryResponse | null>(null);
  readonly couriers = signal<readonly UserResponse[]>([]);
  readonly shippableOrders = signal<readonly OrderResponse[]>([]);
  readonly loading = signal(true);
  statusFilter: DeliveryStatus | '' = '';
  carrierFilter: CarrierType | '' = '';
  panel: 'ship' | 'detail' | 'status' | null = null;
  busy = false;
  lookupType: 'tracking' | 'order' = 'tracking';
  lookupValue = '';
  newStatus: DeliveryStatus = 'EN_PREPARATION';
  shipForm = {
    referenceCommandeId: null as number | null,
    typeTransporteur: 'SOCIETE_LIVRAISON' as CarrierType,
    nomSociete: '',
    livreurId: null as number | null,
  };

  ngOnInit(): void {
    this.load();
    if (this.canShip) {
      this.usersApi.getActiveCouriers().subscribe({
        next: (couriers) => this.couriers.set(couriers),
        error: (error) => this.feedback.error(error, 'Available couriers could not be loaded.'),
      });
      this.loadShippableOrders();
    }
  }

  load(page = 0): void {
    this.loading.set(true);
    this.api.search(page, 20, this.statusFilter || undefined, this.carrierFilter || undefined).subscribe({
      next: (value) => { this.page.set(value); this.loading.set(false); },
      error: (error) => { this.loading.set(false); this.feedback.error(error); },
    });
  }

  lookup(): void {
    if (!this.lookupValue) return;
    const call = this.lookupType === 'tracking'
      ? this.api.getByTrackingNumber(this.lookupValue)
      : this.api.getByOrderId(Number(this.lookupValue));
    call.subscribe({
      next: (value) => { this.selected.set(value); this.panel = 'detail'; },
      error: (error) => this.feedback.error(error),
    });
  }

  open(panel: 'ship' | 'detail' | 'status', delivery?: DeliveryResponse): void {
    if (panel === 'ship' && !this.canShip) return;
    this.selected.set(delivery ?? null);
    this.panel = panel;
    if (delivery && panel === 'status') {
      this.newStatus = this.allowedStatuses(delivery)[0] ?? delivery.statutLivraison;
    }
    if (delivery && panel === 'detail') {
      this.api.getById(delivery.idLivraison).subscribe({
        next: (value) => this.selected.set(value),
        error: (error) => this.feedback.error(error),
      });
    }
  }

  close(): void {
    this.panel = null;
    this.selected.set(null);
  }

  ship(): void {
    const form = this.shipForm;
    if (!this.canShip || !form.referenceCommandeId) return;
    if (form.typeTransporteur === 'LIVREUR_INTERNE' && form.livreurId === null) {
      this.feedback.error(null, 'Select an active internal courier.');
      return;
    }
    if (form.typeTransporteur === 'SOCIETE_LIVRAISON' && !form.nomSociete.trim()) {
      this.feedback.error(null, 'Enter the external delivery company name.');
      return;
    }
    this.busy = true;
    this.api.ship({
      referenceCommandeId: form.referenceCommandeId,
      typeTransporteur: form.typeTransporteur,
      nomSociete: form.typeTransporteur === 'SOCIETE_LIVRAISON' ? form.nomSociete : undefined,
      livreurId: form.typeTransporteur === 'LIVREUR_INTERNE' ? form.livreurId ?? undefined : undefined,
    }).subscribe({
      next: (value) => {
        this.busy = false;
        this.feedback.success(`Shipment ${value.codeSuiviTracking} created.`);
        this.close();
        this.load();
        this.loadShippableOrders();
      },
      error: (error) => { this.busy = false; this.feedback.error(error); },
    });
  }

  update(): void {
    const delivery = this.selected();
    if (!delivery) return;
    this.busy = true;
    this.api.updateStatus(delivery.idLivraison, { statut: this.newStatus }).subscribe({
      next: () => {
        this.busy = false;
        this.feedback.success('Delivery status updated.');
        this.close();
        this.load();
      },
      error: (error) => { this.busy = false; this.feedback.error(error); },
    });
  }

  confirm(delivery: DeliveryResponse): void {
    this.api.confirmReception(delivery.idLivraison).subscribe({
      next: () => { this.feedback.success('Reception confirmed.'); this.load(); },
      error: (error) => this.feedback.error(error),
    });
  }

  allowedStatuses(delivery: DeliveryResponse): readonly DeliveryStatus[] {
    switch (delivery.statutLivraison) {
      case 'EN_PREPARATION':
        return delivery.typeTransporteur === 'LIVREUR_INTERNE'
          ? ['EN_COURS', 'ECHEC']
          : ['CHEZ_TRANSPORTEUR', 'ECHEC'];
      case 'CHEZ_TRANSPORTEUR': return ['EN_COURS', 'ECHEC', 'RETOUR'];
      case 'EN_COURS': return ['LIVREE', 'ECHEC', 'RETOUR'];
      case 'ECHEC': return ['EN_COURS', 'RETOUR'];
      default: return [];
    }
  }

  label(value: string): string {
    return value.replaceAll('_', ' ').toLowerCase().replace(/^./, (character) => character.toUpperCase());
  }

  tone(value: DeliveryStatus): string {
    return value === 'LIVREE' ? 'success'
      : value === 'ECHEC' || value === 'RETOUR' ? 'critical'
      : value === 'EN_COURS' || value === 'CHEZ_TRANSPORTEUR' ? 'info'
      : 'warning';
  }

  private loadShippableOrders(): void {
    this.crmApi.searchOrders(0, 100, 'PREPARATION').subscribe({
      next: (result) => this.shippableOrders.set(result.content.filter(
        (order) => order.statutPaiement === 'PAID' || order.statutPaiement === 'AWAITING_COLLECTION',
      )),
      error: (error) => this.feedback.error(error, 'Orders ready to ship could not be loaded.'),
    });
  }
}
