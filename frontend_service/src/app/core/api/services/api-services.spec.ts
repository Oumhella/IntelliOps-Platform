import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../api.config';
import { PaymentsApiService } from './payments-api.service';
import { StockApiService } from './stock-api.service';
import { SubscriptionsApiService } from './subscriptions-api.service';
import { CrmApiService } from './crm-api.service';
import { PlatformApiService } from './platform-api.service';

describe('API services transport details', () => {
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://gateway:8080' },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('sends stock reservation values as query parameters', () => {
    TestBed.inject(StockApiService).reserveStock(4, 9, 3).subscribe();

    const request = httpTesting.expectOne(
      (candidate) => candidate.url === 'http://gateway:8080/api/v1/inventaires/boutiques/4/produits/9/reserver',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('quantite')).toBe('3');
    request.flush({});
  });

  it('omits the optional plan status filter when it is not supplied', () => {
    TestBed.inject(SubscriptionsApiService).getPlans().subscribe();

    const request = httpTesting.expectOne('http://gateway:8080/api/v1/plans');
    expect(request.request.params.keys()).toEqual([]);
    request.flush([]);
  });

  it('requests invoice download URLs as plain text', () => {
    let downloadUrl = '';
    TestBed.inject(PaymentsApiService).getInvoiceDownloadUrl(12).subscribe((value) => downloadUrl = value);

    const request = httpTesting.expectOne('http://gateway:8080/api/v1/payments/factures/12/download-url');
    expect(request.request.responseType).toBe('text');
    request.flush('https://object-store.example/invoice.pdf');
    expect(downloadUrl).toContain('invoice.pdf');
  });

  it('sends pagination and optional CRM filters using backend parameter names', () => {
    TestBed.inject(CrmApiService).searchLeads(2, 25, 'IN_PROGRESS', 44).subscribe();

    const request = httpTesting.expectOne(
      (candidate) => candidate.url === 'http://gateway:8080/api/v1/leads',
    );
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('25');
    expect(request.request.params.get('statut')).toBe('IN_PROGRESS');
    expect(request.request.params.get('agentId')).toBe('44');
    request.flush({ content: [] });
  });

  it('loads the secured platform overview from the gateway', () => {
    TestBed.inject(PlatformApiService).getOverview().subscribe();

    const request = httpTesting.expectOne('http://gateway:8080/api/v1/platform/overview');
    expect(request.request.method).toBe('GET');
    request.flush({
      generatedAt: '2026-08-02T12:00:00Z',
      totals: { enterprises: 0, users: 0, activeUsers: 0, onlineServices: 0, totalServices: 9 },
      tenants: [],
      services: [],
    });
  });
});
