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
  CourierDashboardResponse,
  DELIVERY_FAILURE_REASONS,
  DeliveryFailureReason,
} from '../../core/api';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';

@Component({
  selector: 'app-deliveries',
  imports: [FormsModule, DatePipe],
  templateUrl: './deliveries.component.html',
  styleUrls: ['./business-view.scss', './deliveries.component.scss'],
})
export class DeliveriesComponent implements OnInit {
  private readonly api = inject(DeliveriesApiService);
  private readonly usersApi = inject(UsersApiService);
  private readonly crmApi = inject(CrmApiService);
  readonly role = inject(AuthSessionService).currentUser()?.role;
  readonly feedback = inject(UiFeedbackService);
  readonly statuses = DELIVERY_STATUSES;
  readonly carriers = CARRIER_TYPES;
  readonly canShip = this.role === 'ROLE_ADMIN' || this.role === 'ROLE_LOGISTIC';
  readonly isCourier = this.role === 'ROLE_LIVREUR';
  readonly failureReasons = DELIVERY_FAILURE_REASONS;
  readonly dashboard = signal<CourierDashboardResponse | null>(null);
  readonly proofPreviewUrl = signal<string | null>(null);
  readonly page = signal<PageResponse<DeliveryResponse> | null>(null);
  readonly selected = signal<DeliveryResponse | null>(null);
  readonly couriers = signal<readonly UserResponse[]>([]);
  readonly shippableOrders = signal<readonly OrderResponse[]>([]);
  readonly loading = signal(true);
  statusFilter: DeliveryStatus | '' = '';
  carrierFilter: CarrierType | '' = '';
  panel: 'ship' | 'detail' | 'status' | 'assign' | 'failure' | 'complete' | null = null;
  busy = false;
  lookupType: 'tracking' | 'order' = 'tracking';
  lookupValue = '';
  newStatus: DeliveryStatus = 'EN_PREPARATION';
  assignedCourierId: number | null = null;
  proofPhoto?: File;
  locationMessage = '';
  failureForm = {
    reason: 'CLIENT_ABSENT' as DeliveryFailureReason,
    note: '',
    latitude: undefined as number | undefined,
    longitude: undefined as number | undefined,
  };
  completionForm = {
    recipientName: '',
    signature: '',
    collectedCodAmount: 0,
    codDiscrepancyNote: '',
    latitude: undefined as number | undefined,
    longitude: undefined as number | undefined,
  };
  shipForm = {
    referenceCommandeId: null as number | null,
    typeTransporteur: 'SOCIETE_LIVRAISON' as CarrierType,
    nomSociete: '',
    livreurId: null as number | null,
  };

