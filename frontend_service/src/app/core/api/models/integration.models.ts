import { IsoDateTime } from './common.models';

export type IntegrationPlatform = 'SHOPIFY' | 'WOOCOMMERCE';
export type IntegrationConnectionStatus = 'CONNECTED' | 'ACTION_REQUIRED' | 'ERROR' | 'DISCONNECTED';
export type IntegrationEventStatus = 'RECEIVED' | 'PROCESSED' | 'ACTION_REQUIRED' | 'FAILED';
export interface IntegrationCapabilities { readonly shopify:boolean;readonly woocommerce:boolean;readonly publicCallbacks:boolean;readonly inventoryAuthority:string;readonly message:string; }
export interface IntegrationConnectRequest { readonly displayName:string;readonly store:string;readonly stockLocationId:number; }
export interface IntegrationAuthorization { readonly authorizationUrl:string;readonly expiresAt:IsoDateTime; }
export interface StoreConnectionResponse { readonly id:number;readonly platform:IntegrationPlatform;readonly displayName:string;readonly storeUrl:string;readonly stockLocationId:number;readonly status:IntegrationConnectionStatus;readonly webhooksActive:boolean;readonly lastError:string|null;readonly lastSyncAt:IsoDateTime|null;readonly createdAt:IsoDateTime; }
export interface ExternalProductResponse { readonly productId:string;readonly variantId:string;readonly sku:string;readonly name:string;readonly salePrice:number|null;readonly availableQuantity:number|null; }
export interface ProductMappingRequest { readonly externalProductId:string;readonly externalVariantId:string;readonly externalSku?:string;readonly externalName:string;readonly internalProductId:number; }
export interface ProductMappingResponse extends ProductMappingRequest { readonly id:number;readonly connectionId:number;readonly createdAt:IsoDateTime; }
export interface IntegrationEventResponse { readonly id:number;readonly connectionId:number;readonly externalEventId:string;readonly topic:string;readonly status:IntegrationEventStatus;readonly errorMessage:string|null;readonly receivedAt:IsoDateTime;readonly processedAt:IsoDateTime|null; }
export interface AutoImportResponse { readonly importedCount:number;readonly skippedCount:number;readonly mappings:readonly ProductMappingResponse[]; }
