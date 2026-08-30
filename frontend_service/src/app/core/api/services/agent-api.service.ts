import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import { AgentActionExecutionResponse, AgentChatRequest, AgentReplyResponse, AgentStatusResponse } from '../models';
import { I18nService } from '../../i18n/i18n.service';

@Injectable({ providedIn: 'root' })
export class AgentApiService {
  private readonly i18n = inject(I18nService);
  private readonly http = inject(HttpClient);
  private readonly url = buildApiUrl(inject(API_BASE_URL), '/api/v1/agent');

  getStatus(): Observable<AgentStatusResponse> {
    return this.http.get<AgentStatusResponse>(`${this.url}/status`);
  }

  chat(message: string): Observable<AgentReplyResponse> {
    const request: AgentChatRequest = { message, locale: this.i18n.language() };
    return this.http.post<AgentReplyResponse>(`${this.url}/chat`, request);
  }

  confirmAction(token: string, confirmation: string, reason?: string): Observable<AgentActionExecutionResponse> {
    return this.http.post<AgentActionExecutionResponse>(`${this.url}/actions/${encodeURIComponent(token)}/confirm`, { confirmation, reason });
  }

  rejectAction(token: string): Observable<AgentActionExecutionResponse> {
    return this.http.post<AgentActionExecutionResponse>(`${this.url}/actions/${encodeURIComponent(token)}/reject`, null);
  }
}
