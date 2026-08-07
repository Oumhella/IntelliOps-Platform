import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Type } from '@angular/core';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/api';
import { AssistantComponent } from './assistant.component';
import { BillingComponent } from './billing.component';
import { BusinessHomeComponent } from './business-home.component';
import { BusinessShellComponent } from './business-shell.component';
import { DeliveriesComponent } from './deliveries.component';
import { LeadsComponent } from './leads.component';
import { IntegrationsComponent } from './integrations.component';
import { NotificationsComponent } from './notifications.component';
import { OrdersComponent } from './orders.component';
import { ProfileComponent } from './profile.component';
import { StockComponent } from './stock.component';
import { SubscriptionsComponent } from './subscriptions.component';
import { TeamComponent } from './team.component';

describe('business views', () => {
  const components: Type<unknown>[] = [
    BusinessShellComponent,
    BusinessHomeComponent,
    LeadsComponent,
    IntegrationsComponent,
    OrdersComponent,
    StockComponent,
    DeliveriesComponent,
    BillingComponent,
    SubscriptionsComponent,
    TeamComponent,
    NotificationsComponent,
    AssistantComponent,
    ProfileComponent,
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: components,
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_BASE_URL, useValue: '' },
      ],
    }).compileComponents();
  });

  for (const component of components) {
    it(`creates ${component.name}`, () => {
      expect(TestBed.createComponent(component).componentInstance).toBeTruthy();
    });
  }
});
