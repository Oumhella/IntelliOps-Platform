# Backend API layer

This layer is the boundary between Angular components and the Spring microservices. Components should inject a domain service and call a typed method; they should not construct gateway URLs, authorization headers, or backend DTO shapes themselves.

## Request flow

```text
Component / store
      |
      v
Typed domain service ---- request/response interfaces
      |
      v
Angular HttpClient ---- API_BASE_URL + /api/v1/...
      |
      v
apiInterceptor ---- adds Authorization: Bearer <JWT>
      |
      v
API Gateway :8080 ---- validates JWT, injects trusted user/tenant headers
      |
      v
Spring microservice
```

The frontend always calls the gateway, never a microservice port directly. During `ng serve`, requests beginning with `/api` are forwarded by `proxy.conf.json` to `http://localhost:8080`. In production, Nginx performs the same forwarding. Both environment files therefore use an empty `apiUrl`, which produces same-origin URLs and avoids browser CORS problems.

## What each folder does

- `core/api/models`: exact TypeScript contracts matching Java request DTOs, response DTOs, and enum strings. Java `LocalDate` and `LocalDateTime` values deliberately remain ISO strings at the API boundary; a UI can format them without silently changing their timezone.
- `core/api/services`: one injectable service per domain. These classes own HTTP verbs, paths, path variables, query parameters, bodies, and response types.
- `core/api/api.config.ts`: provides the gateway base URL through dependency injection. Tests can replace it without changing application code.
- `core/auth/auth-session.service.ts`: stores the successful login response, exposes the signed-in user through signals, rejects expired/malformed JWTs, and removes the session on logout.
- `core/http/api.interceptor.ts`: attaches the JWT only to configured ERP `/api/` requests (so it cannot leak to a third-party URL), clears the local session on API HTTP 401, and converts backend errors to `ApiError`.
- `core/http/api-error.ts`: turns Spring `ProblemDetail`, field-validation errors, empty gateway errors, and network failures into one stable frontend error shape.
- `app.config.ts`: activates `HttpClient`, Fetch, the interceptor, and the configured gateway URL application-wide.

## Authentication behavior

`AuthApiService.login()` sends credentials to `/api/v1/users/login`. Its `tap` stores the returned `AuthResponse`. Every later request automatically receives the bearer token through the interceptor. Public login/register/setup calls simply have no bearer header before a session exists.

The backend currently returns the token in JSON and expects it in an `Authorization` header, so the frontend persists it in `localStorage`. This makes refreshes convenient but means XSS prevention is important. A more secure future contract would use a server-set `HttpOnly`, `Secure`, `SameSite` cookie; that change requires backend support.

The session helper is browser/SSR safe: it does not access `localStorage` while Angular is rendering on the server. JWT decoding is used only to inspect expiration. It does not replace the gateway's signature validation.

Example:

```ts
import { Component, inject } from '@angular/core';
import { AuthApiService } from './core/api';
import { ApiError } from './core/http/api-error';

export class LoginComponent {
  private readonly authApi = inject(AuthApiService);

  submit(email: string, password: string): void {
    this.authApi.login({ email, password }).subscribe({
      next: (session) => console.log(`Signed in as ${session.role}`),
      error: (error: ApiError) => console.error(error.message, error.fieldErrors),
    });
  }
}
```

## Endpoint coverage

### Users and authentication

| Frontend method | HTTP endpoint | Purpose |
|---|---|---|
| `AuthApiService.login` | `POST /api/v1/users/login` | Authenticate and save the returned session |
| `register` | `POST /api/v1/users/register` | Register an enterprise administrator |
| `logout` | Local only | Remove the saved token/session |
| `UsersApiService.getMyProfile` | `GET /api/v1/users/me` | Current user profile |
| `updateMyProfile` | `PUT /api/v1/users/me` | Partial profile update; omit unchanged fields |
| `changeMyPassword` | `PUT /api/v1/users/me/password` | Change password with confirmation |
| `createStaffMember` | `POST /api/v1/users/staff` | Create a CSM, logistics, or internal courier account |
| `getEnterpriseStaff` | `GET /api/v1/users/staff` | Tenant staff list |
| `getStaffMember` | `GET /api/v1/users/staff/{id}` | One tenant staff member |
| `setStaffStatus` | `PATCH /api/v1/users/staff/{id}/status` | Activate/deactivate staff |
| `deleteStaffMember` | `DELETE /api/v1/users/staff/{id}` | Permanently delete staff |
| `getUserById` | `GET /api/v1/users/{id}` | User lookup, also used between services |

