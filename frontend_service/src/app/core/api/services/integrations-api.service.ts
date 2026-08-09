import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import { AutoImportResponse, ExternalProductResponse, IntegrationAuthorization, IntegrationCapabilities, IntegrationConnectRequest, IntegrationEventResponse, ProductMappingRequest, ProductMappingResponse, StoreConnectionResponse } from '../models';

@Injectable({providedIn:'root'})
export class IntegrationsApiService {
  private readonly http=inject(HttpClient);private readonly url=buildApiUrl(inject(API_BASE_URL),'/api/v1/integrations');
  capabilities():Observable<IntegrationCapabilities>{return this.http.get<IntegrationCapabilities>(`${this.url}/capabilities`)}
  connections():Observable<readonly StoreConnectionResponse[]>{return this.http.get<readonly StoreConnectionResponse[]>(`${this.url}/connections`)}
  connectShopify(request:IntegrationConnectRequest):Observable<IntegrationAuthorization>{return this.http.post<IntegrationAuthorization>(`${this.url}/shopify/connect`,request)}
  connectWooCommerce(request:IntegrationConnectRequest):Observable<IntegrationAuthorization>{return this.http.post<IntegrationAuthorization>(`${this.url}/woocommerce/connect`,request)}
  externalProducts(id:number):Observable<readonly ExternalProductResponse[]>{return this.http.get<readonly ExternalProductResponse[]>(`${this.url}/connections/${id}/products`)}
  mappings(id:number):Observable<readonly ProductMappingResponse[]>{return this.http.get<readonly ProductMappingResponse[]>(`${this.url}/connections/${id}/mappings`)}
  createMapping(id:number,request:ProductMappingRequest):Observable<ProductMappingResponse>{return this.http.post<ProductMappingResponse>(`${this.url}/connections/${id}/mappings`,request)}
  deleteMapping(id:number):Observable<void>{return this.http.delete<void>(`${this.url}/mappings/${id}`)}
  disconnect(id:number):Observable<StoreConnectionResponse>{return this.http.delete<StoreConnectionResponse>(`${this.url}/connections/${id}`)}
  events():Observable<readonly IntegrationEventResponse[]>{return this.http.get<readonly IntegrationEventResponse[]>(`${this.url}/events`)}
  autoImport(id:number):Observable<AutoImportResponse>{return this.http.post<AutoImportResponse>(`${this.url}/connections/${id}/auto-import`,{})}
}
