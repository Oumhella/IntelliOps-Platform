ALTER TABLE livraisons
    ADD COLUMN IF NOT EXISTS accepted_at timestamp(6) without time zone,
    ADD COLUMN IF NOT EXISTS started_at timestamp(6) without time zone,
    ADD COLUMN IF NOT EXISTS last_attempt_at timestamp(6) without time zone,
    ADD COLUMN IF NOT EXISTS return_requested_at timestamp(6) without time zone,
    ADD COLUMN IF NOT EXISTS attempt_count integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS failure_reason varchar(40),
    ADD COLUMN IF NOT EXISTS failure_note varchar(1000),
    ADD COLUMN IF NOT EXISTS last_latitude double precision,
    ADD COLUMN IF NOT EXISTS last_longitude double precision,
    ADD COLUMN IF NOT EXISTS delivered_to varchar(255),
    ADD COLUMN IF NOT EXISTS proof_signature varchar(255),
    ADD COLUMN IF NOT EXISTS proof_photo_object_key varchar(512),
    ADD COLUMN IF NOT EXISTS proof_captured_at timestamp(6) without time zone,
    ADD COLUMN IF NOT EXISTS cod_collected_amount numeric(14,2),
    ADD COLUMN IF NOT EXISTS cod_discrepancy_note varchar(1000),
    ADD COLUMN IF NOT EXISTS cod_reconciled_at timestamp(6) without time zone,
    ADD COLUMN IF NOT EXISTS cod_reconciled_by bigint;

CREATE INDEX IF NOT EXISTS idx_delivery_courier_status
    ON livraisons(enterprise_id, external_livreur_id, statut_livraison);
