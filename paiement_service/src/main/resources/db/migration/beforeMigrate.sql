-- Flyway executes before Hibernate. Ensure the base table exists before the
-- first versioned hardening migration runs on a completely fresh database.
-- As an idempotent callback, this also preserves V1's checksum and remains safe
-- for databases where V1 has already succeeded.
CREATE TABLE IF NOT EXISTS transactions_paiement (
    id bigserial PRIMARY KEY,
    idempotency_key varchar(100) NOT NULL,
    enterprise_id bigint NOT NULL,
    reference_source_id bigint NOT NULL,
    type_contexte varchar(50) NOT NULL,
    montant numeric(19,2) NOT NULL,
    montant_rembourse numeric(19,2) NOT NULL DEFAULT 0.00,
    mode varchar(50) NOT NULL,
    statut varchar(50) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_tenant_idempotency
    ON transactions_paiement(enterprise_id, idempotency_key);
