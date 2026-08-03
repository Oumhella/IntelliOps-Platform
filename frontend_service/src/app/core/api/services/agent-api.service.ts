import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import { AgentChatRequest, AgentReplyResponse, AgentStatusResponse } from '../models';

@Injectable({ providedIn: 'root' })
export class AgentApiService {
  private readonly http = inject(HttpClient);
  private readonly url = buildApiUrl(inject(API_BASE_URL), '/api/v1/agent');

  getStatus(): Observable<AgentStatusResponse> {
    return this.http.get<AgentStatusResponse>(`${this.url}/status`);
  }

  chat(message: string): Observable<AgentReplyResponse> {
    const request: AgentChatRequest = { message };
    return this.http.post<AgentReplyResponse>(`${this.url}/chat`, request);
  }
}
