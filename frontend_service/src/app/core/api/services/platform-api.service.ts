import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import { PlatformEvent, PlatformOverview, PlatformSettings } from '../models';

@Injectable({ providedIn: 'root' })
export class PlatformApiService {
  private readonly http = inject(HttpClient);
  private readonly url = buildApiUrl(inject(API_BASE_URL), '/api/v1/platform');

  getOverview(): Observable<PlatformOverview> {
    return this.http.get<PlatformOverview>(`${this.url}/overview`);
  }

  getAudit(): Observable<readonly PlatformEvent[]> {
    return this.http.get<readonly PlatformEvent[]>(`${this.url}/audit`);
  }

  getSettings(): Observable<PlatformSettings> {
    return this.http.get<PlatformSettings>(`${this.url}/settings`);
  }
}
