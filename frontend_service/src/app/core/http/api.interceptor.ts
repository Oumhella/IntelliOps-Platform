import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api/api.config';
import { AuthSessionService } from '../auth/auth-session.service';
import { ApiError } from './api-error';

export const apiInterceptor: HttpInterceptorFn = (request, next) => {
  const authSession = inject(AuthSessionService);
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
      }
      return throwError(() => ApiError.fromHttpError(error));
    }),
  );
};
