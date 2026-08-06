import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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
export class LoginComponent {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  email = '';
  password = '';
  submitting = false;
  errorMessage = '';

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
          const destination = session.role === 'ROLE_SUPER_ADMIN' ? '/super-admin' : '/app';
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
