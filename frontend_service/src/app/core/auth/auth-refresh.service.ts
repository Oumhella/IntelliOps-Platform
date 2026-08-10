import { HttpBackend, HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { finalize, Observable, shareReplay, tap } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api/api.config';
import { AuthResponse } from '../api/models';
import { AuthSessionService } from './auth-session.service';

@Injectable({ providedIn: 'root' })
export class AuthRefreshService {
  private readonly http = new HttpClient(inject(HttpBackend));
  private readonly session = inject(AuthSessionService);
  private readonly refreshUrl = buildApiUrl(inject(API_BASE_URL), '/api/v1/users/refresh');
  private inFlight: Observable<AuthResponse> | null = null;

  refresh(): Observable<AuthResponse> {
    if (this.inFlight === null) {
      this.inFlight = this.http.post<AuthResponse>(this.refreshUrl, {}, { withCredentials: true }).pipe(
        tap((response) => this.session.setSession(response)),
        finalize(() => this.inFlight = null),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.inFlight;
  }
}
