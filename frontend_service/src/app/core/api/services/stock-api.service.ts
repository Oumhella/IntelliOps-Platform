import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import {
  InventoryResponse,
  ProductRequest,
  ProductResponse,
  SalesProductResponse,
  ReplenishmentRuleRequest,
  StoreRequest,
  StoreResponse,
  UpdateStockRequest,
} from '../models';

@Injectable({ providedIn: 'root' })
export class StockApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly storesUrl = buildApiUrl(this.baseUrl, '/api/v1/boutiques');
  private readonly productsUrl = buildApiUrl(this.baseUrl, '/api/v1/produits');
  private readonly inventoryUrl = buildApiUrl(this.baseUrl, '/api/v1/inventaires');

  createStore(request: StoreRequest): Observable<StoreResponse> {
    return this.http.post<StoreResponse>(this.storesUrl, request);
  }

  getStores(): Observable<readonly StoreResponse[]> {
    return this.http.get<readonly StoreResponse[]>(this.storesUrl);
  }

  getStoreById(id: number): Observable<StoreResponse> {
    return this.http.get<StoreResponse>(`${this.storesUrl}/${id}`);
  }

  updateStore(id: number, request: StoreRequest): Observable<StoreResponse> {
    return this.http.put<StoreResponse>(`${this.storesUrl}/${id}`, request);
  }

  createProduct(request: ProductRequest): Observable<ProductResponse> {
    return this.http.post<ProductResponse>(this.productsUrl, request);
  }

  getProductById(id: number): Observable<ProductResponse> {
    return this.http.get<ProductResponse>(`${this.productsUrl}/${id}`);
  }

  getProducts(): Observable<readonly ProductResponse[]> {
    return this.http.get<readonly ProductResponse[]>(this.productsUrl);
  }

  getSalesCatalog(): Observable<readonly SalesProductResponse[]> {
    return this.http.get<readonly SalesProductResponse[]>(`${this.productsUrl}/catalog`);
  }

  updateProduct(id: number, request: ProductRequest): Observable<ProductResponse> {
    return this.http.put<ProductResponse>(`${this.productsUrl}/${id}`, request);
  }

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`${this.productsUrl}/${id}`);
  }

  adjustStock(storeId: number, productId: number, request: UpdateStockRequest): Observable<InventoryResponse> {
    return this.http.patch<InventoryResponse>(
      `${this.inventoryUrl}/boutiques/${storeId}/produits/${productId}/ajuster`,
      request,
    );
  }

  getInventory(storeId: number, productId: number): Observable<InventoryResponse> {
    return this.http.get<InventoryResponse>(
      `${this.inventoryUrl}/boutiques/${storeId}/produits/${productId}`,
    );
  }

  getStoreInventory(storeId: number): Observable<readonly InventoryResponse[]> {
    return this.http.get<readonly InventoryResponse[]>(`${this.inventoryUrl}/boutiques/${storeId}`);
  }

  configureReplenishmentRule(
    inventoryId: number,
    request: ReplenishmentRuleRequest,
  ): Observable<InventoryResponse> {
    return this.http.put<InventoryResponse>(
      `${this.inventoryUrl}/${inventoryId}/regle-approvisionnement`,
      request,
    );
  }
}
