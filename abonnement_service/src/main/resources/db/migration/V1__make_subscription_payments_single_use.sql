-- A captured payment can activate or renew at most one subscription record.
-- Partial indexes preserve support for an explicit zero-price plan, whose
-- activation has no payment id.
DO $$
BEGIN
    IF to_regclass('public.abonnements') IS NOT NULL THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_subscription_payment_id
            ON abonnements(paiement_id)
            WHERE paiement_id IS NOT NULL;
    END IF;

    IF to_regclass('public.renouvellements') IS NOT NULL THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_renewal_payment_id
            ON renouvellements(paiement_id)
            WHERE paiement_id IS NOT NULL;
    END IF;
END
$$;
