import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import {
  CreateLeadRequest,
  CreateOrderRequest,
  InteractionRequest,
  InteractionResponse,
  LeadResponse,
  OrderResponse,
  OrderStatus,
  LeadStatus,
  PageResponse,
} from '../models';

@Injectable({ providedIn: 'root' })
export class CrmApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly leadsUrl = buildApiUrl(this.baseUrl, '/api/v1/leads');
  private readonly ordersUrl = buildApiUrl(this.baseUrl, '/api/v1/commandes');

  createLead(request: CreateLeadRequest): Observable<LeadResponse> {
    return this.http.post<LeadResponse>(this.leadsUrl, request);
  }

  searchLeads(
    page = 0,
    size = 20,
    status?: LeadStatus,
    agentId?: number,
  ): Observable<PageResponse<LeadResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status !== undefined) params = params.set('statut', status);
    if (agentId !== undefined) params = params.set('agentId', agentId);
    return this.http.get<PageResponse<LeadResponse>>(this.leadsUrl, { params });
  }

  getLeadById(id: number): Observable<LeadResponse> {
    return this.http.get<LeadResponse>(`${this.leadsUrl}/${id}`);
  }

  getLeadsByAgent(agentId: number): Observable<readonly LeadResponse[]> {
    return this.http.get<readonly LeadResponse[]>(`${this.leadsUrl}/agent/${agentId}`);
  }

  assignAgent(leadId: number, agentId: number): Observable<LeadResponse> {
    const params = new HttpParams().set('agentId', agentId);
    return this.http.put<LeadResponse>(`${this.leadsUrl}/${leadId}/assigner`, null, { params });
  }

  addInteraction(leadId: number, request: InteractionRequest): Observable<InteractionResponse> {
    return this.http.post<InteractionResponse>(`${this.leadsUrl}/${leadId}/interactions`, request);
  }

  convertToOrder(leadId: number, request: CreateOrderRequest): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(`${this.leadsUrl}/${leadId}/convertir`, request);
  }

  getOrderById(id: number): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${this.ordersUrl}/${id}`);
  }

  searchOrders(page = 0, size = 20, status?: OrderStatus): Observable<PageResponse<OrderResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status !== undefined) params = params.set('statut', status);
    return this.http.get<PageResponse<OrderResponse>>(this.ordersUrl, { params });
  }

  addProductToOrder(
    orderId: number,
    productId: number,
    quantity: number,
    unitPrice: number,
  ): Observable<OrderResponse> {
    const params = new HttpParams()
      .set('produitId', productId)
      .set('quantite', quantity)
      .set('prixUnitaire', unitPrice);
    return this.http.post<OrderResponse>(`${this.ordersUrl}/${orderId}/lignes`, null, { params });
  }

  changeOrderStatus(orderId: number, status: OrderStatus): Observable<OrderResponse> {
    const params = new HttpParams().set('nouveauStatut', status);
    return this.http.put<OrderResponse>(`${this.ordersUrl}/${orderId}/statut`, null, { params });
  }
}
