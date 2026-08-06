# IntelliOps SaaS operating model

The UI is organized around ownership, not around the physical microservice that exposes an endpoint. Every business record is tenant-scoped by `enterpriseId`; a frontend role check improves the experience, while Spring Security and repository tenant predicates remain the authorization boundary.

## Actor boundaries

| Actor | Owns | Must not own |
| --- | --- | --- |
| SUPER_ADMIN | Platform health, enterprise visibility, and the global commercial plan catalogue | Tenant leads, stock, staff, orders, or fulfillment |
| ADMIN | One enterprise workspace: staff/access, commerce integrations, subscription, billing, operational oversight, and notification audit | Global plans or another enterprise's data |
| CSM | Its assigned lead queue, customer interactions, conversion, order composition, and commercial confirmation/cancellation | Platform configuration, staff, integrations, inventory, or another CSM's leads |
| LOGISTIC | Products, inventory, replenishment, fulfillment, carriers, delivery progression, and reception | Global plans, tenant access, store credentials, CRM contacts, or billing |

## URL map

| URL | Business meaning | Roles |
| --- | --- | --- |
| `/super-admin` | Platform control, enterprises, services, and global plan catalogue | SUPER_ADMIN |
| `/app` | Role-specific enterprise overview | ADMIN, CSM, LOGISTIC |
| `/app/leads` | Tenant pipeline; CSM results are forced to the authenticated agent | ADMIN read/assign, CSM operate |
| `/app/orders` | Commercial and fulfillment handoff | ADMIN read, CSM commercial steps, LOGISTIC fulfillment steps |
| `/app/stock` | Store visibility, catalogue, inventory, and replenishment | ADMIN, LOGISTIC |
| `/app/deliveries` | Shipment and carrier lifecycle; couriers see only their assignments | ADMIN, LOGISTIC, LIVREUR |
| `/app/billing` | Tenant payment transactions, refunds, cancellation, and invoices | ADMIN |
| `/app/subscriptions` | The enterprise's current entitlement and history; active plans are read-only | ADMIN |
| `/app/team` | Tenant CSM, LOGISTIC, and LIVREUR accounts | ADMIN |
| `/app/notifications` | Tenant-wide outbound delivery audit and direct administrative send | ADMIN |
| `/app/assistant` | Read-only operational guidance | ADMIN, CSM, LOGISTIC |
| `/app/profile` | The authenticated person's identity and password | ADMIN, CSM, LOGISTIC, LIVREUR |

`/login` and `/register` are public. Registration creates a new tenant and its first ADMIN. There is no setup-admin route.

## Platform catalogue versus tenant subscription

Plans are global platform products because `PlanAbonnement` has no `enterpriseId`. Creating, updating, or archiving them is therefore protected with `ROLE_SUPER_ADMIN` and rendered only in `/super-admin`.

A tenant ADMIN sees active plans as a read-only catalogue. A subscription is an enterprise entitlement:

- the backend derives both `enterpriseId` and the activating account from the authenticated context;
- callers cannot submit an arbitrary user id;
- a paid plan cannot activate from a missing, pending, mismatched, or reused payment;
- checkout charges the server-side catalogue price rather than trusting a browser amount;
- one enterprise cannot create a second active or paused entitlement;
- immediate upgrades must target an active, different, higher-priced plan;
- the UI shows one current organization plan and enterprise history instead of a per-user subscription console;
- expiration and monthly-limit checks are backend policy concerns and are not exposed as manual buttons.

The retained `userId` on a subscription identifies the tenant account that activated it; it is not the subscription owner.

## Operational lifecycles

### CRM to order

1. A CSM creates a lead; the backend assigns the authenticated CSM id.
2. CSM searches are forcibly scoped to that id, even if a different `agentId` is sent.
3. The CSM records interactions and may convert only a lead assigned to them.
4. ADMIN can review the tenant pipeline and reassign work but cannot perform CSM contact/conversion actions.
5. Order lines can be changed only while an order is `EN_ATTENTE` and only by the originating CSM.

### Order fulfillment

The backend rejects skipped or reversed state changes. The supported progression is:

`EN_ATTENTE → CONFIRMEE → PREPARATION → EXPEDIEE → LIVREE`

Cancellation is allowed before shipment; a shipped or delivered order can move to `RETOURNEE` where appropriate. CSM owns confirmation/cancellation, LOGISTIC owns preparation onward, and ADMIN has read-only oversight.

### Store, stock, and delivery

- Store credentials, connection tests, and synchronization are ADMIN integration settings.
- LOGISTIC can see connected stores but cannot edit or retrieve credentials.
- ADMIN and LOGISTIC manage products, stock movements, and replenishment rules.
- Delivery status transitions are validated server-side; delivered and returned records are terminal.
- Reception can only be confirmed for a dispatched delivery.

### Billing and notifications

Payment and invoice records are enterprise-scoped financial data and are ADMIN-only. The notification screen is a tenant-wide outbound delivery log rather than a personal inbox, so it is also ADMIN-only. Operational users receive business notifications through their configured delivery channel; they do not receive access to every recipient address and message in the tenant log.

## Technical enforcement

- `roleGuard` mirrors route ownership in Angular.
- Controller `@PreAuthorize` rules enforce the same role matrix in every service.
- `TenantContext.requireEnterpriseId()` and tenant-aware repository queries prevent cross-enterprise reads and writes.
- Auth responses include the current user id so role-specific screens can request their own queue; the backend still overrides CSM lead filters with the trusted JWT user id.
- The API interceptor attaches the bearer token and clears the session after a `401`.
- Empty states are real empty backend results; there is no mock-data fallback.

## Payment integration

Card checkout uses Stripe's Payment Element. The backend creates the PaymentIntent from the authoritative business amount and returns only its client secret plus the publishable key. Angular confirms it with Stripe, then the backend retrieves and validates the provider transaction before subscription activation, renewal, upgrade, or billing completion. Redirect-based authentication is recovered from session storage, and an unconfirmed payment leaves the business operation unchanged. Cash-on-delivery remains a separate direct initiation flow. Scheduled expiration and usage-limit enforcement remain automated backend jobs, not user-triggered UI controls.
