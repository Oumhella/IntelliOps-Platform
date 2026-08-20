import { IsoDateTime } from './common.models';

export const CARRIER_TYPES = ['SOCIETE_LIVRAISON', 'LIVREUR_INTERNE'] as const;
export type CarrierType = (typeof CARRIER_TYPES)[number];

export const DELIVERY_STATUSES = [
  'ASSIGNEE', 'ACCEPTEE', 'EN_PREPARATION', 'CHEZ_TRANSPORTEUR', 'EN_COURS',
  'LIVREE', 'ECHEC', 'RETOUR_DEMANDE', 'RETOUR',
] as const;
export type DeliveryStatus = (typeof DELIVERY_STATUSES)[number];

export interface ShipDeliveryRequest {
  readonly referenceCommandeId: number;
  readonly typeTransporteur: CarrierType;
  readonly nomSociete?: string;
  readonly livreurId?: number;
}

export interface UpdateDeliveryStatusRequest {
  readonly statut: DeliveryStatus;
}

export interface AssignCourierRequest { readonly livreurId: number; }

export const DELIVERY_FAILURE_REASONS = [
  'CLIENT_ABSENT', 'ADRESSE_INCORRECTE', 'CLIENT_REFUSE',
  'PROBLEME_PAIEMENT', 'COLIS_ENDOMMAGE', 'AUTRE',
] as const;
export type DeliveryFailureReason = (typeof DELIVERY_FAILURE_REASONS)[number];

export interface FailedDeliveryAttemptRequest {
  readonly reason: DeliveryFailureReason;
  readonly note?: string;
  readonly latitude?: number;
  readonly longitude?: number;
}

export interface CompleteDeliveryRequest {
  readonly recipientName: string;
  readonly signature: string;
  readonly collectedCodAmount: number;
  readonly codDiscrepancyNote?: string;
  readonly latitude?: number;
  readonly longitude?: number;
}

export interface CourierDashboardResponse {
  readonly assignedToday: number;
  readonly activeDeliveries: number;
  readonly completedToday: number;
  readonly failedAttempts: number;
  readonly codAwaitingReconciliation: number;
}

export interface DeliveryResponse {
  readonly idLivraison: number;
  readonly referenceCommandeId: number;
  readonly codeSuiviTracking: string;
  readonly statutLivraison: DeliveryStatus;
  readonly typeTransporteur: CarrierType;
  readonly nomSociete: string | null;
  readonly livreurId: number | null;
  readonly shippingDate: IsoDateTime;
  readonly deliveryDate: IsoDateTime | null;
  readonly montantACollecterCoD: number;
  readonly delaiJours: number;
  readonly clientNomComplet: string;
  readonly clientEmail: string | null;
  readonly clientTelephone: string;
  readonly adresseLivraison: string;
  readonly villeLivraison: string;
  readonly acceptedAt: IsoDateTime | null;
  readonly startedAt: IsoDateTime | null;
  readonly lastAttemptAt: IsoDateTime | null;
  readonly returnRequestedAt: IsoDateTime | null;
  readonly attemptCount: number;
  readonly failureReason: DeliveryFailureReason | null;
  readonly failureNote: string | null;
  readonly lastLatitude: number | null;
  readonly lastLongitude: number | null;
  readonly deliveredTo: string | null;
  readonly proofSignature: string | null;
  readonly proofPhotoAvailable: boolean;
  readonly proofCapturedAt: IsoDateTime | null;
  readonly codCollectedAmount: number | null;
  readonly codDiscrepancyNote: string | null;
  readonly codReconciledAt: IsoDateTime | null;
}
