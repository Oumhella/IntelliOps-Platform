import { IsoDateTime } from './common.models';

export const LEAD_STATUSES = [
  'NEW_LEAD', 'ATTEMPTED_CONTACT', 'IN_PROGRESS', 'SCHEDULED_RECALL',
  'UNREACHABLE', 'REFUSED', 'CONVERTED',
] as const;
export type LeadStatus = (typeof LEAD_STATUSES)[number];

export const LEAD_PRIORITIES = ['IMMEDIATE', 'HIGH', 'MEDIUM', 'LOW'] as const;
export type LeadPriority = (typeof LEAD_PRIORITIES)[number];

export const INTERACTION_TYPES = ['APPEL_TEL', 'WHATSAPP', 'EMAIL_AUTO'] as const;
export type InteractionType = (typeof INTERACTION_TYPES)[number];

export const ORDER_STATUSES = [
  'EN_ATTENTE', 'CONFIRMEE', 'PREPARATION', 'EXPEDIEE', 'LIVREE', 'ANNULEE', 'RETOURNEE',
] as const;
export type OrderStatus = (typeof ORDER_STATUSES)[number];

export interface CustomerDetails {
  readonly nomComplet: string;
  readonly email: string;
  readonly telephone: string;
  readonly adresseLivraison: string;
  readonly ville: string;
}

export interface LeadResponse {
  readonly idLead: number;
  readonly statutLead: LeadStatus;
  readonly ordrePriorite: LeadPriority;
  readonly infosClient: CustomerDetails;
  readonly boutiqueId: number | null;
  readonly agentId: number;
}

export interface CreateLeadRequest {
  readonly statutLead?: LeadStatus;
  readonly ordrePriorite: LeadPriority;
  readonly infosClient: CustomerDetails;
  readonly boutiqueId?: number;
}

export interface InteractionRequest {
  readonly typeInteraction: InteractionType;
  readonly nouveauStatut?: LeadStatus;
  readonly commentaireAgent: string;
}

export interface InteractionResponse {
  readonly idHistorique: number;
  readonly ancienStatut: LeadStatus;
  readonly nouveauStatut: LeadStatus;
  readonly typeInteraction: InteractionType;
  readonly dateChangement: IsoDateTime;
  readonly commentaireAgent: string;
}

export interface CreateOrderItemRequest {
  readonly productId: number;
  readonly quantity: number;
  readonly unitPrice: number;
}

export interface CreateOrderRequest {
  readonly totalAmount: number;
  readonly items: readonly CreateOrderItemRequest[];
}

export interface OrderLineResponse {
  readonly idLigne: number;
  readonly quantite: number;
  readonly prixUnitaireApplique: number;
  readonly produitId: number;
}

export interface OrderResponse {
  readonly idCommande: number;
  readonly reference: string;
  readonly totalPrix: number;
  readonly statutCommande: OrderStatus;
  readonly infosClient: CustomerDetails;
  readonly lignesCommande: readonly OrderLineResponse[];
}
