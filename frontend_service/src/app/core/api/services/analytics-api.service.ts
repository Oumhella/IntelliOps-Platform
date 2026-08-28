import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import { AnalyticsReport,AnalyticsReportPeriod,AnalyticsResponse,AnalyticsSuggestions,ConversationMessage,ConversationSurface } from '../models/analytics.models';
import { I18nService } from '../../i18n/i18n.service';

@Injectable({ providedIn: 'root' })
export class AnalyticsApiService {
  private readonly http = inject(HttpClient);
  private readonly i18n = inject(I18nService);
  private readonly url = buildApiUrl(inject(API_BASE_URL), '/api/v1/analytics');
  suggestions(): Observable<AnalyticsSuggestions> { return this.http.get<AnalyticsSuggestions>(`${this.url}/suggestions`); }
  ask(question: string): Observable<AnalyticsResponse> { return this.http.post<AnalyticsResponse>(`${this.url}/ask`, { question, locale: this.i18n.language() }); }
  history(surface:ConversationSurface):Observable<ConversationMessage[]>{return this.http.get<ConversationMessage[]>(`${this.url}/conversations/${surface}`);}
  store(surface:ConversationSurface,role:'user'|'assistant',content:string,payload?:Record<string,unknown>):Observable<ConversationMessage>{return this.http.post<ConversationMessage>(`${this.url}/conversations`,{surface,role,content,payload});}
  clearHistory(surface:ConversationSurface):Observable<void>{return this.http.delete<void>(`${this.url}/conversations/${surface}`);}
  exportCsv(question:string):Observable<Blob>{return this.http.post(`${this.url}/reports/csv`,{question,locale:this.i18n.language()},{responseType:'blob'});}
  reports(period?:AnalyticsReportPeriod):Observable<AnalyticsReport[]>{
    const query = new URLSearchParams({locale:this.i18n.language()});
    if(period) query.set('period_type',period);
    return this.http.get<AnalyticsReport[]>(`${this.url}/reports?${query}`);
  }
  generateReport(period_type:AnalyticsReportPeriod):Observable<AnalyticsReport>{
    return this.http.post<AnalyticsReport>(`${this.url}/reports/generate`,{
      period_type,locale:this.i18n.language(),
    });
  }
  downloadReport(id:string):Observable<Blob>{
    return this.http.get(`${this.url}/reports/${encodeURIComponent(id)}/download`,{
      responseType:'blob',
    });
  }
}
