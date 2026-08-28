import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  NOTIFICATION_STATUSES,
  NOTIFICATION_TYPES,
  NotificationResponse,
  NotificationStatus,
  NotificationType,
  NotificationsApiService,
  PageResponse,
} from '../../core/api';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';
import { DomainLabelPipe } from '../../core/i18n/domain-label.pipe';
import { LocaleDatePipe } from '../../core/i18n/locale-date.pipe';

@Component({
  selector: 'app-notifications',
  imports: [FormsModule, DatePipe, DomainLabelPipe, LocaleDatePipe],
  templateUrl: './notifications.component.html',
  styleUrl: './business-view.scss',
})
export class NotificationsComponent implements OnInit {
  private readonly api = inject(NotificationsApiService);
  readonly feedback = inject(UiFeedbackService);
  readonly statuses = NOTIFICATION_STATUSES;
  readonly types = NOTIFICATION_TYPES;
  readonly sendTypes: readonly NotificationType[] = ['EMAIL'];
  readonly page = signal<PageResponse<NotificationResponse> | null>(null);
  readonly selected = signal<NotificationResponse | null>(null);
  statusFilter: NotificationStatus | '' = '';
  typeFilter: NotificationType | '' = '';
  panel: 'send' | 'detail' | null = null;
  busy = false;
  lookupId: number | null = null;
  form = { type: 'EMAIL' as NotificationType, recipientContact: '', subject: '', contenu: '' };

  ngOnInit(): void { this.load(); }
  load(page = 0): void { this.api.search(page, 20, this.statusFilter || undefined, this.typeFilter || undefined).subscribe({ next: (value) => this.page.set(value), error: (error) => this.feedback.error(error) }); }
  open(notification: NotificationResponse): void { this.selected.set(notification); this.panel = 'detail'; this.api.getById(notification.idNotification).subscribe({ next: (value) => this.selected.set(value), error: (error) => this.feedback.error(error) }); }
  lookup(): void { if (!this.lookupId) return; this.api.getById(this.lookupId).subscribe({ next: (value) => { this.selected.set(value); this.panel = 'detail'; }, error: (error) => this.feedback.error(error) }); }
  close(): void { this.panel = null; this.selected.set(null); }
  send(): void {
    if (!this.form.recipientContact || !this.form.contenu) return;
    this.busy = true;
    this.api.sendDirect({ type: this.form.type, recipientContact: this.form.recipientContact, subject: this.form.subject || undefined, contenu: this.form.contenu }).subscribe({
      next: () => { this.busy = false; this.feedback.success('Email sent through the configured provider.'); this.close(); this.load(); },
      error: (error) => { this.busy = false; this.feedback.error(error); },
    });
  }
  label(value: string): string { return value.replaceAll('_', ' ').toLowerCase().replace(/^./, (character) => character.toUpperCase()); }
  tone(value: NotificationStatus): string { return value === 'DELIVERED' || value === 'SENT' ? 'success' : value === 'FAILED' ? 'critical' : 'warning'; }
}
