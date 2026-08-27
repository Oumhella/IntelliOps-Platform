# Platform administration

The platform control interface is separate from tenant business workflows:

- `/super-admin` is restricted to `ROLE_SUPER_ADMIN`.
- `/app` is for enterprise `ADMIN`, `CSM`, and `LOGISTIC` users.
- `/login` redirects a successful login to the correct workspace.

## Provisioning the platform administrator

There is intentionally no public setup endpoint and no default password. Configure the deployment environment before starting `user-service`:

```dotenv
PLATFORM_ADMIN_EMAIL=platform-admin@example.com
PLATFORM_ADMIN_PASSWORD=replace-with-a-long-random-secret
PLATFORM_ADMIN_FIRSTNAME=Platform
PLATFORM_ADMIN_LASTNAME=Administrator
```

The password must contain at least 12 characters. On startup, `user-service` creates the account only when it does not already exist. It never resets an existing platform administrator password.

## Real dashboard data

`GET /api/v1/platform/overview` is routed through the API gateway and enforced with `ROLE_SUPER_ADMIN`. It returns:

- enterprise, business-user, and active-account counts from the user PostgreSQL database;
- registered enterprise records and their user counts;
- online/offline service state and instance counts from Eureka.

The endpoint reports missing Eureka registrations as `OFFLINE`; the frontend does not substitute demo metrics when the endpoint is unavailable.

## Platform activity and settings

The super-admin workspace also exposes two authenticated, read-only platform views:

- `GET /api/v1/platform/audit` reports the current service-health and enterprise-registry observations;
- `GET /api/v1/platform/settings` reports the configured authentication mode, service-discovery provider, and expected registry members.

These values are derived from the live user-service configuration and Eureka state. Deployment configuration remains managed through the deployment environment rather than edited from the browser.
