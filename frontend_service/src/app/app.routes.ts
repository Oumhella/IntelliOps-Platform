import { Routes } from '@angular/router';
import { authenticatedGuard, businessUserGuard, guestGuard, roleGuard, superAdminGuard } from './core/auth/route.guards';

const ADMIN = ['ROLE_ADMIN'] as const;
const CRM = ['ROLE_ADMIN', 'ROLE_CSM'] as const;
const LOGISTICS = ['ROLE_ADMIN', 'ROLE_LOGISTIC'] as const;
const DELIVERY = ['ROLE_ADMIN', 'ROLE_LOGISTIC', 'ROLE_LIVREUR'] as const;
const BUSINESS = ['ROLE_ADMIN', 'ROLE_CSM', 'ROLE_LOGISTIC'] as const;
const WORKSPACE = ['ROLE_ADMIN', 'ROLE_CSM', 'ROLE_LOGISTIC', 'ROLE_LIVREUR'] as const;

export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/public/landing.component').then((m) => m.LandingComponent), title: 'IntelliOps — Commerce operations platform' },
  { path: 'login', loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent), canActivate: [guestGuard], title: 'Sign in — IntelliOps' },
  { path: 'register', loadComponent: () => import('./features/auth/register.component').then((m) => m.RegisterComponent), canActivate: [guestGuard], title: 'Create workspace — IntelliOps' },
  { path: 'super-admin', loadComponent: () => import('./features/super-admin/platform-dashboard.component').then((m) => m.PlatformDashboardComponent), canActivate: [authenticatedGuard, superAdminGuard], title: 'Platform control — IntelliOps' },
  { path: 'forgot-password', loadComponent: () => import('./features/auth/forgot-password.component').then(m => m.ForgotPasswordComponent), canActivate: [guestGuard], title: 'Forgot password - IntelliOps' },
  { path: 'reset-password', loadComponent: () => import('./features/auth/reset-password.component').then(m => m.ResetPasswordComponent), canActivate: [guestGuard], title: 'Reset password - IntelliOps' },
  {
    path: 'app',
    loadComponent: () => import('./features/business/business-shell.component').then((m) => m.BusinessShellComponent),
    canActivate: [authenticatedGuard, businessUserGuard],
    children: [
      { path: '', loadComponent: () => import('./features/business/business-home.component').then((m) => m.BusinessHomeComponent), title: 'Overview — IntelliOps' },
      { path: 'leads', loadComponent: () => import('./features/business/leads.component').then((m) => m.LeadsComponent), canActivate: [roleGuard], data: { roles: CRM }, title: 'Leads — IntelliOps' },
      { path: 'orders', loadComponent: () => import('./features/business/orders.component').then((m) => m.OrdersComponent), canActivate: [roleGuard], data: { roles: BUSINESS }, title: 'Orders — IntelliOps' },
      { path: 'stock', loadComponent: () => import('./features/business/stock.component').then((m) => m.StockComponent), canActivate: [roleGuard], data: { roles: LOGISTICS }, title: 'Stock — IntelliOps' },
      { path: 'integrations', loadComponent: () => import('./features/business/integrations.component').then((m) => m.IntegrationsComponent), canActivate: [roleGuard], data: { roles: ADMIN }, title: 'Store integrations — IntelliOps' },
      { path: 'deliveries', loadComponent: () => import('./features/business/deliveries.component').then((m) => m.DeliveriesComponent), canActivate: [roleGuard], data: { roles: DELIVERY }, title: 'Deliveries — IntelliOps' },
      { path: 'billing', loadComponent: () => import('./features/business/billing.component').then((m) => m.BillingComponent), canActivate: [roleGuard], data: { roles: ADMIN }, title: 'Billing — IntelliOps' },
      { path: 'subscriptions', loadComponent: () => import('./features/business/subscriptions.component').then((m) => m.SubscriptionsComponent), canActivate: [roleGuard], data: { roles: ADMIN }, title: 'Subscriptions — IntelliOps' },
      { path: 'team', loadComponent: () => import('./features/business/team.component').then((m) => m.TeamComponent), canActivate: [roleGuard], data: { roles: ADMIN }, title: 'Team — IntelliOps' },
      { path: 'notifications', loadComponent: () => import('./features/business/notifications.component').then((m) => m.NotificationsComponent), canActivate: [roleGuard], data: { roles: ADMIN }, title: 'Notification log — IntelliOps' },
      { path: 'assistant', loadComponent: () => import('./features/business/assistant.component').then((m) => m.AssistantComponent), canActivate: [roleGuard], data: { roles: WORKSPACE }, title: 'AI operations — IntelliOps' },
      { path: 'analytics', loadComponent: () => import('./features/business/analytics.component').then((m) => m.AnalyticsComponent), canActivate: [roleGuard], data: { roles: BUSINESS }, title: 'Conversational BI — IntelliOps' },
      { path: 'profile', loadComponent: () => import('./features/business/profile.component').then((m) => m.ProfileComponent), canActivate: [roleGuard], data: { roles: WORKSPACE }, title: 'Profile — IntelliOps' },
    ],
  },
  { path: '**', redirectTo: '' },
];
