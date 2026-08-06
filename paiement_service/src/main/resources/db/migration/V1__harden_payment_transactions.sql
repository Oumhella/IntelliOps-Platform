-- Adopt the existing development schema and add the provider/consumption data
-- required for auditable, single-use subscription payments.
ALTER TABLE IF EXISTS transactions_paiement
    ADD COLUMN IF NOT EXISTS provider_transaction_id varchar(120),
    ADD COLUMN IF NOT EXISTS consumption_reference varchar(180),
    ADD COLUMN IF NOT EXISTS consumed_at timestamp;

ALTER TABLE IF EXISTS transactions_paiement
    ALTER COLUMN montant TYPE numeric(19,2) USING round(montant::numeric, 2),
    ALTER COLUMN montant_rembourse TYPE numeric(19,2) USING round(montant_rembourse::numeric, 2);

UPDATE transactions_paiement
SET montant_rembourse = 0.00
WHERE montant_rembourse IS NULL;

ALTER TABLE IF EXISTS transactions_paiement
    ALTER COLUMN montant_rembourse SET DEFAULT 0.00,
    ALTER COLUMN montant_rembourse SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_provider_transaction
    ON transactions_paiement(provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;
