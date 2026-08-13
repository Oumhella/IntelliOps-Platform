import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import { AnalyticsResponse } from '../models/analytics.models';

@Injectable({ providedIn: 'root' })
export class AnalyticsApiService {
  private readonly http = inject(HttpClient);
  private readonly url = buildApiUrl(inject(API_BASE_URL), '/api/v1/analytics');
  suggestions(): Observable<{ suggestions: string[] }> { return this.http.get<{ suggestions: string[] }>(`${this.url}/suggestions`); }
  ask(question: string): Observable<AnalyticsResponse> { return this.http.post<AnalyticsResponse>(`${this.url}/ask`, { question }); }
}
