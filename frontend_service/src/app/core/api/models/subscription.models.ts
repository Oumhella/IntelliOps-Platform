import { IsoDate } from './common.models';

export const OFFER_DURATIONS = ['HEBDOMADAIRE', 'MENSUEL', 'TRIMESTRIEL', 'ANNUEL'] as const;
export type OfferDuration = (typeof OFFER_DURATIONS)[number];

export const OFFER_STATUSES = ['ACTIF', 'DESACTIVE', 'SUPPRIME'] as const;
export type OfferStatus = (typeof OFFER_STATUSES)[number];

export const SUBSCRIPTION_STATUSES = [
  'EN_ATTENTE',
  'ACTIF',
  'EXPIRE',
  'SUSPENDU',
  'ANNULATION_EN_COURS',
  'ANNULE',
  'ECHEC_REMBOURSEMENT',
] as const;
export type SubscriptionStatus = (typeof SUBSCRIPTION_STATUSES)[number];

export interface PlanRequest {
  readonly nomPlan: string;
  readonly description?: string;
  readonly prix: number;
  readonly duree: OfferDuration;
  readonly minJoursEntreDesactivation: number;
  readonly maxPeriodeDesactivation: number;
  readonly estActif: OfferStatus;
  readonly limiteCommandesMois: number;
}

export interface PlanResponse {
  readonly idPlan: number;
  readonly nomPlan: string;
  readonly description: string | null;
  readonly prix: number;
  readonly duree: OfferDuration;
  readonly minJoursEntreDesactivation: number;
  readonly maxPeriodeDesactivation: number;
  readonly estActif: OfferStatus;
  readonly limiteCommandesMois: number;
}

export interface SubscriptionRequest {
  readonly planId: number;
  readonly paiementId?: number;
}

export interface SubscriptionCheckoutRequest {
  readonly planId: number;
  readonly idempotencyKey: string;
}

export interface PaymentCheckoutRequest {
  readonly idempotencyKey: string;
}

export interface UpgradeCheckoutRequest extends PaymentCheckoutRequest {
  readonly newPlanId: number;
}

export interface CompletePaymentCheckoutRequest {
  readonly paymentId: number;
}

export interface CheckoutPreparationResponse {
  readonly paymentId: number;
  readonly clientSecret: string;
  readonly publishableKey: string;
  readonly amount: number;
  readonly currency: string;
}

export interface SubscriptionResponse {
  readonly idAbonnement: number;
  readonly dateDebut: IsoDate;
  readonly dateFin: IsoDate;
  readonly statut: SubscriptionStatus;
  readonly prixPaye: number;
  readonly userId: number;
  readonly paiementId: number | null;
  readonly planAbonnement: PlanResponse;
}
