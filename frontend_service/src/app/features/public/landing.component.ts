import { DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PlanResponse, SubscriptionsApiService } from '../../core/api';
import { AuthSessionService } from '../../core/auth/auth-session.service';

@Component({
  selector: 'app-landing',
  imports: [RouterLink, DecimalPipe],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.scss',
})
export class LandingComponent implements OnInit {
  private readonly subscriptions = inject(SubscriptionsApiService);
  readonly session = inject(AuthSessionService);
  readonly plans = signal<readonly PlanResponse[]>([]);
  readonly loadingPlans = signal(true);
  readonly plansUnavailable = signal(false);

  ngOnInit(): void {
    this.subscriptions.getPlans('ACTIF').subscribe({
      next: (plans) => {
        this.plans.set(plans);
        this.loadingPlans.set(false);
      },
      error: () => {
        this.plansUnavailable.set(true);
        this.loadingPlans.set(false);
      },
    });
  }

  dashboardUrl(): string {
    return this.session.currentUser()?.role === 'ROLE_SUPER_ADMIN' ? '/super-admin' : '/app';
  }

  duration(value: string): string {
    return ({ HEBDOMADAIRE: 'week', MENSUEL: 'month', TRIMESTRIEL: 'quarter', ANNUEL: 'year' } as Record<string, string>)[value] ?? value.toLowerCase();
  }
}
