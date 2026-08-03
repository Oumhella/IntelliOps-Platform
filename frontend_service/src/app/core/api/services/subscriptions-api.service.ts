import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import {
  OfferStatus,
  PageResponse,
  PlanRequest,
  PlanResponse,
  SubscriptionRequest,
  SubscriptionResponse,
  SubscriptionStatus,
} from '../models';

@Injectable({ providedIn: 'root' })
export class SubscriptionsApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly plansUrl = buildApiUrl(this.baseUrl, '/api/v1/plans');
  private readonly subscriptionsUrl = buildApiUrl(this.baseUrl, '/api/v1/abonnements');

  createPlan(request: PlanRequest): Observable<PlanResponse> {
    return this.http.post<PlanResponse>(this.plansUrl, request);
  }

  getPlans(status?: OfferStatus): Observable<readonly PlanResponse[]> {
    const params = status === undefined ? undefined : new HttpParams().set('statut', status);
    return this.http.get<readonly PlanResponse[]>(this.plansUrl, { params });
  }

  getPlanById(id: number): Observable<PlanResponse> {
    return this.http.get<PlanResponse>(`${this.plansUrl}/${id}`);
  }

  updatePlan(id: number, request: PlanRequest): Observable<PlanResponse> {
    return this.http.put<PlanResponse>(`${this.plansUrl}/${id}`, request);
  }

  deletePlan(id: number): Observable<void> {
    return this.http.delete<void>(`${this.plansUrl}/${id}`);
  }

  subscribe(request: SubscriptionRequest): Observable<SubscriptionResponse> {
    return this.http.post<SubscriptionResponse>(this.subscriptionsUrl, request);
  }

  searchSubscriptions(
    page = 0,
    size = 20,
    status?: SubscriptionStatus,
  ): Observable<PageResponse<SubscriptionResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status !== undefined) {
      params = params.set('statut', status);
    }
    return this.http.get<PageResponse<SubscriptionResponse>>(this.subscriptionsUrl, { params });
  }

  getSubscriptionById(id: number): Observable<SubscriptionResponse> {
    return this.http.get<SubscriptionResponse>(`${this.subscriptionsUrl}/${id}`);
  }

  getUserSubscriptionHistory(userId: number): Observable<readonly SubscriptionResponse[]> {
    return this.http.get<readonly SubscriptionResponse[]>(`${this.subscriptionsUrl}/utilisateur/${userId}`);
  }

  suspend(id: number, reason: string): Observable<void> {
    const params = new HttpParams().set('motif', reason);
    return this.http.post<void>(`${this.subscriptionsUrl}/${id}/suspendre`, null, { params });
  }

  renew(id: number, paymentId: number): Observable<void> {
    const params = new HttpParams().set('paiementId', paymentId);
    return this.http.post<void>(`${this.subscriptionsUrl}/${id}/renouveler`, null, { params });
  }

  upgrade(id: number, newPlanId: number): Observable<void> {
    const params = new HttpParams().set('nouveauPlanId', newPlanId);
    return this.http.put<void>(`${this.subscriptionsUrl}/${id}/upgrade`, null, { params });
  }

  getRemainingDays(id: number): Observable<number> {
    return this.http.get<number>(`${this.subscriptionsUrl}/${id}/duree-restante`);
  }

  canCreateOrder(id: number, completedOrders: number): Observable<boolean> {
    const params = new HttpParams().set('commandesEffectuees', completedOrders);
    return this.http.get<boolean>(`${this.subscriptionsUrl}/${id}/verifier-limite`, { params });
  }

  checkExpiration(id: number): Observable<boolean> {
    return this.http.post<boolean>(`${this.subscriptionsUrl}/${id}/verifier-expiration`, null);
  }
}
