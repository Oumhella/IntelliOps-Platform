-- Older development databases were initially created by Hibernate with a
-- CHECK constraint generated from the original Java enum. Adding new enum
-- constants does not update that database constraint, so replace it explicitly.
ALTER TABLE livraisons
    DROP CONSTRAINT IF EXISTS livraisons_statut_livraison_check;

ALTER TABLE livraisons
    ADD CONSTRAINT livraisons_statut_livraison_check CHECK (
        statut_livraison IN (
            'ASSIGNEE',
            'ACCEPTEE',
            'EN_PREPARATION',
            'CHEZ_TRANSPORTEUR',
            'EN_COURS',
            'LIVREE',
            'ECHEC',
            'RETOUR_DEMANDE',
            'RETOUR'
        )
    );
