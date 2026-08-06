import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import {
  InvoiceResponse,
  InitiatePaymentRequest,
  PageResponse,
  PaymentContext,
  PaymentStatus,
  PaymentTransactionResponse,
  PreparePaymentRequest,
  PaymentPreparationResponse,
  RefundRequest,
} from '../models';

@Injectable({ providedIn: 'root' })
export class PaymentsApiService {
  private readonly http = inject(HttpClient);
  private readonly url = buildApiUrl(inject(API_BASE_URL), '/api/v1/payments');

  searchTransactions(
    page = 0,
    size = 20,
    status?: PaymentStatus,
    context?: PaymentContext,
  ): Observable<PageResponse<PaymentTransactionResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status !== undefined) params = params.set('statut', status);
    if (context !== undefined) params = params.set('contexte', context);
    return this.http.get<PageResponse<PaymentTransactionResponse>>(this.url, { params });
  }

  getTransaction(id: number): Observable<PaymentTransactionResponse> {
    return this.http.get<PaymentTransactionResponse>(`${this.url}/${id}`);
  }

  initiate(request: InitiatePaymentRequest): Observable<PaymentTransactionResponse> {
    return this.http.post<PaymentTransactionResponse>(`${this.url}/initier`, request);
  }

  prepare(request: PreparePaymentRequest): Observable<PaymentPreparationResponse> {
    return this.http.post<PaymentPreparationResponse>(`${this.url}/prepare`, request);
  }

  finalize(transactionId: number): Observable<PaymentTransactionResponse> {
    return this.http.post<PaymentTransactionResponse>(`${this.url}/${transactionId}/finalize`, null);
  }

  refund(transactionId: number, request: RefundRequest): Observable<PaymentTransactionResponse> {
    return this.http.post<PaymentTransactionResponse>(`${this.url}/${transactionId}/rembourser`, request);
  }

  cancel(transactionId: number): Observable<PaymentTransactionResponse> {
    return this.http.post<PaymentTransactionResponse>(`${this.url}/${transactionId}/annuler`, null);
  }

  getInvoiceDownloadUrl(invoiceId: number): Observable<string> {
    // The backend returns text/plain, so Angular must not try to parse this as JSON.
    return this.http.get(`${this.url}/factures/${invoiceId}/download-url`, { responseType: 'text' });
  }

  getInvoices(page = 0, size = 20): Observable<PageResponse<InvoiceResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<InvoiceResponse>>(`${this.url}/factures`, { params });
  }

  getInvoice(invoiceId: number): Observable<InvoiceResponse> {
    return this.http.get<InvoiceResponse>(`${this.url}/factures/${invoiceId}`);
  }
}
