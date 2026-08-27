import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import {
  AuthApiService,
  OFFER_DURATIONS,
  OFFER_STATUSES,
  OfferDuration,
  OfferStatus,
  PlanResponse,
  PlatformEvent,
  PlatformApiService,
  PlatformOverview,
  PlatformSettings,
  PlatformServiceSummary,
  SubscriptionsApiService,
} from '../../core/api';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ApiError } from '../../core/http/api-error';

@Component({
  selector: 'app-platform-dashboard',
  imports: [DatePipe, FormsModule],
  templateUrl: './platform-dashboard.component.html',
  styleUrl: './platform-dashboard.component.scss',
})
export class PlatformDashboardComponent implements OnInit {
  private readonly platformApi = inject(PlatformApiService);
  private readonly subscriptionsApi = inject(SubscriptionsApiService);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  readonly currentUser = inject(AuthSessionService).currentUser;
  readonly overview = signal<PlatformOverview | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly audit = signal<readonly PlatformEvent[]>([]);
  readonly auditLoading = signal(true);
  readonly auditError = signal('');
  readonly settings = signal<PlatformSettings | null>(null);
  readonly settingsLoading = signal(true);
  readonly settingsError = signal('');
  readonly searchTerm = signal('');
  readonly plans = signal<readonly PlanResponse[]>([]);
  readonly planLoading = signal(true);
  readonly planNotice = signal('');
  readonly durations = OFFER_DURATIONS;
  readonly offerStatuses = OFFER_STATUSES;
  sidebarOpen = false;
  planPanelOpen = false;
  savingPlan = false;
  editingPlan: PlanResponse | null = null;
  planForm = this.emptyPlanForm();

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
    this.loadPlans();
    this.loadAudit();
    this.loadSettings();
  }

  loadAudit(): void {
    this.auditLoading.set(true);
    this.auditError.set('');
    this.platformApi.getAudit()
      .pipe(finalize(() => this.auditLoading.set(false)))
      .subscribe({
        next: (events) => this.audit.set(events),
        error: (error: unknown) => this.auditError.set(error instanceof ApiError ? error.message : 'The platform activity could not be loaded.'),
      });
  }

  loadSettings(): void {
    this.settingsLoading.set(true);
    this.settingsError.set('');
    this.platformApi.getSettings()
      .pipe(finalize(() => this.settingsLoading.set(false)))
      .subscribe({
        next: (settings) => this.settings.set(settings),
        error: (error: unknown) => this.settingsError.set(error instanceof ApiError ? error.message : 'Platform settings could not be loaded.'),
      });
  }

  loadPlans(): void {
    this.planLoading.set(true);
    this.subscriptionsApi.getPlans()
      .pipe(finalize(() => this.planLoading.set(false)))
      .subscribe({
        next: (plans) => this.plans.set(plans),
        error: () => this.planNotice.set('The plan catalogue could not be loaded.'),
      });
  }

  openPlan(plan?: PlanResponse): void {
    this.editingPlan = plan ?? null;
    this.planForm = plan
      ? {
          nomPlan: plan.nomPlan,
          description: plan.description ?? '',
          prix: plan.prix,
          duree: plan.duree,
          minJoursEntreDesactivation: plan.minJoursEntreDesactivation,
          maxPeriodeDesactivation: plan.maxPeriodeDesactivation,
          estActif: plan.estActif,
          limiteCommandesMois: plan.limiteCommandesMois,
        }
      : this.emptyPlanForm();
    this.planNotice.set('');
    this.planPanelOpen = true;
  }

  closePlan(): void {
    this.planPanelOpen = false;
    this.editingPlan = null;
  }

  savePlan(): void {
    if (!this.planForm.nomPlan.trim()) return;
    this.savingPlan = true;
    const request = { ...this.planForm, nomPlan: this.planForm.nomPlan.trim() };
    const operation = this.editingPlan
      ? this.subscriptionsApi.updatePlan(this.editingPlan.idPlan, request)
      : this.subscriptionsApi.createPlan(request);
    operation.pipe(finalize(() => this.savingPlan = false)).subscribe({
      next: () => {
        this.planNotice.set(this.editingPlan ? 'Plan updated.' : 'Plan created.');
        this.closePlan();
        this.loadPlans();
      },
      error: () => this.planNotice.set('The plan could not be saved.'),
    });
  }

  archivePlan(plan: PlanResponse): void {
    if (!confirm(`Archive the ${plan.nomPlan} plan? Existing subscriptions will keep their recorded plan.`)) return;
    this.subscriptionsApi.deletePlan(plan.idPlan).subscribe({
      next: () => {
        this.planNotice.set('Plan archived.');
        this.loadPlans();
      },
      error: () => this.planNotice.set('The plan could not be archived.'),
    });
  }

  planLabel(value: string): string {
    return value.replaceAll('_', ' ').toLowerCase().replace(/^./, (character) => character.toUpperCase());
  }

  planTone(status: OfferStatus): string {
    return status === 'ACTIF' ? 'success' : status === 'DESACTIVE' ? 'warning' : 'critical';
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

  private emptyPlanForm(): {
    nomPlan: string;
    description: string;
    prix: number;
    duree: OfferDuration;
    minJoursEntreDesactivation: number;
    maxPeriodeDesactivation: number;
    estActif: OfferStatus;
    limiteCommandesMois: number;
  } {
    return {
      nomPlan: '',
      description: '',
      prix: 0,
      duree: 'MENSUEL',
      minJoursEntreDesactivation: 0,
      maxPeriodeDesactivation: 0,
      estActif: 'ACTIF',
      limiteCommandesMois: 0,
    };
  }
}
