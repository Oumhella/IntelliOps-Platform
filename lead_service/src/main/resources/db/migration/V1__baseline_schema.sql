--
-- PostgreSQL database dump
--


-- Dumped from database version 16.14
-- Dumped by pg_dump version 16.14

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: commandes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.commandes (
    id_commande bigint NOT NULL,
    adresse_livraison character varying(255),
    email character varying(255),
    nom_complet character varying(255),
    telephone character varying(255),
    ville character varying(255),
    reference character varying(255) NOT NULL,
    statut_commande character varying(255) NOT NULL,
    total_prix double precision NOT NULL,
    lead_id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    statut_paiement character varying(32) DEFAULT 'UNPAID'::character varying NOT NULL,
    stock_location_id bigint,
    stock_reservation_reference character varying(100),
    CONSTRAINT commandes_statut_commande_check CHECK (((statut_commande)::text = ANY ((ARRAY['EN_ATTENTE'::character varying, 'CONFIRMEE'::character varying, 'PREPARATION'::character varying, 'EXPEDIEE'::character varying, 'LIVREE'::character varying, 'ANNULEE'::character varying, 'RETOURNEE'::character varying])::text[]))),
    CONSTRAINT commandes_statut_paiement_check CHECK (((statut_paiement)::text = ANY ((ARRAY['UNPAID'::character varying, 'AWAITING_COLLECTION'::character varying, 'PAID'::character varying, 'PARTIALLY_REFUNDED'::character varying, 'REFUNDED'::character varying])::text[])))
);


--
-- Name: commandes_id_commande_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.commandes_id_commande_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: commandes_id_commande_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.commandes_id_commande_seq OWNED BY public.commandes.id_commande;


--
-- Name: leads; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leads (
    id_lead bigint NOT NULL,
    agent_id bigint,
    boutique_id bigint,
    enterprise_id bigint NOT NULL,
    adresse_livraison character varying(255),
    email character varying(255),
    nom_complet character varying(255),
    telephone character varying(255),
    ville character varying(255),
    ordre_priorite character varying(255) NOT NULL,
    statut_lead character varying(255) NOT NULL,
    source character varying(32) DEFAULT 'MANUAL'::character varying NOT NULL,
    CONSTRAINT leads_ordre_priorite_check CHECK (((ordre_priorite)::text = ANY ((ARRAY['IMMEDIATE'::character varying, 'HIGH'::character varying, 'MEDIUM'::character varying, 'LOW'::character varying])::text[]))),
    CONSTRAINT leads_source_check CHECK (((source)::text = ANY ((ARRAY['MANUAL'::character varying, 'SHOPIFY'::character varying, 'WOOCOMMERCE'::character varying, 'EXTERNAL_API'::character varying, 'IMPORT'::character varying])::text[]))),
    CONSTRAINT leads_statut_lead_check CHECK (((statut_lead)::text = ANY ((ARRAY['NEW_LEAD'::character varying, 'ATTEMPTED_CONTACT'::character varying, 'IN_PROGRESS'::character varying, 'SCHEDULED_RECALL'::character varying, 'UNREACHABLE'::character varying, 'REFUSED'::character varying, 'CONVERTED'::character varying])::text[])))
);


--
-- Name: leads_id_lead_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.leads_id_lead_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: leads_id_lead_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.leads_id_lead_seq OWNED BY public.leads.id_lead;


--
-- Name: lignes_commande; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lignes_commande (
    id_ligne bigint NOT NULL,
    prix_unitaire_applique double precision NOT NULL,
    produit_id bigint,
    quantite integer NOT NULL,
    commande_id bigint NOT NULL
);


--
-- Name: lignes_commande_id_ligne_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lignes_commande_id_ligne_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lignes_commande_id_ligne_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lignes_commande_id_ligne_seq OWNED BY public.lignes_commande.id_ligne;


--
-- Name: notes_interaction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notes_interaction (
    id_historique bigint NOT NULL,
    ancien_statut character varying(255),
    commentaire_agent text,
    date_changement timestamp(6) without time zone,
    nouveau_statut character varying(255),
    type_interaction character varying(255),
    lead_id bigint NOT NULL,
    CONSTRAINT notes_interaction_type_interaction_check CHECK (((type_interaction)::text = ANY ((ARRAY['APPEL_TEL'::character varying, 'WHATSAPP'::character varying, 'EMAIL_AUTO'::character varying])::text[])))
);


--
-- Name: notes_interaction_id_historique_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notes_interaction_id_historique_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notes_interaction_id_historique_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notes_interaction_id_historique_seq OWNED BY public.notes_interaction.id_historique;


--
-- Name: commandes id_commande; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commandes ALTER COLUMN id_commande SET DEFAULT nextval('public.commandes_id_commande_seq'::regclass);


--
-- Name: leads id_lead; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leads ALTER COLUMN id_lead SET DEFAULT nextval('public.leads_id_lead_seq'::regclass);


--
-- Name: lignes_commande id_ligne; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignes_commande ALTER COLUMN id_ligne SET DEFAULT nextval('public.lignes_commande_id_ligne_seq'::regclass);


--
-- Name: notes_interaction id_historique; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notes_interaction ALTER COLUMN id_historique SET DEFAULT nextval('public.notes_interaction_id_historique_seq'::regclass);


--
-- Name: commandes commandes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commandes
    ADD CONSTRAINT commandes_pkey PRIMARY KEY (id_commande);


--
-- Name: leads leads_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT leads_pkey PRIMARY KEY (id_lead);


--
-- Name: lignes_commande lignes_commande_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignes_commande
    ADD CONSTRAINT lignes_commande_pkey PRIMARY KEY (id_ligne);


--
-- Name: notes_interaction notes_interaction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notes_interaction
    ADD CONSTRAINT notes_interaction_pkey PRIMARY KEY (id_historique);


--
-- Name: commandes uk_7puwjgv8euf3hgg9md3c392wd; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commandes
    ADD CONSTRAINT uk_7puwjgv8euf3hgg9md3c392wd UNIQUE (reference);


--
-- Name: commandes uk_gn2mjhum112g7i00chm9sv7n2; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commandes
    ADD CONSTRAINT uk_gn2mjhum112g7i00chm9sv7n2 UNIQUE (lead_id);


--
-- Name: commandes fk4dafpr2b206psg5ce7flywf6l; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commandes
    ADD CONSTRAINT fk4dafpr2b206psg5ce7flywf6l FOREIGN KEY (lead_id) REFERENCES public.leads(id_lead);


--
-- Name: notes_interaction fkljsh45imc5bw7ftsi2f71wq4c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notes_interaction
    ADD CONSTRAINT fkljsh45imc5bw7ftsi2f71wq4c FOREIGN KEY (lead_id) REFERENCES public.leads(id_lead);


--
-- Name: lignes_commande fktry44xh8jbos217m4nk3wyyem; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignes_commande
    ADD CONSTRAINT fktry44xh8jbos217m4nk3wyyem FOREIGN KEY (commande_id) REFERENCES public.commandes(id_commande);


--
-- PostgreSQL database dump complete
--


