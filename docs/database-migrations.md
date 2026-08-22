# Database migrations

Each service owns the Flyway history for its database. Business schema changes
must live in that service under `src/main/resources/db/migration`; the GitOps
repository owns PostgreSQL infrastructure and database creation only.

## Rules

- Add a new immutable `V<number>__description.sql` file for every schema change.
- Never edit a migration after it has been deployed.
- Keep `spring.jpa.hibernate.ddl-auto=validate`; Hibernate must verify schemas,
  not mutate them.
- Test migrations against both an existing database and an empty database.
- Keep cross-service foreign keys out of service databases. Store external IDs
  as ordinary columns and enforce ownership in application logic.

Services introduced to Flyway after Hibernate had already created their schema
use `baseline-on-migrate=true`. A populated database without Flyway history is
marked at the configured baseline version, while a fresh database executes the
baseline migration normally.

## Kubernetes lifecycle

On startup, Flyway acquires its database lock and applies pending migrations
before JPA starts. Kubernetes readiness remains false until migration and
Hibernate validation finish. A failed migration therefore blocks only the
owning service rollout and leaves the previous healthy ReplicaSet available.
