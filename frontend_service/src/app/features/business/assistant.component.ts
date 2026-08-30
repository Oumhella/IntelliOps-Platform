import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AgentActionPreview, AgentApiService, AgentReplyResponse, AgentStatusResponse, AnalyticsApiService, UserRole } from '../../core/api';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

type ActionState = 'pending' | 'executed' | 'rejected' | 'failed' | 'historical';
interface ActionView extends AgentActionPreview { state: ActionState; feedback?: string; confirmationText?: string; reason?: string }
interface ChatMessage { role: 'user' | 'assistant'; text: string; safety?: string; action?: ActionView }

@Component({ selector: 'app-assistant', imports: [FormsModule, DatePipe, TranslatePipe], templateUrl: './assistant.component.html', styleUrls: ['./assistant.component.scss', './assistant-risk.component.scss'] })
export class AssistantComponent implements OnInit {
  private readonly api = inject(AgentApiService);
  private readonly historyApi = inject(AnalyticsApiService);
  private readonly session = inject(AuthSessionService);
  readonly feedback = inject(UiFeedbackService);
  readonly status = signal<AgentStatusResponse | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  message = '';
  sending = false;
  actionBusyToken = '';

  get role(): UserRole | undefined { return this.session.currentUser()?.role; }
  get suggestions(): readonly string[] {
    switch (this.role) {
      case 'ROLE_CSM': return [
        'Show the leads assigned to me that need follow-up',
        'Prepare converting qualified lead 1 into an order using location 1 and product 1 quantity 1',
        'Show my pending orders and the next customer action',
      ];
      case 'ROLE_LOGISTIC': return [
        'Show products with low available stock',
        'Prepare adding 10 units of product 1 at location 1 as REASSORT',
        'Show confirmed orders waiting for preparation',
      ];
      case 'ROLE_LIVREUR': return [
        'Show my assigned deliveries and their next action',
        'Show the customer and collection details for my active delivery',
        'Which delivery should I handle next?',
      ];
      default: return [
        'Summarize today’s operational exceptions',
        'Show current revenue and orders by status',
        'Prepare adding 10 units of product 1 at location 1 as REASSORT',
      ];
    }
  }

  ngOnInit(): void {
    this.api.getStatus().subscribe({ next: (value) => this.status.set(value), error: (error) => this.feedback.error(error, 'Agent status is unavailable.') });
    this.historyApi.history('ASSISTANT').subscribe({
      next: (items) => this.messages.set(items.map((item) => {
        const stored = item.payload?.['action'] as unknown as AgentActionPreview | undefined;
        return { role: item.role, text: item.content, safety: item.payload?.['safety'] as string | undefined,
          action: stored ? { ...stored, state: 'historical', feedback: 'Archived preview. Prepare it again if no later execution result is shown.' } : undefined };
      })), error: () => undefined,
    });
  }

  useSuggestion(value: string): void { this.message = value; }
  send(): void {
    const text = this.message.trim();
    if (!text || this.sending) return;
    this.messages.update((items) => [...items, { role: 'user', text }]);
    this.historyApi.store('ASSISTANT', 'user', text).subscribe({ error: () => undefined });
    this.message = '';
    this.sending = true;
    this.api.chat(text).subscribe({
      next: (reply: AgentReplyResponse) => {
        this.sending = false;
        const action: ActionView | undefined = reply.action ? { ...reply.action, state: 'pending', confirmationText: '', reason: '' } : undefined;
        this.messages.update((items) => [...items, { role: 'assistant', text: reply.answer, safety: reply.safety, action }]);
        const historicalAction = reply.action ? {
          operation: reply.action.operation, summary: reply.action.summary, expiresAt: reply.action.expiresAt,
          requiresExplicitConfirmation: true, riskLevel: reply.action.riskLevel,
          requiresReason: reply.action.requiresReason, nextStep: 'This approval was available only in the live session.',
        } : undefined;
        this.historyApi.store('ASSISTANT', 'assistant', reply.answer, { safety: reply.safety, ...(historicalAction ? { action: historicalAction } : {}) }).subscribe({ error: () => undefined });
      }, error: (error) => { this.sending = false; this.feedback.error(error); },
    });
  }

  confirm(action: ActionView): void {
    if (!this.canConfirm(action)) return;
    this.actionBusyToken = action.approvalToken;
    const confirmation = action.riskLevel === 'HIGH' ? action.confirmationText!.trim() : 'CONFIRM';
    this.api.confirmAction(action.approvalToken, confirmation, action.reason?.trim()).subscribe({
      next: (result) => { this.actionBusyToken = ''; this.updateAction(action.approvalToken, 'executed', result.message); this.appendExecutionMessage(result.message); this.feedback.success(result.message); },
      error: (error) => { this.actionBusyToken = ''; this.updateAction(action.approvalToken, 'failed', 'Execution failed. No retry was performed automatically.'); this.feedback.error(error, 'The action could not be completed.'); },
    });
  }

  reject(action: ActionView): void {
    if (action.state !== 'pending') return;
    this.actionBusyToken = action.approvalToken;
    this.api.rejectAction(action.approvalToken).subscribe({
      next: (result) => { this.actionBusyToken = ''; this.updateAction(action.approvalToken, 'rejected', result.message); this.feedback.success(result.message); },
      error: (error) => { this.actionBusyToken = ''; this.feedback.error(error); },
    });
  }

  clear(): void { this.historyApi.clearHistory('ASSISTANT').subscribe({ next: () => this.messages.set([]), error: (error) => this.feedback.error(error) }); }
  isExpired(action: ActionView): boolean { return new Date(action.expiresAt).getTime() <= Date.now(); }
  canConfirm(action: ActionView): boolean {
    if (action.state !== 'pending' || this.isExpired(action) || this.actionBusyToken === action.approvalToken) return false;
    if (action.riskLevel !== 'HIGH') return true;
    return action.confirmationText?.trim() === 'CONFIRM HIGH RISK' && (action.reason?.trim().length ?? 0) >= 10;
  }
  operationLabel(operation: string): string { return operation.replaceAll('_', ' ').toLowerCase().replace(/^./, (value) => value.toUpperCase()); }
  format(text: string): string {
    return text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>').replace(/^[-*] (.+)$/gm, '<span class="message-bullet">• $1</span>').replaceAll('\n', '<br>');
  }

  private updateAction(token: string, state: ActionState, feedback: string): void {
    this.messages.update((items) => items.map((item) => item.action?.approvalToken === token ? { ...item, action: { ...item.action, state, feedback } } : item));
  }
  private appendExecutionMessage(message: string): void {
    this.messages.update((items) => [...items, { role: 'assistant', text: message, safety: 'Executed after your explicit approval.' }]);
    this.historyApi.store('ASSISTANT', 'assistant', message, { safety: 'Executed after explicit user approval.' }).subscribe({ error: () => undefined });
  }
}
