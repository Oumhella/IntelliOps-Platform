import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../api.config';
import { PaymentsApiService } from './payments-api.service';
import { StockApiService } from './stock-api.service';
import { SubscriptionsApiService } from './subscriptions-api.service';
import { CrmApiService } from './crm-api.service';
import { PlatformApiService } from './platform-api.service';
import { DeliveriesApiService } from './deliveries-api.service';
import { UsersApiService } from './users-api.service';
import { IntegrationsApiService } from './integrations-api.service';

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

  it('omits the optional plan status filter when it is not supplied', () => {
    TestBed.inject(SubscriptionsApiService).getPlans().subscribe();

    const request = httpTesting.expectOne('http://gateway:8080/api/v1/plans');
    expect(request.request.params.keys()).toEqual([]);
    request.flush([]);
  });

  it('prepares an authoritative Stripe checkout without accepting card data', () => {
    TestBed.inject(SubscriptionsApiService).prepareCheckout({
      planId: 3,
      idempotencyKey: 'checkout-abc',
    }).subscribe();

    const request = httpTesting.expectOne('http://gateway:8080/api/v1/abonnements/checkout/prepare');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      planId: 3,
      idempotencyKey: 'checkout-abc',
    });
    expect(request.request.body.paiementId).toBeUndefined();
    expect(request.request.body.paymentMethodId).toBeUndefined();
    request.flush({});
  });

  it('completes checkout using only the server payment record', () => {
    TestBed.inject(SubscriptionsApiService).completeCheckout({ paymentId: 18 }).subscribe();

    const request = httpTesting.expectOne('http://gateway:8080/api/v1/abonnements/checkout/complete');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ paymentId: 18 });
    request.flush({});
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

  it('loads active tenant couriers for delivery assignment', () => {
    TestBed.inject(UsersApiService).getActiveCouriers().subscribe();

    const request = httpTesting.expectOne('http://gateway:8080/api/v1/users/staff/couriers');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('sends an internal delivery assignment using livreurId', () => {
    TestBed.inject(DeliveriesApiService).ship({
      referenceCommandeId: 7,
      typeTransporteur: 'LIVREUR_INTERNE',
      livreurId: 18,
    }).subscribe();

    const request = httpTesting.expectOne('http://gateway:8080/api/v1/livraisons/expedier');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.livreurId).toBe(18);
    expect(request.request.body.endpointApiUrl).toBeUndefined();
    request.flush({});
  });

  it('starts Shopify authorization without sending provider credentials', () => {
    TestBed.inject(IntegrationsApiService).connectShopify({
      displayName: 'Main store',
      store: 'merchant.myshopify.com',
      stockLocationId: 4,
    }).subscribe();

    const request = httpTesting.expectOne('http://gateway:8080/api/v1/integrations/shopify/connect');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      displayName: 'Main store',
      store: 'merchant.myshopify.com',
      stockLocationId: 4,
    });
    expect(request.request.body.accessToken).toBeUndefined();
    expect(request.request.body.consumerSecret).toBeUndefined();
    request.flush({ authorizationUrl: 'https://merchant.myshopify.com/admin/oauth/authorize', expiresAt: '2026-08-07T16:00:00Z' });
  });

  it('creates an explicit external-to-internal product mapping', () => {
    TestBed.inject(IntegrationsApiService).createMapping(9, {
      externalProductId: '81',
      externalVariantId: '93',
      externalSku: 'EXT-93',
      externalName: 'Blue shirt — Medium',
      internalProductId: 12,
    }).subscribe();

    const request = httpTesting.expectOne('http://gateway:8080/api/v1/integrations/connections/9/mappings');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.internalProductId).toBe(12);
    request.flush({});
  });
});
