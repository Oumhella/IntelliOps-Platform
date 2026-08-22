CREATE TEMP TABLE inventory_merge AS
SELECT id AS duplicate_id,
       MIN(id) OVER (PARTITION BY boutique_id, produit_id) AS keeper_id
FROM inventaires;

DELETE FROM inventory_merge WHERE duplicate_id = keeper_id;

UPDATE mouvements_stock movement
SET inventaire_id = merge.keeper_id
FROM inventory_merge merge
WHERE movement.inventaire_id = merge.duplicate_id;

UPDATE reservations_stock reservation
SET inventaire_id = merge.keeper_id
FROM inventory_merge merge
WHERE reservation.inventaire_id = merge.duplicate_id;

UPDATE inventaires keeper
SET quantite_disponible = totals.quantite_disponible,
    quantite_reservee = totals.quantite_reservee
FROM (
    SELECT boutique_id, produit_id,
           SUM(quantite_disponible) AS quantite_disponible,
           SUM(quantite_reservee) AS quantite_reservee,
           MIN(id) AS keeper_id
    FROM inventaires
    GROUP BY boutique_id, produit_id
) totals
WHERE keeper.id = totals.keeper_id;

DELETE FROM inventaires inventory
USING inventory_merge merge
WHERE inventory.id = merge.duplicate_id;

DELETE FROM regles_approvisionnement rule
WHERE NOT EXISTS (
    SELECT 1 FROM inventaires inventory
    WHERE inventory.regle_approvisionnement_id = rule.id
);

ALTER TABLE inventaires
    ADD CONSTRAINT uk_inventory_location_product UNIQUE (boutique_id, produit_id);
