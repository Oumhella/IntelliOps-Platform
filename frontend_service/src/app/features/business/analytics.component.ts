import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AnalyticsApiService, AnalyticsResponse } from '../../core/api';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';

interface AnalyticsMessage { role: 'user' | 'assistant'; text: string; response?: AnalyticsResponse; }

@Component({ selector: 'app-analytics', imports: [FormsModule], templateUrl: './analytics.component.html', styleUrl: './analytics.component.scss' })
export class AnalyticsComponent implements OnInit {
  private readonly api = inject(AnalyticsApiService);
  readonly feedback = inject(UiFeedbackService);
  readonly suggestions = signal<string[]>([]);
  readonly messages = signal<AnalyticsMessage[]>([]);
  question = '';
  sending = false;
  ngOnInit(): void { this.api.suggestions().subscribe({ next: value => this.suggestions.set(value.suggestions), error: error => this.feedback.error(error, 'Analytics suggestions are unavailable.') }); }
  ask(question = this.question): void {
    const value = question.trim(); if (!value || this.sending) return;
    this.messages.update(items => [...items, { role: 'user', text: value }]); this.question = ''; this.sending = true;
    this.api.ask(value).subscribe({ next: response => { this.sending = false; this.messages.update(items => [...items, { role: 'assistant', text: response.answer, response }]); }, error: error => { this.sending = false; this.feedback.error(error, 'The business question could not be answered.'); } });
  }
  cell(row: Record<string, unknown>, name: string): unknown { return row[name]; }
}
