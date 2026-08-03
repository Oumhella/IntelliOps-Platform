import { Routes } from '@angular/router';
import {
  authenticatedGuard,
  businessUserGuard,
  entryGuard,
  guestGuard,
  superAdminGuard,
} from './core/auth/route.guards';
import { LoginComponent } from './features/auth/login.component';
import { BusinessHomeComponent } from './features/business/business-home.component';
import { PlatformDashboardComponent } from './features/super-admin/platform-dashboard.component';

export const routes: Routes = [
  { path: '', component: BusinessHomeComponent, canActivate: [entryGuard] },
  { path: 'login', component: LoginComponent, canActivate: [guestGuard], title: 'Sign in — IntelliOps' },
  {
    path: 'super-admin',
    component: PlatformDashboardComponent,
    canActivate: [authenticatedGuard, superAdminGuard],
    title: 'Platform control — IntelliOps',
  },
  {
    path: 'app',
    component: BusinessHomeComponent,
    canActivate: [authenticatedGuard, businessUserGuard],
    title: 'Workspace — IntelliOps',
  },
  { path: '**', redirectTo: '' },
];
