import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import {
  AuthApiService,
  PlatformApiService,
  PlatformOverview,
  PlatformServiceSummary,
} from '../../core/api';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ApiError } from '../../core/http/api-error';

@Component({
  selector: 'app-platform-dashboard',
  imports: [DatePipe],
  templateUrl: './platform-dashboard.component.html',
  styleUrl: './platform-dashboard.component.scss',
})
export class PlatformDashboardComponent implements OnInit {
  private readonly platformApi = inject(PlatformApiService);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  readonly currentUser = inject(AuthSessionService).currentUser;
  readonly overview = signal<PlatformOverview | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly searchTerm = signal('');
  sidebarOpen = false;

  readonly filteredServices = computed(() => {
    const query = this.searchTerm().trim().toLowerCase();
    const services = this.overview()?.services ?? [];
    return query ? services.filter((service) => `${service.name} ${service.serviceId}`.toLowerCase().includes(query)) : services;
  });

  readonly filteredTenants = computed(() => {
    const query = this.searchTerm().trim().toLowerCase();
    const tenants = this.overview()?.tenants ?? [];
    return query
      ? tenants.filter((tenant) => `${tenant.companyName ?? ''} ${tenant.activityType ?? ''} ${tenant.enterpriseId}`.toLowerCase().includes(query))
      : tenants;
  });

  readonly activeUserPercentage = computed(() => {
    const totals = this.overview()?.totals;
    return !totals || totals.users === 0 ? 0 : Math.round((totals.activeUsers / totals.users) * 100);
  });

  ngOnInit(): void {
    this.loadOverview();
  }

  loadOverview(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.platformApi.getOverview()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (overview) => this.overview.set(overview),
        error: (error: unknown) => {
          this.errorMessage.set(error instanceof ApiError
            ? error.message
            : 'The platform overview could not be loaded.');
        },
      });
  }

  updateSearch(event: Event): void {
    this.searchTerm.set((event.target as HTMLInputElement).value);
  }

  serviceMonogram(service: PlatformServiceSummary): string {
    return service.name.split(/\s+/).map((word) => word[0]).join('').slice(0, 2).toUpperCase();
  }

  logout(): void {
    this.authApi.logout();
    void this.router.navigateByUrl('/login');
  }

  openSidebar(): void { this.sidebarOpen = true; }
  closeSidebar(): void { this.sidebarOpen = false; }
}
