import { EnvironmentProviders, InjectionToken, makeEnvironmentProviders } from '@angular/core';

/** Base URL of the API gateway. An empty value keeps requests same-origin. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL');

export function provideApiConfig(baseUrl: string): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: API_BASE_URL, useValue: baseUrl.replace(/\/$/, '') },
  ]);
}

export function buildApiUrl(baseUrl: string, path: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${baseUrl}${normalizedPath}`;
}
