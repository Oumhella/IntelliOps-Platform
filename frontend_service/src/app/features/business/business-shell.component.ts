import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthApiService, UserRole } from '../../core/api';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';
import { LanguageSwitcherComponent } from '../../core/i18n/language-switcher.component';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

@Component({
  selector: 'app-business-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, LanguageSwitcherComponent, TranslatePipe],
  templateUrl: './business-shell.component.html',
  styleUrl: './business-shell.component.scss',
})
export class BusinessShellComponent {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  readonly user = inject(AuthSessionService).currentUser;
  readonly feedback = inject(UiFeedbackService);
  sidebarOpen = false;

  hasRole(...roles: UserRole[]): boolean { return roles.includes(this.user()?.role as UserRole); }
  openSidebar(): void { this.sidebarOpen = true; }
  closeSidebar(): void { this.sidebarOpen = false; }
  logout(): void { this.authApi.logout(); void this.router.navigateByUrl('/login'); }
}
