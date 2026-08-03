import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import {
  NotificationRequest,
  NotificationResponse,
  NotificationStatus,
  NotificationType,
  PageResponse,
} from '../models';

@Injectable({ providedIn: 'root' })
export class NotificationsApiService {
  private readonly http = inject(HttpClient);
  private readonly url = buildApiUrl(inject(API_BASE_URL), '/api/v1/notifications');

  search(
    page = 0,
    size = 20,
    status?: NotificationStatus,
    type?: NotificationType,
  ): Observable<PageResponse<NotificationResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status !== undefined) params = params.set('statut', status);
    if (type !== undefined) params = params.set('type', type);
    return this.http.get<PageResponse<NotificationResponse>>(this.url, { params });
  }

  getById(id: number): Observable<NotificationResponse> {
    return this.http.get<NotificationResponse>(`${this.url}/${id}`);
  }

  sendDirect(request: NotificationRequest): Observable<NotificationResponse> {
    return this.http.post<NotificationResponse>(`${this.url}/direct`, request);
  }
}
