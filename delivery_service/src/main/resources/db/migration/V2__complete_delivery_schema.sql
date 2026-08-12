CREATE TABLE IF NOT EXISTS livraisons (
    id_livraison bigserial PRIMARY KEY,
    client_email varchar(255),
    code_suivi_tracking varchar(64) NOT NULL,
    delivery_date timestamp(6) without time zone,
    endpoint_api_url varchar(255),
    enterprise_id bigint NOT NULL,
    external_livreur_id bigint,
    montantacollecter_cod double precision NOT NULL,
    nom_societe varchar(255),
    reference_commande_id bigint NOT NULL,
    shipping_date timestamp(6) without time zone,
    statut_livraison varchar(30) NOT NULL,
    type_transporteur varchar(30) NOT NULL,
    adresse_livraison varchar(255),
    client_nom_complet varchar(255),
    client_telephone varchar(255),
    ville_livraison varchar(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_tracking_code
    ON livraisons(code_suivi_tracking);
CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_tenant_order
    ON livraisons(enterprise_id, reference_commande_id);
