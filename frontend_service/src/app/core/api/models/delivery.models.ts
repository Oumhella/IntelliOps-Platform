import { IsoDateTime } from './common.models';

export const CARRIER_TYPES = ['SOCIETE_LIVRAISON', 'LIVREUR_INTERNE'] as const;
export type CarrierType = (typeof CARRIER_TYPES)[number];

export const DELIVERY_STATUSES = [
  'EN_PREPARATION', 'CHEZ_TRANSPORTEUR', 'EN_COURS', 'LIVREE', 'ECHEC', 'RETOUR',
] as const;
export type DeliveryStatus = (typeof DELIVERY_STATUSES)[number];

export interface ShipDeliveryRequest {
  readonly referenceCommandeId: number;
  readonly typeTransporteur: CarrierType;
  readonly montantACollecterCoD: number;
  readonly clientEmail?: string;
  readonly nomSociete?: string;
  readonly endpointApiUrl?: string;
  readonly externalLivreurId?: number;
}

export interface UpdateDeliveryStatusRequest {
  readonly statut: DeliveryStatus;
}

export interface DeliveryResponse {
  readonly idLivraison: number;
  readonly referenceCommandeId: number;
  readonly codeSuiviTracking: string;
  readonly statutLivraison: DeliveryStatus;
  readonly typeTransporteur: CarrierType;
  readonly nomSociete: string | null;
  readonly externalLivreurId: number | null;
  readonly shippingDate: IsoDateTime;
  readonly deliveryDate: IsoDateTime | null;
  readonly montantACollecterCoD: number;
  readonly delaiJours: number;
}
