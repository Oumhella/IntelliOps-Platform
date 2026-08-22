-- Keep the database constraint aligned with TypeMouvement. Reservation lifecycle
-- operations were added after the baseline migration.
ALTER TABLE mouvements_stock
    DROP CONSTRAINT IF EXISTS mouvements_stock_type_mouvement_check;

ALTER TABLE mouvements_stock
    ADD CONSTRAINT mouvements_stock_type_mouvement_check
    CHECK (type_mouvement IN (
        'REASSORT', 'RESERVATION', 'LIBERATION', 'VENTE',
        'RETOUR', 'PERTE', 'AJUSTEMENT'
    ));
