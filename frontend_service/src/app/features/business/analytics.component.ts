import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { AnalyticsApiService, AnalyticsResponse } from '../../core/api';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { I18nService } from '../../core/i18n/i18n.service';

interface AnalyticsMessage {
  role: 'user' | 'assistant';
  text: string;
  question?: string;
  response?: AnalyticsResponse;
  failed?: boolean;
}

@Component({
  selector: 'app-analytics',
  imports: [FormsModule, TranslatePipe],
  templateUrl: './analytics.component.html',
  styleUrl: './analytics.component.scss',
})
export class AnalyticsComponent implements OnInit {
  readonly i18n = inject(I18nService);
  private readonly api = inject(AnalyticsApiService);
  readonly feedback = inject(UiFeedbackService);
  readonly suggestions = signal<readonly string[]>([]);
  readonly messages = signal<AnalyticsMessage[]>([]);
  readonly role = signal('');
  readonly exporting = signal<string | null>(null);
  readonly palette = ['#4f46e5', '#06b6d4', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6'];
  question = '';
  sending = false;

  ngOnInit(): void {
    this.api.suggestions().subscribe({
      next: (value) => {
        this.role.set(value.role);
        this.suggestions.set(value.suggestions);
      },
      error: (error) => this.feedback.error(error),
    });
    this.api.history('BI').subscribe({
      next: (items) => this.messages.set(items.map((item) => ({
        role: item.role,
        text: item.content,
        response: item.payload as unknown as AnalyticsResponse,
      }))),
      error: () => undefined,
    });
  }

  ask(question = this.question): void {
    const value = question.trim();
    if (!value || this.sending) return;
    this.messages.update((items) => [...items, { role: 'user', text: value }]);
    this.api.store('BI', 'user', value).subscribe({ error: () => undefined });
    this.question = '';
    this.sending = true;
    this.api.ask(value).subscribe({
      next: (response) => {
        this.sending = false;
        this.messages.update((items) => [...items, {
          role: 'assistant', text: response.answer, question: value, response,
        }]);
        this.api.store(
          'BI', 'assistant', response.answer, response as unknown as Record<string, unknown>,
        ).subscribe({ error: () => undefined });
      },
      error: (error) => {
        this.sending = false;
        this.messages.update((items) => [...items, {
          role: 'assistant',
          text: 'This analysis could not be completed. Refresh your session or try an approved question.',
          question: value,
          failed: true,
        }]);
        this.feedback.error(error);
      },
    });
  }

  exportCsv(message: AnalyticsMessage): void {
    if (!message.response) return;
    const question = message.question ?? message.response.question;
    this.exporting.set(question);
    this.api.exportCsv(question).pipe(
      finalize(() => this.exporting.set(null)),
    ).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `intelliops-${message.response?.metadata.metric ?? 'report'}.csv`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (error) => this.feedback.error(error, 'The report could not be exported.'),
    });
  }

  cell(row: Record<string, unknown>, name: string): unknown { return row[name]; }
  numeric(row: Record<string, unknown>, name?: string): number {
    const value = Number(name ? row[name] : 0);
    return Number.isFinite(value) ? value : 0;
  }
  max(response: AnalyticsResponse): number {
    return Math.max(1, ...response.result.rows.map((row) =>
      this.numeric(row, response.visualization.y)));
  }
  barWidth(row: Record<string, unknown>, response: AnalyticsResponse): string {
    return `${Math.max(2, this.numeric(row, response.visualization.y) / this.max(response) * 100)}%`;
  }
  donutBackground(response: AnalyticsResponse): string {
    const values = response.result.rows.map((row) => this.numeric(row, response.visualization.y));
    const total = values.reduce((sum, value) => sum + value, 0) || 1;
    let offset = 0;
    const stops = values.map((value, index) => {
      const start = offset;
      offset += value / total * 100;
      return `${this.palette[index % this.palette.length]} ${start}% ${offset}%`;
    });
    return `conic-gradient(${stops.join(',')})`;
  }
  roleLabel(): string { return this.role().replace('ROLE_', '').replace('_', ' '); }
  exportKey(message: AnalyticsMessage): string {
    return message.question ?? message.response?.question ?? '';
  }
  clear(): void {
    this.api.clearHistory('BI').subscribe({
      next: () => this.messages.set([]),
      error: (error) => this.feedback.error(error),
    });
  }
}
