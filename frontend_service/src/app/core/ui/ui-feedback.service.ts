import { Injectable, signal } from '@angular/core';
import { ApiError } from '../http/api-error';

export interface UiNotice {
  readonly tone: 'success' | 'error';
  readonly message: string;
}

@Injectable({ providedIn: 'root' })
export class UiFeedbackService {
  readonly notice = signal<UiNotice | null>(null);
  private timer: ReturnType<typeof setTimeout> | null = null;

  success(message: string): void { this.show({ tone: 'success', message }); }
  error(error: unknown, fallback = 'The operation could not be completed.'): void {
    this.show({ tone: 'error', message: error instanceof ApiError ? error.message : fallback });
  }
  clear(): void { this.notice.set(null); }

  private show(notice: UiNotice): void {
    if (this.timer !== null) clearTimeout(this.timer);
    this.notice.set(notice);
    this.timer = setTimeout(() => this.notice.set(null), 4500);
  }
}
