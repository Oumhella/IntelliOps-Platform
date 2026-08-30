export interface AnalyticsColumn { readonly name: string; readonly type: string; }
export interface AnalyticsResult { readonly columns: readonly AnalyticsColumn[]; readonly rows: readonly Record<string, unknown>[]; }
export interface AnalyticsVisualization { readonly type: 'none' | 'single_value' | 'table' | 'bar' | 'line' | 'donut'; readonly title?: string; readonly x?: string; readonly y?: string; }
export interface AnalyticsMetadata { readonly metric: string; readonly data_freshness?: string; readonly truncated: boolean; readonly assumptions: readonly string[]; }
export interface AnalyticsResponse { readonly question: string; readonly answer: string; readonly result: AnalyticsResult; readonly visualization: AnalyticsVisualization; readonly metadata: AnalyticsMetadata; }
export type ConversationSurface='ASSISTANT'|'BI';
export interface ConversationMessage {readonly id:number;readonly surface:ConversationSurface;readonly role:'user'|'assistant';readonly content:string;readonly payload?:Record<string,unknown>;readonly created_at:string;}
export interface AnalyticsSuggestions {readonly role:string;readonly suggestions:readonly string[];}
export type AnalyticsReportPeriod = 'WEEKLY' | 'MONTHLY';
export interface AnalyticsReportSummary {
  readonly orders: number;
  readonly paid_revenue: number;
  readonly average_order_value: number;
  readonly delivered: number;
  readonly low_stock_items: number;
  readonly recommendations: readonly string[];
}
export interface AnalyticsReport {
  readonly id: string;
  readonly audience_role: string;
  readonly period_type: AnalyticsReportPeriod;
  readonly period_start: string;
  readonly period_end: string;
  readonly locale: 'en' | 'fr' | 'ar';
  readonly generated_at: string;
  readonly file_name: string;
  readonly summary: AnalyticsReportSummary;
}
