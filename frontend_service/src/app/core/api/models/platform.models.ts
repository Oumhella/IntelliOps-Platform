export interface PlatformTotals {
  readonly enterprises: number;
  readonly users: number;
  readonly activeUsers: number;
  readonly onlineServices: number;
  readonly totalServices: number;
}

export interface PlatformTenantSummary {
  readonly enterpriseId: number;
  readonly companyName: string | null;
  readonly activityType: string | null;
  readonly userCount: number;
  readonly active: boolean;
  readonly createdAt: string;
}

export type PlatformServiceStatus = 'ONLINE' | 'OFFLINE';

export interface PlatformServiceSummary {
  readonly serviceId: string;
  readonly name: string;
  readonly status: PlatformServiceStatus;
  readonly instanceCount: number;
}

export interface PlatformOverview {
  readonly generatedAt: string;
  readonly totals: PlatformTotals;
  readonly tenants: readonly PlatformTenantSummary[];
  readonly services: readonly PlatformServiceSummary[];
}

export interface PlatformEvent {
  readonly type: string;
  readonly subject: string;
  readonly detail: string;
  readonly observedAt: string;
  readonly severity: 'INFO' | 'WARNING';
}

export interface PlatformSettings {
  readonly environment: string;
  readonly authentication: string;
  readonly serviceDiscovery: string;
  readonly expectedServices: readonly string[];
  readonly generatedAt: string;
}
