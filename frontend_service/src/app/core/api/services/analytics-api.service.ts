import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import { AnalyticsResponse,ConversationMessage,ConversationSurface } from '../models/analytics.models';

@Injectable({ providedIn: 'root' })
export class AnalyticsApiService {
  private readonly http = inject(HttpClient);
  private readonly url = buildApiUrl(inject(API_BASE_URL), '/api/v1/analytics');
  suggestions(): Observable<{ suggestions: string[] }> { return this.http.get<{ suggestions: string[] }>(`${this.url}/suggestions`); }
  ask(question: string): Observable<AnalyticsResponse> { return this.http.post<AnalyticsResponse>(`${this.url}/ask`, { question }); }
  history(surface:ConversationSurface):Observable<ConversationMessage[]>{return this.http.get<ConversationMessage[]>(`${this.url}/conversations/${surface}`);}
  store(surface:ConversationSurface,role:'user'|'assistant',content:string,payload?:Record<string,unknown>):Observable<ConversationMessage>{return this.http.post<ConversationMessage>(`${this.url}/conversations`,{surface,role,content,payload});}
  clearHistory(surface:ConversationSurface):Observable<void>{return this.http.delete<void>(`${this.url}/conversations/${surface}`);}
}
