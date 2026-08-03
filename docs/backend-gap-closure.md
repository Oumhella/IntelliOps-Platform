# Backend gap closure before screen development

This change completes the read/management contracts needed by the frontend before screen work begins. The Spring controllers and Angular API services now have matching coverage: 73 controller operations and 73 typed frontend HTTP calls.

## Added contracts

- Stores: tenant store list, detail, and update. Store creation gets `adminId` and `enterpriseId` from the authenticated context rather than trusting browser input.
- Products: tenant catalog isolation and safe deletion. Deletion returns HTTP 409 while inventory still references the product.
- CRM: paginated/filterable lead and order lists.
- Subscriptions: paginated tenant subscription list; plan update and soft delete. A deleted plan uses the existing `SUPPRIME` state so historical subscriptions keep their plan reference.
- Payments: paginated/filterable transaction history, transaction detail, invoice history, and invoice detail. Responses now include the idempotency key and refunded amount.
- Deliveries: paginated/filterable history and delivery detail.
- Notifications: paginated/filterable history and notification detail.

List endpoints use `PageResponse<T>` from `common_lib`. Its stable JSON shape is:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

Page numbers are zero-based and requested sizes are clamped to 1–100.

## Tenant isolation

Business root records now persist the `enterpriseId` injected after gateway JWT validation:

- `Boutique` and `Produit`; inventory is scoped through its store.
- `Lead`; orders are scoped through their lead.
- `Abonnement`.
- `TransactionPaiement`; invoices are scoped through their transaction.
- `Livraison`.
- `Notification`.

Services use tenant-aware repository methods for both list and single-record lookups. Knowing another tenant's numeric ID therefore does not make that record accessible. User-by-ID lookup was also changed to require the caller's enterprise.

Kafka notification events now carry `enterpriseId`. Request-driven producers take it from `TenantContext`; the subscription expiration scheduler passes the subscription's stored enterprise explicitly. The notification consumer persists this ownership value before making a record visible through history endpoints.

## Authentication cleanup

The obsolete `/api/v1/users/setup-admin` operation was removed from:

- `UserController`;
- the user-service public security rules;
- the gateway JWT bypass;
- the MCP protected-operation list and documentation;
- the Angular authentication service and API documentation.

Normal `/register` remains the only public administrator registration path. Staff mutation annotations were corrected from `hasRole('ROLE_ADMIN')` to `hasRole('ADMIN')`, matching Spring Security's automatic `ROLE_` prefix behavior.

## Existing database migration

The services currently use `spring.jpa.hibernate.ddl-auto=update`. A fresh development database will receive the new `enterprise_id` columns automatically. An existing database with business records needs an explicit operational choice before restart:

1. Back up and re-seed development data; or
2. Add nullable columns, backfill every row with the correct tenant, then enforce `NOT NULL`.

Do not assign one guessed enterprise to all legacy rows. Tenant ownership cannot be inferred safely across the separate microservice databases.

## Verification

- Full Maven reactor compilation succeeds.
- Focused lead/order unit tests succeed.
- Angular production build succeeds.
- Angular transport/interceptor suite succeeds: 10 tests.
- The repository's generic `@SpringBootTest` suite still requires the config server and its external infrastructure. Running the entire Maven test reactor without that stack stops at the pre-existing `user_service` context test because `localhost:8888` is unavailable; this is not a code-compilation failure.

The corresponding Angular usage and endpoint tables are maintained in `frontend_service/docs/backend-api-layer.md`.
