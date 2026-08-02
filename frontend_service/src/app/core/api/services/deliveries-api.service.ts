import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import {
  DeliveryResponse,
  ShipDeliveryRequest,
  UpdateDeliveryStatusRequest,
  DeliveryStatus,
  CarrierType,
  PageResponse,
} from '../models';

@Injectable({ providedIn: 'root' })
export class DeliveriesApiService {
  private readonly http = inject(HttpClient);
  private readonly url = buildApiUrl(inject(API_BASE_URL), '/api/v1/livraisons');

  search(
    page = 0,
    size = 20,
    status?: DeliveryStatus,
    carrier?: CarrierType,
  ): Observable<PageResponse<DeliveryResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status !== undefined) params = params.set('statut', status);
    if (carrier !== undefined) params = params.set('transporteur', carrier);
    return this.http.get<PageResponse<DeliveryResponse>>(this.url, { params });
  }

  getById(id: number): Observable<DeliveryResponse> {
    return this.http.get<DeliveryResponse>(`${this.url}/${id}`);
  }

  ship(request: ShipDeliveryRequest): Observable<DeliveryResponse> {
    return this.http.post<DeliveryResponse>(`${this.url}/expedier`, request);
  }

  getByTrackingNumber(trackingNumber: string): Observable<DeliveryResponse> {
    return this.http.get<DeliveryResponse>(
      `${this.url}/tracking/${encodeURIComponent(trackingNumber)}`,
    );
  }

  getByOrderId(orderId: number): Observable<DeliveryResponse> {
    return this.http.get<DeliveryResponse>(`${this.url}/commande/${orderId}`);
  }

  updateStatus(deliveryId: number, request: UpdateDeliveryStatusRequest): Observable<DeliveryResponse> {
    return this.http.patch<DeliveryResponse>(`${this.url}/${deliveryId}/statut`, request);
  }

  confirmReception(deliveryId: number): Observable<DeliveryResponse> {
    return this.http.post<DeliveryResponse>(`${this.url}/${deliveryId}/confirmer-reception`, null);
  }
}