`UserCreationRequest.role` accepts `CSM`, `LOGISTIC`, or `LIVREUR`; responses and JWTs use the corresponding `ROLE_*` value. Java's boolean getter `isActive()` is serialized by Jackson as `active`, which is why the frontend response uses that name.

### Plans and subscriptions

Plan reads provide the commercial catalogue. Plan writes are platform-global and require `ROLE_SUPER_ADMIN`; tenant subscription operations require `ROLE_ADMIN`. Paid checkout derives the authoritative amount from the selected plan and prepares a Stripe PaymentIntent. Stripe's Payment Element collects card/payment details directly. The completion call retrieves the PaymentIntent from Stripe, validates tenant/context/source/amount/currency, consumes it once, and only then activates or changes the entitlement. Direct paid activation without a completed payment returns HTTP 402.

| Frontend method | HTTP endpoint |
|---|---|
| `SubscriptionsApiService.createPlan` | `POST /api/v1/plans` |
| `getPlans` | `GET /api/v1/plans?statut=...` |
| `getPlanById` | `GET /api/v1/plans/{id}` |
| `updatePlan` | `PUT /api/v1/plans/{id}` |
| `deletePlan` | `DELETE /api/v1/plans/{id}` (soft delete to `SUPPRIME`) |
| `subscribe` | `POST /api/v1/abonnements` |
| `prepareCheckout` / `completeCheckout` | `POST /api/v1/abonnements/checkout/prepare` / `checkout/complete` |
| `searchSubscriptions` | `GET /api/v1/abonnements?page=...&size=...&statut=...` |
| `getSubscriptionById` | `GET /api/v1/abonnements/{id}` |
| `getUserSubscriptionHistory` | `GET /api/v1/abonnements/utilisateur/{userId}` |
| `suspend` | `POST /api/v1/abonnements/{id}/suspendre?motif=...` |
| `renew` | `POST /api/v1/abonnements/{id}/renouveler?paiementId=...` |
| `prepareRenewalCheckout` / `completeRenewalCheckout` | `POST /api/v1/abonnements/{id}/renew-checkout/prepare` / `renew-checkout/complete` |
| `upgrade` | `PUT /api/v1/abonnements/{id}/upgrade?nouveauPlanId=...&paiementId=...` |
| `prepareUpgradeCheckout` / `completeUpgradeCheckout` | `POST /api/v1/abonnements/{id}/upgrade-checkout/prepare` / `upgrade-checkout/complete` |
| `getRemainingDays` | `GET /api/v1/abonnements/{id}/duree-restante` |
| `canCreateOrder` | `GET /api/v1/abonnements/{id}/verifier-limite?commandesEffectuees=...` |
| `checkExpiration` | `POST /api/v1/abonnements/{id}/verifier-expiration` |

### CRM leads and orders

| Frontend method | HTTP endpoint |
|---|---|
| `CrmApiService.createLead` | `POST /api/v1/leads` |
| `searchLeads` | `GET /api/v1/leads?page=...&size=...&statut=...&agentId=...` |
| `getLeadById` | `GET /api/v1/leads/{id}` |
| `getLeadsByAgent` | `GET /api/v1/leads/agent/{agentId}` |
| `assignAgent` | `PUT /api/v1/leads/{id}/assigner?agentId=...` |
| `addInteraction` | `POST /api/v1/leads/{id}/interactions` |
| `convertToOrder` | `POST /api/v1/leads/{id}/convertir` |
| `getOrderById` | `GET /api/v1/commandes/{id}` |
| `searchOrders` | `GET /api/v1/commandes?page=...&size=...&statut=...` |
| `addProductToOrder` | `POST /api/v1/commandes/{id}/lignes?produitId=...&quantite=...&prixUnitaire=...` |
| `changeOrderStatus` | `PUT /api/v1/commandes/{id}/statut?nouveauStatut=...` |

