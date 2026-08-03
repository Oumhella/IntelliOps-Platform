import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthApiService } from '../../core/api';
import { AuthSessionService } from '../../core/auth/auth-session.service';

@Component({
  selector: 'app-business-home',
  templateUrl: './business-home.component.html',
  styleUrl: './business-home.component.scss',
})
export class BusinessHomeComponent {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  readonly user = inject(AuthSessionService).currentUser;

  readonly roleLabels: Record<string, string> = {
    ROLE_ADMIN: 'Enterprise administrator',
    ROLE_CSM: 'Customer success manager',
    ROLE_LOGISTIC: 'Logistics operator',
  };

  logout(): void {
    this.authApi.logout();
    void this.router.navigateByUrl('/login');
  }
}
