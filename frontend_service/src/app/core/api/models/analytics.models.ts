export interface AnalyticsColumn { readonly name: string; readonly type: string; }
export interface AnalyticsResult { readonly columns: readonly AnalyticsColumn[]; readonly rows: readonly Record<string, unknown>[]; }
export interface AnalyticsVisualization { readonly type: 'none' | 'single_value' | 'table' | 'bar' | 'line'; readonly x?: string; readonly y?: string; }
export interface AnalyticsMetadata { readonly metric: string; readonly data_freshness?: string; readonly truncated: boolean; readonly assumptions: readonly string[]; }
export interface AnalyticsResponse { readonly question: string; readonly answer: string; readonly result: AnalyticsResult; readonly visualization: AnalyticsVisualization; readonly metadata: AnalyticsMetadata; }
export type ConversationSurface='ASSISTANT'|'BI';
export interface ConversationMessage {readonly id:number;readonly surface:ConversationSurface;readonly role:'user'|'assistant';readonly content:string;readonly payload?:Record<string,unknown>;readonly created_at:string;}
