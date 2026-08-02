import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthSessionService } from '../auth/auth-session.service';
import { ApiError } from './api-error';
import { apiInterceptor } from './api.interceptor';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../api/api.config';

describe('apiInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let session: jasmine.SpyObj<AuthSessionService>;

  beforeEach(() => {
    session = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', ['getToken', 'clear']);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '' },
        { provide: AuthSessionService, useValue: session },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('does not leak the ERP token to a third-party URL', () => {
    session.getToken.and.returnValue('test-token');

    http.get('https://cdn.example/assets.json').subscribe();

    const request = httpTesting.expectOne('https://cdn.example/assets.json');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    expect(session.getToken).not.toHaveBeenCalled();
    request.flush({});
  });

  afterEach(() => httpTesting.verify());

  it('adds the bearer token when a session exists', () => {
    session.getToken.and.returnValue('test-token');

    http.get('/api/v1/users/me').subscribe();

    const request = httpTesting.expectOne('/api/v1/users/me');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush({});
  });

  it('normalizes Spring ProblemDetail errors and clears an unauthorized session', () => {
    session.getToken.and.returnValue('expired-token');
    let receivedError: unknown;

    http.get('/api/v1/users/me').subscribe({ error: (error) => receivedError = error });
    httpTesting.expectOne('/api/v1/users/me').flush(
      {
        title: 'Validation Error',
        detail: 'Validation failed for one or more fields.',
        errors: { email: 'Email format is invalid' },
      },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(session.clear).toHaveBeenCalled();
    expect(receivedError).toEqual(jasmine.any(ApiError));
    expect((receivedError as ApiError).fieldErrors['email']).toBe('Email format is invalid');
  });
});
