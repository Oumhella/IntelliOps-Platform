import { IsoDateTime } from './common.models';

export const PAYMENT_CONTEXTS = ['COMMANDE_PRODUCT', 'ABONNEMENT_PLATFORM'] as const;
export type PaymentContext = (typeof PAYMENT_CONTEXTS)[number];

export const PAYMENT_MODES = ['CASH_ON_DELIVERY', 'CREDIT_CARD'] as const;
export type PaymentMode = (typeof PAYMENT_MODES)[number];

export const PAYMENT_STATUSES = [
  'PENDING', 'AWAITING_COLLECTION', 'AUTHORIZED', 'COMPLETED', 'FAILED',
  'CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED',
] as const;
export type PaymentStatus = (typeof PAYMENT_STATUSES)[number];

export interface InitiatePaymentRequest {
  readonly idempotencyKey: string;
  readonly referenceSourceId: number;
  readonly typeContexte: PaymentContext;
  readonly montant: number;
  readonly mode: PaymentMode;
}

export interface RefundRequest {
  readonly montant: number;
  readonly motif: string;
}

export interface PreparePaymentRequest {
  readonly idempotencyKey: string;
  readonly referenceSourceId: number;
  readonly typeContexte: PaymentContext;
  readonly montant: number;
}

export interface OrderPaymentRequest {
  readonly idempotencyKey: string;
}

export interface PaymentPreparationResponse {
  readonly paymentId: number;
  readonly clientSecret: string;
  readonly publishableKey: string;
  readonly amount: number;
  readonly currency: string;
  readonly status: PaymentStatus;
}

export interface InvoiceResponse {
  readonly id: number;
  readonly numeroFactureUnique: string;
  readonly cheminFichierPdf: string;
  readonly dateEmission: IsoDateTime;
}

export interface PaymentTransactionResponse {
  readonly id: number;
  readonly idempotencyKey: string;
  readonly referenceSourceId: number;
  readonly typeContexte: PaymentContext;
  readonly montant: number;
  readonly montantRembourse: number;
  readonly mode: PaymentMode;
  readonly statut: PaymentStatus;
  readonly providerTransactionId: string | null;
  readonly consumptionReference: string | null;
  readonly consumedAt: IsoDateTime | null;
  readonly facture: InvoiceResponse | null;
}
