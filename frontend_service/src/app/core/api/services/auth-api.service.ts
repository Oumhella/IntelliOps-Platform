import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { finalize, Observable, tap } from 'rxjs';
import { AuthSessionService } from '../../auth/auth-session.service';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  UserResponse,
} from '../models';

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly session = inject(AuthSessionService);
  private readonly usersUrl = buildApiUrl(this.baseUrl, '/api/v1/users');

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.usersUrl}/login`, request, { withCredentials: true }).pipe(
      tap((response) => this.session.setSession(response)),
    );
  }

  register(request: RegisterRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${this.usersUrl}/register`, request);
  }

  logout(): void {
    this.http.post<void>(`${this.usersUrl}/logout`, {}, { withCredentials: true }).pipe(
      finalize(() => this.session.clear()),
    ).subscribe({ error: () => undefined });
  }
}
