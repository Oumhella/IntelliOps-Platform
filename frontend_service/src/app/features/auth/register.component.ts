import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthApiService, PlanResponse, SubscriptionsApiService } from '../../core/api';
import { ApiError } from '../../core/http/api-error';
import { I18nService } from '../../core/i18n/i18n.service';
import { LanguageSwitcherComponent } from '../../core/i18n/language-switcher.component';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

const ONBOARDING_PLAN_KEY = 'intelliops.onboarding-plan';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink, LanguageSwitcherComponent, TranslatePipe],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent implements OnInit {
  readonly i18n = inject(I18nService);
  private readonly api = inject(AuthApiService);
  private readonly subscriptions = inject(SubscriptionsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly plans = signal<readonly PlanResponse[]>([]);
  submitting = false;
  loadingPlans = true;
  errorMessage = '';
  planId: number | null = null;
  form = { companyName: '', activityType: '', email: '', password: '', firstname: '', lastname: '', phone: '' };

  ngOnInit(): void {
    const requestedPlan = Number(this.route.snapshot.queryParamMap.get('plan'));
    this.planId = Number.isSafeInteger(requestedPlan) && requestedPlan > 0 ? requestedPlan : null;
    this.subscriptions.getPlans('ACTIF').pipe(finalize(() => this.loadingPlans = false)).subscribe({
      next: (plans) => {
        this.plans.set(plans);
        if (this.planId === null || !plans.some((plan) => plan.idPlan === this.planId)) {
          this.planId = plans[0]?.idPlan ?? null;
        }
      },
      error: () => this.errorMessage = 'Plans could not be loaded. Registration is paused to avoid creating an unusable workspace.',
    });
  }

  selectedPlan(): PlanResponse | undefined {
    return this.plans().find((plan) => plan.idPlan === this.planId);
  }

  submit(): void {
    const form = this.form;
    if (!this.planId || !form.companyName.trim() || !form.activityType.trim() || !form.email.trim()
        || !form.password || !form.firstname.trim() || !form.lastname.trim() || this.submitting) {
      this.errorMessage = 'Choose a plan and complete every required field.';
      return;
    }
    this.submitting = true;
    this.errorMessage = '';
    this.api.register({
      companyName: form.companyName.trim(), activityType: form.activityType.trim(), email: form.email.trim(),
      password: form.password, firstname: form.firstname.trim(), lastname: form.lastname.trim(),
      phone: form.phone.trim() || undefined,
    }).pipe(finalize(() => this.submitting = false)).subscribe({
      next: () => {
        globalThis.sessionStorage?.setItem(ONBOARDING_PLAN_KEY, String(this.planId));
        void this.router.navigate(['/login'], { queryParams: { registered: 'true', plan: this.planId } });
      },
      error: (error: unknown) => this.errorMessage = error instanceof ApiError ? error.message : 'Registration failed.',
    });
  }
}
