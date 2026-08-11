# Public onboarding and store-integration workflow

## Public SaaS entry

`/` is intentionally a public product page. It loads only active plan catalogue data from
`GET /api/v1/plans`; tenant metrics and service health remain restricted to `/super-admin`.

The onboarding sequence is:

1. The visitor selects a live plan on `/`.
2. `/register?plan={id}` creates the enterprise and its first `ROLE_ADMIN` account.
3. The selected plan is carried to `/login` in browser session state.
4. After authentication, that administrator is sent to
   `/app/subscriptions?onboarding=1&plan={id}`.
5. A paid plan remains inactive until Stripe confirms and the payment service consumes the
   verified PaymentIntent. Free plans can be activated without manufacturing a payment.
6. Subscription entitlement filters keep operational APIs locked until the workspace has an
   active plan.

This avoids accepting anonymous payments before a tenant exists and prevents orphaned Stripe
transactions from being mistaken for subscriptions.

## Service boundary

`store-integration-service` owns every provider-specific concern:

- Shopify authorization-code OAuth, callback HMAC verification, and Admin GraphQL calls;
- WooCommerce REST API authorization, Basic Auth credentials, and webhook creation;
- AES-256-GCM encrypted credential envelopes backed by a master key from Vault;
- tenant-scoped connections, one-time OAuth state, product mappings, and webhook audit records;
- raw-body webhook signature verification and provider delivery-ID deduplication;
- conversion from provider payloads to the internal order contract.

Core CRM and stock services do not know provider access tokens, provider URLs, or webhook
formats. The connector calls their internal-only endpoints with a short-lived signed service JWT.
The obsolete stock-service test/synchronize adapters and the `cleApi` API/entity field were
removed; stock-service now models internal fulfillment locations only. On the next Compose start,
the idempotent database job also drops the obsolete plaintext `boutiques.cle_api` column. Those
legacy credentials are intentionally not migrated because a single unencrypted key cannot be
safely converted into the provider-specific encrypted credential envelope; stores must be
authorized again through the integration workspace.

## Order workflow and authorities

```text
Shopify / WooCommerce
        |
        | signed order webhook
        v
store-integration-service
  verify signature -> deduplicate -> normalize -> require product mappings
        |
        | short-lived tenant service JWT
        v
lead-service -----------------------> stock-service
  idempotent external order            reserve mapped stock
  provider payment state               release on cancellation
        |
        v
normal IntelliOps fulfillment and delivery workflow
```

IntelliOps is the authority for its fulfillment-location reservations. Incoming provider
inventory notifications are therefore not allowed to overwrite reserved quantities. The current
connector imports orders; it does **not yet push IntelliOps availability back to Shopify or
WooCommerce**. Outbound inventory publication should be implemented as a separate event-driven
flow with per-connection reconciliation cursors before claiming two-way stock synchronization.

## Correctness rules

- The connection belongs to the authenticated tenant and references a real tenant stock location.
- Provider credentials are encrypted and are never returned through the API.
- WooCommerce origins must be public HTTPS origins; loopback, private, link-local, reserved, port,
  query, fragment, and credential-bearing URLs are rejected to reduce SSRF risk.
- Shopify accepts only the permanent `{shop}.myshopify.com` hostname.
- Shopify callbacks require the provider HMAC, a one-time ten-minute database state, and the
  secure HttpOnly browser-state cookie set when authorization begins.
- Webhooks are verified against the raw request bytes before JSON parsing.
- Shopify webhook IDs and WooCommerce delivery IDs are required and unique per connection.
- Every external variant must be explicitly mapped to an internal product before import.
- Imported prices, final order totals, currency, and payment state come only from the verified
  provider payload. This deployment accepts `MAD`, matching the payment service.
- Repeated create deliveries return the existing deterministic order instead of reserving twice.
- Cancellations release unconsumed reservations; cancellation after shipment becomes a return.
- If an external update changes line items or the total, it becomes `ACTION_REQUIRED` instead of
  silently corrupting an existing reservation. A future reconciliation operation can automate
  those edits with a stock-adjustment saga.

## Operations and configuration

Required production configuration:

- `INTEGRATION_PUBLIC_BASE_URL`: public HTTPS gateway origin used by provider callbacks;
- `FRONTEND_PUBLIC_URL`: public browser origin;
- `INTEGRATION_ORDER_CURRENCY`: defaults to `MAD` and must match payment currency;
- `SHOPIFY_CLIENT_ID` and `SHOPIFY_CLIENT_SECRET`: supplied to `vault-init`, not the application
  container;
- `SHOPIFY_SCOPES`: defaults to least-privilege `read_orders,read_products`;
- `INTEGRATION_CREDENTIALS_MASTER_KEY`: optional pre-provisioned Base64 32-byte key. If absent,
  Vault generates and preserves one.

`database-init` is an idempotent Compose job. It creates databases that were added after an
existing PostgreSQL volume was initialized, including `erp_integrations`.

Provider setup still requires public infrastructure: configure the Shopify allowed redirect URL
and app URL to the gateway callback, and make the gateway reachable over trusted HTTPS. A blank
public callback URL deliberately disables the connect buttons rather than presenting a flow that
cannot complete.

## Current bounded limitations

- The catalogue picker currently loads the first 100 provider products/variants per API request.
  Cursor/page-based mapping search should be added for larger catalogues.
- Local disconnect destroys the stored credential envelope but cannot guarantee provider-side
  revocation. The administrator is told to uninstall the Shopify app or revoke WooCommerce keys.
- Automatic external item-edit reconciliation and outbound inventory publication are held for a
  later stock saga; both require compensating transactions and reconciliation jobs, not direct
  quantity overwrites.
