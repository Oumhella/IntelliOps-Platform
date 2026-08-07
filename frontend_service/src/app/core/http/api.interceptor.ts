import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api/api.config';
import { AuthSessionService } from '../auth/auth-session.service';
import { ApiError } from './api-error';

export const apiInterceptor: HttpInterceptorFn = (request, next) => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);
  const apiPrefix = buildApiUrl(inject(API_BASE_URL), '/api/');
  const isApiRequest = request.url.startsWith(apiPrefix);
  const token = isApiRequest ? authSession.getToken() : null;
  const authenticatedRequest = token === null
    ? request
    : request.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

  return next(authenticatedRequest).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      if (isApiRequest && error.status === 401) {
        authSession.clear();
        void router.navigateByUrl('/login');
      }
      if (isApiRequest && error.status === 402
          && authSession.currentUser()?.role === 'ROLE_ADMIN'
          && !router.url.startsWith('/app/subscriptions')) {
        void router.navigateByUrl('/app/subscriptions');
      }
      return throwError(() => ApiError.fromHttpError(error));
    }),
  );
};
