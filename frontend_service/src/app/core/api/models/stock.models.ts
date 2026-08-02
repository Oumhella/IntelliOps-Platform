import { IsoDateTime } from './common.models';

export const PLATFORM_TYPES = ['SHOPIFY', 'WOOCOMMERCE', 'YOUCAN', 'MAGENTO', 'AUTRE'] as const;
export type PlatformType = (typeof PLATFORM_TYPES)[number];

export const STOCK_MOVEMENT_TYPES = ['REASSORT', 'VENTE', 'RETOUR', 'PERTE', 'AJUSTEMENT'] as const;
export type StockMovementType = (typeof STOCK_MOVEMENT_TYPES)[number];

export interface StoreRequest {
  readonly nomBoutique: string;
  readonly plateformeType: PlatformType;
  readonly cleApi: string;
}

export interface StoreResponse {
  readonly idBoutique: number;
  readonly nomBoutique: string;
  readonly plateformeType: PlatformType;
  readonly adminId: number;
}

export interface ProductRequest {
  readonly nomProduit: string;
  readonly prixAchat: number;
  readonly prixVente: number;
  readonly globalSku: string;
}

export interface ProductResponse extends ProductRequest {
  readonly idProduit: number;
}

export interface UpdateStockRequest {
  readonly quantite: number;
  readonly typeMouvement: StockMovementType;
}

export interface ReplenishmentRuleRequest {
  readonly seuilAlerte: number;
  readonly quantiteRecommandeAuto: number;
  readonly estActif: boolean;
}

export interface ReplenishmentRuleResponse extends ReplenishmentRuleRequest {
  readonly id: number;
}

export interface StockMovementResponse {
  readonly id: number;
  readonly typeMouvement: StockMovementType;
  readonly quantite: number;
  readonly dateMouvement: IsoDateTime;
  readonly auteurId: number;
}

export interface InventoryResponse {
  readonly id: number;
  readonly quantiteDisponible: number;
  readonly quantiteReservee: number;
  readonly boutique: StoreResponse;
  readonly produit: ProductResponse;
  readonly regleApprovisionnement: ReplenishmentRuleResponse | null;
  readonly mouvements: readonly StockMovementResponse[];
}
