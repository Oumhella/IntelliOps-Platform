import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import type { UserRole } from '../api/models';
import { AuthSessionService } from './auth-session.service';

function destinationForCurrentSession(): string {
  const session = inject(AuthSessionService);
  return session.currentUser()?.role === 'ROLE_SUPER_ADMIN' ? '/super-admin' : '/app';
}

export const entryGuard: CanActivateFn = () => {
  const session = inject(AuthSessionService);
  const router = inject(Router);
  return router.parseUrl(session.isAuthenticated() ? destinationForCurrentSession() : '/login');
};

export const guestGuard: CanActivateFn = () => {
  const session = inject(AuthSessionService);
  return session.isAuthenticated() ? inject(Router).parseUrl(destinationForCurrentSession()) : true;
};

export const authenticatedGuard: CanActivateFn = (_route, state) => {
  const session = inject(AuthSessionService);
  return session.isAuthenticated()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export const superAdminGuard: CanActivateFn = () => {
  const session = inject(AuthSessionService);
  return session.currentUser()?.role === 'ROLE_SUPER_ADMIN'
    ? true
    : inject(Router).parseUrl('/app');
};

export const businessUserGuard: CanActivateFn = () => {
  const session = inject(AuthSessionService);
  return session.currentUser()?.role === 'ROLE_SUPER_ADMIN'
    ? inject(Router).parseUrl('/super-admin')
    : true;
};

export const roleGuard: CanActivateFn = (route) => {
  const role = inject(AuthSessionService).currentUser()?.role;
  const allowedRoles = route.data['roles'] as readonly UserRole[] | undefined;
  return role !== undefined && (allowedRoles === undefined || allowedRoles.includes(role))
    ? true
    : inject(Router).parseUrl('/app');
};
