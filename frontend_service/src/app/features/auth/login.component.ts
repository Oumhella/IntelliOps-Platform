import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthApiService } from '../../core/api';
import { ApiError } from '../../core/http/api-error';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  email = '';
  password = '';
  showPassword = false;
  submitting = false;
  errorMessage = '';
  registrationMessage = '';
  private onboardingPlanId: number | null = null;

  ngOnInit(): void {
    const registered = this.route.snapshot.queryParamMap.get('registered') === 'true';
    const requestedPlan = Number(this.route.snapshot.queryParamMap.get('plan'));
    const storedPlan = Number(globalThis.sessionStorage?.getItem('intelliops.onboarding-plan'));
    const planId = Number.isSafeInteger(requestedPlan) && requestedPlan > 0 ? requestedPlan : storedPlan;
    this.onboardingPlanId = registered && Number.isSafeInteger(planId) && planId > 0 ? planId : null;
    if (registered) {
      this.registrationMessage = 'Workspace created. Sign in to securely activate the selected plan.';
    }
  }

  submit(): void {
    if (!this.email.trim() || !this.password || this.submitting) {
      this.errorMessage = 'Enter your email address and password.';
      return;
    }

    this.submitting = true;
    this.errorMessage = '';
    this.authApi.login({ email: this.email.trim(), password: this.password })
      .pipe(finalize(() => this.submitting = false))
      .subscribe({
        next: (session) => {
          if (session.role === 'ROLE_SUPER_ADMIN') {
            void this.router.navigateByUrl('/super-admin');
            return;
          }
          if (session.role === 'ROLE_ADMIN' && this.onboardingPlanId !== null) {
            void this.router.navigate(['/app/subscriptions'], {
              queryParams: { onboarding: '1', plan: this.onboardingPlanId },
            });
            return;
          }
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
          const destination = returnUrl?.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/app';
          void this.router.navigateByUrl(destination);
        },
        error: (error: unknown) => {
          this.errorMessage = error instanceof ApiError
            ? error.message
            : 'Sign-in failed. Please try again.';
        },
      });
  }
}
