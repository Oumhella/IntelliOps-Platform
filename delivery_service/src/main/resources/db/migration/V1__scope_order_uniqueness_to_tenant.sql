-- Earlier versions made reference_commande_id globally unique. Order ids are
-- generated per tenant/service boundary, so that constraint incorrectly blocks
-- two enterprises from shipping their own order with the same numeric id.
-- The entity now owns the correct (enterprise_id, reference_commande_id)
-- constraint; this migration only removes the obsolete single-column one.
DO $$
DECLARE
    legacy_constraint record;
BEGIN
    FOR legacy_constraint IN
        SELECT constraint_row.conname
        FROM pg_constraint constraint_row
        JOIN pg_class table_row ON table_row.oid = constraint_row.conrelid
        JOIN pg_namespace schema_row ON schema_row.oid = table_row.relnamespace
        WHERE schema_row.nspname = 'public'
          AND table_row.relname = 'livraisons'
          AND constraint_row.contype = 'u'
          AND (
              SELECT array_agg(column_row.attname ORDER BY key_row.ordinality)
              FROM unnest(constraint_row.conkey) WITH ORDINALITY AS key_row(attnum, ordinality)
              JOIN pg_attribute column_row
                ON column_row.attrelid = constraint_row.conrelid
               AND column_row.attnum = key_row.attnum
          ) = ARRAY['reference_commande_id']::name[]
    LOOP
        EXECUTE format(
            'ALTER TABLE public.livraisons DROP CONSTRAINT %I',
            legacy_constraint.conname
        );
    END LOOP;
END
$$;