The gateway injects the authenticated `userId` when a CSM creates a lead. `CreateLeadRequest` therefore does not allow a component to choose `agentId`.

### Stock, stores, products, and inventory

| Frontend method | HTTP endpoint |
|---|---|
| `StockApiService.createStore` | `POST /api/v1/boutiques` |
| `getStores` | `GET /api/v1/boutiques` |
| `getStoreById` | `GET /api/v1/boutiques/{id}` |
| `updateStore` | `PUT /api/v1/boutiques/{id}` |
| `testStoreConnection` | `POST /api/v1/boutiques/{id}/tester-connexion` |
| `synchronizeStoreProducts` | `POST /api/v1/boutiques/{id}/synchroniser` |
| `createProduct` | `POST /api/v1/produits` |
| `getProductById` | `GET /api/v1/produits/{id}` |
| `getProducts` | `GET /api/v1/produits` |
| `updateProduct` | `PUT /api/v1/produits/{id}` |
| `deleteProduct` | `DELETE /api/v1/produits/{id}` |
| `adjustStock` | `PATCH /api/v1/inventaires/boutiques/{storeId}/produits/{productId}/ajuster` |
| `reserveStock` | `POST /api/v1/inventaires/boutiques/{storeId}/produits/{productId}/reserver?quantite=...` |
| `getInventory` | `GET /api/v1/inventaires/boutiques/{storeId}/produits/{productId}` |
| `getStoreInventory` | `GET /api/v1/inventaires/boutiques/{storeId}` |
| `configureReplenishmentRule` | `PUT /api/v1/inventaires/{id}/regle-approvisionnement` |

Store ownership (`adminId`) and tenant ownership are now assigned from the authenticated gateway context. A browser request cannot choose either value. Product deletion returns HTTP 409 while the product is still referenced by inventory.

### Payments

| Frontend method | HTTP endpoint |
|---|---|
| `PaymentsApiService.searchTransactions` | `GET /api/v1/payments?page=...&size=...&statut=...&contexte=...` |
| `getTransaction` | `GET /api/v1/payments/{transactionId}` |
| `PaymentsApiService.initiate` | `POST /api/v1/payments/initier` |
| `prepare` | `POST /api/v1/payments/prepare` |
| `finalize` | `POST /api/v1/payments/{transactionId}/finalize` |
| `refund` | `POST /api/v1/payments/{transactionId}/rembourser` |
| `cancel` | `POST /api/v1/payments/{transactionId}/annuler` |
| `getInvoices` | `GET /api/v1/payments/factures?page=...&size=...` |
| `getInvoice` | `GET /api/v1/payments/factures/{invoiceId}` |
| `getInvoiceDownloadUrl` | `GET /api/v1/payments/factures/{invoiceId}/download-url` |

The invoice endpoint returns `text/plain`, not JSON. Its service method explicitly sets `responseType: 'text'`; otherwise Angular would report a parsing error even after a successful HTTP response. `idempotencyKey` should be a newly generated stable key for one logical payment attempt and reused only when retrying that same attempt. `/initier` is for cash-on-delivery; card requests are rejected there and must use prepare → Stripe Payment Element → finalize.

### Deliveries

| Frontend method | HTTP endpoint |
|---|---|
| `DeliveriesApiService.search` | `GET /api/v1/livraisons?page=...&size=...&statut=...&transporteur=...` |
| `getById` | `GET /api/v1/livraisons/{id}` |
| `DeliveriesApiService.ship` | `POST /api/v1/livraisons/expedier` |
| `getByTrackingNumber` | `GET /api/v1/livraisons/tracking/{trackingNumber}` |
| `getByOrderId` | `GET /api/v1/livraisons/commande/{orderId}` |
| `updateStatus` | `PATCH /api/v1/livraisons/{id}/statut` |
| `confirmReception` | `POST /api/v1/livraisons/{id}/confirmer-reception` |