  ngOnInit(): void {
    this.load();
    if (this.isCourier) this.loadDashboard();
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

  open(panel: 'ship' | 'detail' | 'status' | 'assign' | 'failure' | 'complete', delivery?: DeliveryResponse): void {
    if (panel === 'ship' && !this.canShip) return;
    this.selected.set(delivery ?? null);
    this.panel = panel;
    if (delivery && panel === 'status') {
      this.newStatus = this.allowedStatuses(delivery)[0] ?? delivery.statutLivraison;
    }
    if (delivery && panel === 'assign') this.assignedCourierId = delivery.livreurId;
    if (delivery && panel === 'failure') {
      this.failureForm = { reason: 'CLIENT_ABSENT', note: '', latitude: undefined, longitude: undefined };
      this.locationMessage = '';
    }
    if (delivery && panel === 'complete') {
      this.completionForm = {
        recipientName: delivery.clientNomComplet,
        signature: '',
        collectedCodAmount: delivery.montantACollecterCoD,
        codDiscrepancyNote: '',
        latitude: undefined,
        longitude: undefined,
      };
      this.proofPhoto = undefined;
      this.locationMessage = '';
    }
    if (delivery && panel === 'detail') {
      this.api.getById(delivery.idLivraison).subscribe({
        next: (value) => this.selected.set(value),
        error: (error) => this.feedback.error(error),
      });
    }
  }

  close(): void {
    const proofUrl = this.proofPreviewUrl();
    if (proofUrl) URL.revokeObjectURL(proofUrl);
    this.proofPreviewUrl.set(null);
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

  accept(delivery: DeliveryResponse): void {
    this.runAction(this.api.accept(delivery.idLivraison), 'Assignment accepted.');
  }

  start(delivery: DeliveryResponse): void {
    this.runAction(this.api.start(delivery.idLivraison), 'Delivery is now in progress.');
  }

  requestReturn(delivery: DeliveryResponse): void {
    this.runAction(this.api.requestReturn(delivery.idLivraison), 'Return requested for logistics approval.');
  }

  reportFailure(): void {
    const delivery = this.selected();
    if (!delivery) return;
    this.busy = true;
    this.api.reportFailedAttempt(delivery.idLivraison, this.failureForm).subscribe({
      next: () => this.actionSucceeded('Failed attempt recorded.'),
      error: (error) => this.actionFailed(error),
    });
  }

  complete(): void {
    const delivery = this.selected();
    if (!delivery || !this.completionForm.recipientName.trim() || !this.completionForm.signature.trim()) return;
    this.busy = true;
    this.api.complete(delivery.idLivraison, this.completionForm, this.proofPhoto).subscribe({
      next: () => this.actionSucceeded('Delivery completed with proof and COD confirmation.'),
      error: (error) => this.actionFailed(error),
    });
  }

  reconcileCod(delivery: DeliveryResponse): void {
    this.runAction(this.api.reconcileCod(delivery.idLivraison), 'COD collection reconciled.');
  }

  viewProof(delivery: DeliveryResponse): void {
    this.api.getProofPhoto(delivery.idLivraison).subscribe({
      next: (photo) => {
        const previous = this.proofPreviewUrl();
        if (previous) URL.revokeObjectURL(previous);
        const url = URL.createObjectURL(photo);
        this.proofPreviewUrl.set(url);
      },
      error: (error) => this.feedback.error(error, 'Proof photo could not be opened.'),
    });
  }

  selectProofPhoto(event: Event): void {
    this.proofPhoto = (event.target as HTMLInputElement).files?.[0];
  }

  captureLocation(target: 'failure' | 'complete'): void {
    if (!navigator.geolocation) {
      this.locationMessage = 'Location is not supported by this device.';
      return;
    }
    this.locationMessage = 'Capturing location…';
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        const form = target === 'failure' ? this.failureForm : this.completionForm;
        form.latitude = coords.latitude;
        form.longitude = coords.longitude;
        this.locationMessage = 'Current location attached.';
      },
      () => { this.locationMessage = 'Location permission was denied or unavailable.'; },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  }

  mapsUrl(delivery: DeliveryResponse): string {
    return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
      `${delivery.adresseLivraison}, ${delivery.villeLivraison}`,
    )}`;
  }

  selectedOrder(): OrderResponse | undefined {
    return this.shippableOrders().find((order) => order.idCommande === this.shipForm.referenceCommandeId);
  }

  assign(): void {
    const delivery = this.selected();
    if (!delivery || !this.assignedCourierId || !this.canReassign(delivery)) return;
    this.busy = true;
    this.api.assignCourier(delivery.idLivraison, { livreurId: this.assignedCourierId }).subscribe({
      next: () => { this.busy = false; this.feedback.success('Courier assignment updated.'); this.close(); this.load(); },
      error: (error) => { this.busy = false; this.feedback.error(error); },
    });
  }

  allowedStatuses(delivery: DeliveryResponse): readonly DeliveryStatus[] {
    if (this.isCourier) return [];
    const ownsExecution = this.role === 'ROLE_LIVREUR'
      ? delivery.typeTransporteur === 'LIVREUR_INTERNE'
      : delivery.typeTransporteur === 'SOCIETE_LIVRAISON';
    if (!ownsExecution) return [];
    switch (delivery.statutLivraison) {
      case 'ASSIGNEE':
      case 'ACCEPTEE': return [];
      case 'EN_PREPARATION':
        return delivery.typeTransporteur === 'LIVREUR_INTERNE'
          ? ['EN_COURS', 'ECHEC']
          : ['CHEZ_TRANSPORTEUR', 'ECHEC'];
      case 'CHEZ_TRANSPORTEUR': return ['EN_COURS', 'ECHEC', 'RETOUR'];
      case 'EN_COURS': return ['LIVREE', 'ECHEC', 'RETOUR'];
      case 'ECHEC': return delivery.typeTransporteur === 'SOCIETE_LIVRAISON' ? ['EN_COURS', 'RETOUR'] : [];
      case 'RETOUR_DEMANDE': return this.canShip ? ['RETOUR'] : [];
      default: return [];
    }
  }

  canConfirm(delivery: DeliveryResponse): boolean {
    return this.allowedStatuses(delivery).includes('LIVREE');
  }

  canReassign(delivery: DeliveryResponse): boolean {
    return this.canShip && delivery.typeTransporteur === 'LIVREUR_INTERNE'
      && (delivery.statutLivraison === 'ASSIGNEE'
        || delivery.statutLivraison === 'EN_PREPARATION'
        || delivery.statutLivraison === 'ECHEC');
  }

  courierName(id: number | null): string {
    if (id === null) return 'Unassigned';
    if (this.role === 'ROLE_LIVREUR') return 'You';
    const courier = this.couriers().find((item) => item.id === id);
    return courier ? `${courier.firstname} ${courier.lastname}` : `Courier #${id}`;
  }

  label(value: string): string {
    return value.replaceAll('_', ' ').toLowerCase().replace(/^./, (character) => character.toUpperCase());
  }

  tone(value: DeliveryStatus): string {
    return value === 'LIVREE' ? 'success'
      : value === 'ECHEC' || value === 'RETOUR' || value === 'RETOUR_DEMANDE' ? 'critical'
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

  private loadDashboard(): void {
    this.api.courierDashboard().subscribe({
      next: (value) => this.dashboard.set(value),
      error: (error) => this.feedback.error(error, 'Courier summary could not be loaded.'),
    });
  }

  private runAction(call: ReturnType<DeliveriesApiService['accept']>, message: string): void {
    this.busy = true;
    call.subscribe({
      next: () => this.actionSucceeded(message),
      error: (error) => this.actionFailed(error),
    });
  }

  private actionSucceeded(message: string): void {
    this.busy = false;
    this.feedback.success(message);
    this.close();
    this.load();
    if (this.isCourier) this.loadDashboard();
  }

  private actionFailed(error: unknown): void {
    this.busy = false;
    this.feedback.error(error);
  }
}
