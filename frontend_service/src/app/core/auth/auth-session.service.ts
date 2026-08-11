import { computed, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { AuthResponse, UserResponse } from '../api/models';

const SESSION_STORAGE_KEY = 'intelliops.auth';

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly sessionState = signal<AuthResponse | null>(this.readStoredSession());

  readonly session = this.sessionState.asReadonly();
  readonly currentUser = computed(() => {
    const session = this.sessionState();
    return session === null
      ? null
      : {
          id: session.id,
          email: session.email,
          firstname: session.firstname,
          lastname: session.lastname,
          role: session.role,
          enterpriseId: session.enterpriseId,
        };
  });
  readonly isAuthenticated = computed(() => this.sessionState() !== null);

  setSession(session: AuthResponse): void {
    this.sessionState.set(session);
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    }
  }

  clear(): void {
    this.sessionState.set(null);
    if (isPlatformBrowser(this.platformId)) {
      localStorage.removeItem(SESSION_STORAGE_KEY);
    }
  }

  updateIdentity(profile: UserResponse): void {
    const session = this.sessionState();
    if (session === null) return;
    this.setSession({
      ...session,
      email: profile.email,
      firstname: profile.firstname,
      lastname: profile.lastname,
      role: profile.role,
    });
  }

  getToken(): string | null {
    return this.sessionState()?.token ?? null;
  }

  updateToken(token: string): void {
    const session = this.sessionState();
    if (session !== null) {
      this.setSession({ ...session, token });
    }
  }

  private readStoredSession(): AuthResponse | null {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }

    const storedValue = localStorage.getItem(SESSION_STORAGE_KEY);
    if (storedValue === null) {
      return null;
    }

    try {
      const session = JSON.parse(storedValue) as AuthResponse;
      if (!session.token) {
        localStorage.removeItem(SESSION_STORAGE_KEY);
        return null;
      }
      return session;
    } catch {
      localStorage.removeItem(SESSION_STORAGE_KEY);
      return null;
    }
  }

}