The tracking value is URL-encoded before it is placed in the path. Carrier-specific request fields are optional at the transport level: company delivery uses `nomSociete`/`endpointApiUrl`, while internal delivery uses `externalLivreurId`.

### Notifications

| Frontend method | HTTP endpoint |
|---|---|
| `NotificationsApiService.search` | `GET /api/v1/notifications?page=...&size=...&statut=...&type=...` |
| `getById` | `GET /api/v1/notifications/{id}` |
| `NotificationsApiService.sendDirect` | `POST /api/v1/notifications/direct` |

The request supports `EMAIL`, `SMS`, `PUSH`, and `WHATSAPP`. `subject` is optional because it is mainly relevant to email/push.

### Read-only AI agent

| Frontend method | HTTP endpoint |
|---|---|
| `AgentApiService.getStatus` | `GET /api/v1/agent/status` |
| `chat` | `POST /api/v1/agent/chat` |

The chat endpoint is read-only by backend design and limits messages to 4,000 characters. Its response includes both `answer` and a `safety` statement.

The gateway's `/sse` and `/mcp/message/**` routes are intentionally not wrapped here. They implement the MCP transport for external MCP clients, while the Angular application's supported conversational interface is `/api/v1/agent`.

## Handling errors in a component

All HTTP failures handled by the interceptor arrive as `ApiError`:

```ts
this.usersApi.updateMyProfile(formValue).subscribe({
  next: (profile) => this.profile.set(profile),
  error: (error: ApiError) => {
    // General message from Spring ProblemDetail.detail
    this.errorMessage.set(error.message);

    // Per-field messages from ProblemDetail.errors
    const phoneError = error.fieldErrors['phone'];
  },
});
```

- `status === 0`: browser could not reach the gateway.
- `status === 400`: invalid request or field validation failure.
- `status === 401`: missing/invalid/expired JWT; the interceptor clears the session.
- `status === 403`: signed in but the role lacks permission.
- `status === 404`: requested resource does not exist.
- `status === 409`: conflict such as an email already in use.
- `status >= 500`: backend or downstream service failure.

Routing to a login screen after a 401 is intentionally left to the future UI/router layer. The connection layer clears stale credentials without making design/navigation decisions.

## Adding a future endpoint

1. Add or update the request/response interface in the correct model file. Copy the serialized Java DTO field names and enum values exactly.
2. Add one method to the relevant domain service. Keep path/query/body construction there.
3. Add a transport test when the endpoint has non-obvious details such as optional parameters, plain text/blobs, or unusual headers.
4. Update the coverage table in this document.

Import public models and services from the barrel instead of deep paths:

```ts
import { ProductRequest, StockApiService } from './core/api';
```

## Backend gap resolution and tenant behavior

The typed connection layer covers all controller operations. Business screens intentionally expose only role-owned workflows: internal policy checks and platform-global mutations are not rendered merely because a transport method exists. Store retrieval, paginated CRM lists, enterprise subscription history, payment/invoice history, delivery filters, the ADMIN notification log, SUPER_ADMIN plan management, and safe product deletion remain available through their correct interfaces.

Every business root created through these services now stores `enterpriseId`. Repository lookups and lists use the authenticated tenant from `TenantContext`, including indirect lookups such as inventory, orders, and invoices. Notification Kafka events carry the tenant ID so asynchronous records remain isolated. The obsolete public `/setup-admin` operation was removed from the user service, gateway bypass, MCP exclusion list, frontend client, and documentation. Staff authorization now correctly uses `hasRole('ADMIN')`.

Pagination uses one stable `PageResponse<T>` shape with `content`, zero-based `page`, `size`, totals, and `first`/`last`. Backend sizes are clamped to 1–100 records.

The new non-null `enterprise_id` columns are suitable for a fresh development database. If a database already contains business records, back up and either re-seed it or backfill each record with the correct enterprise before deployment; an enterprise cannot be inferred safely across separate microservice databases.
