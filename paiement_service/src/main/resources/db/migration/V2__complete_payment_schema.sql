CREATE TABLE IF NOT EXISTS factures (
    id bigserial PRIMARY KEY,
    chemin_fichier_pdf varchar(255),
    date_emission timestamp(6) without time zone,
    numero_facture_unique varchar(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS modeles_tokenisation (
    id bigserial PRIMARY KEY,
    system_account_id bigint NOT NULL,
    token_carte_securise varchar(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS transactions_paiement (
    id bigserial PRIMARY KEY,
    enterprise_id bigint NOT NULL,
    idempotency_key varchar(100) NOT NULL,
    mode varchar(255) NOT NULL,
    montant numeric(19,2) NOT NULL,
    montant_rembourse numeric(19,2) DEFAULT 0.00 NOT NULL,
    reference_source_id bigint NOT NULL,
    statut varchar(255) NOT NULL,
    type_contexte varchar(255) NOT NULL,
    facture_id bigint,
    tokenisation_id bigint,
    provider_transaction_id varchar(120),
    consumption_reference varchar(180),
    consumed_at timestamp without time zone,
    notification_email varchar(180)
);

ALTER TABLE transactions_paiement
    ADD COLUMN IF NOT EXISTS facture_id bigint,
    ADD COLUMN IF NOT EXISTS tokenisation_id bigint,
    ADD COLUMN IF NOT EXISTS notification_email varchar(180);

CREATE UNIQUE INDEX IF NOT EXISTS uk_invoice_number
    ON factures(numero_facture_unique);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tokenized_card
    ON modeles_tokenisation(token_carte_securise);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_tenant_idempotency
    ON transactions_paiement(enterprise_id, idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_provider_transaction
    ON transactions_paiement(provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;
