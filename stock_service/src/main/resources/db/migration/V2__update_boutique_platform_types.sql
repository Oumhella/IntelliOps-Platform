ALTER TABLE boutiques
    DROP CONSTRAINT IF EXISTS boutiques_plateforme_type_check;

ALTER TABLE boutiques
    ADD CONSTRAINT boutiques_plateforme_type_check
    CHECK (plateforme_type IN (
        'MANUAL',
        'SHOPIFY',
        'WOOCOMMERCE',
        'YOUCAN',
        'MAGENTO',
        'AUTRE'
    ));
