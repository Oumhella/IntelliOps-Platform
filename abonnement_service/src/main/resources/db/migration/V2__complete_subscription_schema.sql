CREATE TABLE IF NOT EXISTS plans_abonnement (
    id_plan bigserial PRIMARY KEY,
    description varchar(255),
    duree varchar(255) NOT NULL,
    est_actif varchar(255) NOT NULL,
    limite_commandes_mois integer NOT NULL,
    max_periode_desactivation integer NOT NULL,
    min_jours_entre_desactivation integer NOT NULL,
    nom_plan varchar(255) NOT NULL,
    prix double precision NOT NULL
);

CREATE TABLE IF NOT EXISTS abonnements (
    id_abonnement bigserial PRIMARY KEY,
    date_debut date NOT NULL,
    date_fin date NOT NULL,
    enterprise_id bigint NOT NULL,
    paiement_id bigint,
    prix_paye double precision NOT NULL,
    statut varchar(255) NOT NULL,
    user_id bigint NOT NULL,
    plan_id bigint NOT NULL REFERENCES plans_abonnement(id_plan),
    contact_email varchar(255)
);

CREATE TABLE IF NOT EXISTS changements_plan (
    id bigserial PRIMARY KEY,
    ancien_plan_id bigint NOT NULL,
    changed_at timestamp(6) without time zone NOT NULL,
    montant double precision NOT NULL,
    nouveau_plan_id bigint NOT NULL,
    paiement_id bigint NOT NULL,
    abonnement_id bigint NOT NULL REFERENCES abonnements(id_abonnement)
);

CREATE TABLE IF NOT EXISTS desactivations (
    id bigserial PRIMARY KEY,
    date_debut_desactivation date NOT NULL,
    date_fin_desactivation date NOT NULL,
    motif varchar(500),
    abonnement_id bigint NOT NULL REFERENCES abonnements(id_abonnement)
);

CREATE TABLE IF NOT EXISTS renouvellements (
    id bigserial PRIMARY KEY,
    date_renouvellement date NOT NULL,
    paiement_id bigint,
    prix_applique double precision NOT NULL,
    statut varchar(255) NOT NULL,
    type_renouvellement varchar(255) NOT NULL,
    abonnement_id bigint NOT NULL REFERENCES abonnements(id_abonnement)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_plan_change_payment
    ON changements_plan(paiement_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_subscription_payment_id
    ON abonnements(paiement_id) WHERE paiement_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_renewal_payment_id
    ON renouvellements(paiement_id) WHERE paiement_id IS NOT NULL;
