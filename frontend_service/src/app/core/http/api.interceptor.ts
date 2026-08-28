import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api/api.config';
import { AuthSessionService } from '../auth/auth-session.service';
import { AuthRefreshService } from '../auth/auth-refresh.service';
import { ApiError } from './api-error';
import { I18nService } from '../i18n/i18n.service';

export const apiInterceptor: HttpInterceptorFn = (request, next) => {
  const authSession = inject(AuthSessionService);
  const authRefresh = inject(AuthRefreshService);
  const router = inject(Router);
  const i18n = inject(I18nService);
  const apiPrefix = buildApiUrl(inject(API_BASE_URL), '/api/');
  const isApiRequest = request.url.startsWith(apiPrefix);
  const isAuthLifecycleRequest = ['/login', '/register', '/refresh', '/logout']
    .some((suffix) => request.url.endsWith(`/api/v1/users${suffix}`));
  const token = isApiRequest ? authSession.getToken() : null;
  const localizedRequest = isApiRequest
    ? request.clone({ setHeaders: { 'Accept-Language': i18n.language() } }) : request;
  const authenticatedRequest = token === null
    ? localizedRequest
    : localizedRequest.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

  return next(authenticatedRequest).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      if (isApiRequest && error.status === 401) {
        if (!isAuthLifecycleRequest && token !== null) {
          return authRefresh.refresh().pipe(
            catchError((refreshError: unknown) => {
              const rejected = refreshError instanceof HttpErrorResponse
                && (refreshError.status === 401 || refreshError.status === 403);
              if (rejected) {
                authSession.clear();
                void router.navigateByUrl('/login');
              }
              return throwError(() => refreshError instanceof HttpErrorResponse
                ? ApiError.fromHttpError(refreshError)
                : refreshError);
            }),
            switchMap((session) => next(localizedRequest.clone({
              setHeaders: { Authorization: `Bearer ${session.token}`, 'Accept-Language': i18n.language() },
            })).pipe(catchError((retryError: unknown) => throwError(() =>
              retryError instanceof HttpErrorResponse ? ApiError.fromHttpError(retryError) : retryError)))),
          );
        }
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
