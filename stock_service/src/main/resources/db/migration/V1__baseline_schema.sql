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
-- Name: boutiques; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.boutiques (
    id_boutique bigint NOT NULL,
    admin_id bigint,
    cle_api character varying(255),
    enterprise_id bigint NOT NULL,
    nom_boutique character varying(255) NOT NULL,
    plateforme_type character varying(255) NOT NULL,
    CONSTRAINT boutiques_plateforme_type_check CHECK (((plateforme_type)::text = ANY ((ARRAY['MANUAL'::character varying, 'SHOPIFY'::character varying, 'WOOCOMMERCE'::character varying, 'YOUCAN'::character varying, 'MAGENTO'::character varying, 'AUTRE'::character varying])::text[])))
);


--
-- Name: boutiques_id_boutique_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.boutiques_id_boutique_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: boutiques_id_boutique_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.boutiques_id_boutique_seq OWNED BY public.boutiques.id_boutique;


--
-- Name: inventaires; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventaires (
    id bigint NOT NULL,
    quantite_disponible integer NOT NULL,
    quantite_reservee integer NOT NULL,
    boutique_id bigint NOT NULL,
    produit_id bigint NOT NULL,
    regle_approvisionnement_id bigint
);


--
-- Name: inventaires_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inventaires_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventaires_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventaires_id_seq OWNED BY public.inventaires.id;


--
-- Name: mouvements_stock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mouvements_stock (
    id bigint NOT NULL,
    auteur_id bigint,
    date_mouvement timestamp(6) without time zone,
    quantite integer NOT NULL,
    type_mouvement character varying(255) NOT NULL,
    inventaire_id bigint NOT NULL,
    CONSTRAINT mouvements_stock_type_mouvement_check CHECK (((type_mouvement)::text = ANY ((ARRAY['REASSORT'::character varying, 'VENTE'::character varying, 'RETOUR'::character varying, 'PERTE'::character varying, 'AJUSTEMENT'::character varying])::text[])))
);


--
-- Name: mouvements_stock_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mouvements_stock_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mouvements_stock_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mouvements_stock_id_seq OWNED BY public.mouvements_stock.id;


--
-- Name: produits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.produits (
    id_produit bigint NOT NULL,
    enterprise_id bigint NOT NULL,
    global_sku character varying(255) NOT NULL,
    nom_produit character varying(255) NOT NULL,
    prix_achat double precision NOT NULL,
    prix_vente double precision NOT NULL
);


--
-- Name: produits_id_produit_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.produits_id_produit_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: produits_id_produit_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.produits_id_produit_seq OWNED BY public.produits.id_produit;


--
-- Name: regles_approvisionnement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.regles_approvisionnement (
    id bigint NOT NULL,
    est_actif boolean,
    quantite_recommande_auto integer NOT NULL,
    seuil_alerte integer NOT NULL
);


--
-- Name: regles_approvisionnement_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.regles_approvisionnement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: regles_approvisionnement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.regles_approvisionnement_id_seq OWNED BY public.regles_approvisionnement.id;


--
-- Name: reservations_stock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reservations_stock (
    id bigint NOT NULL,
    auteur_id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    enterprise_id bigint NOT NULL,
    produit_id bigint NOT NULL,
    quantite integer NOT NULL,
    reference_operation character varying(100) NOT NULL,
    statut character varying(255) NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    inventaire_id bigint NOT NULL,
    CONSTRAINT reservations_stock_statut_check CHECK (((statut)::text = ANY ((ARRAY['RESERVED'::character varying, 'CONSUMED'::character varying, 'RELEASED'::character varying])::text[])))
);


--
-- Name: reservations_stock_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reservations_stock_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reservations_stock_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reservations_stock_id_seq OWNED BY public.reservations_stock.id;


--
-- Name: boutiques id_boutique; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.boutiques ALTER COLUMN id_boutique SET DEFAULT nextval('public.boutiques_id_boutique_seq'::regclass);


--
-- Name: inventaires id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventaires ALTER COLUMN id SET DEFAULT nextval('public.inventaires_id_seq'::regclass);


--
-- Name: mouvements_stock id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mouvements_stock ALTER COLUMN id SET DEFAULT nextval('public.mouvements_stock_id_seq'::regclass);


--
-- Name: produits id_produit; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.produits ALTER COLUMN id_produit SET DEFAULT nextval('public.produits_id_produit_seq'::regclass);


--
-- Name: regles_approvisionnement id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regles_approvisionnement ALTER COLUMN id SET DEFAULT nextval('public.regles_approvisionnement_id_seq'::regclass);


--
-- Name: reservations_stock id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations_stock ALTER COLUMN id SET DEFAULT nextval('public.reservations_stock_id_seq'::regclass);


--
-- Name: boutiques boutiques_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.boutiques
    ADD CONSTRAINT boutiques_pkey PRIMARY KEY (id_boutique);


--
-- Name: inventaires inventaires_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventaires
    ADD CONSTRAINT inventaires_pkey PRIMARY KEY (id);


--
-- Name: mouvements_stock mouvements_stock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mouvements_stock
    ADD CONSTRAINT mouvements_stock_pkey PRIMARY KEY (id);


--
-- Name: produits produits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.produits
    ADD CONSTRAINT produits_pkey PRIMARY KEY (id_produit);


--
-- Name: regles_approvisionnement regles_approvisionnement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regles_approvisionnement
    ADD CONSTRAINT regles_approvisionnement_pkey PRIMARY KEY (id);


--
-- Name: reservations_stock reservations_stock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations_stock
    ADD CONSTRAINT reservations_stock_pkey PRIMARY KEY (id);


--
-- Name: inventaires uk_4m5to991nln38funch749y2o; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventaires
    ADD CONSTRAINT uk_4m5to991nln38funch749y2o UNIQUE (regle_approvisionnement_id);


--
-- Name: reservations_stock uk_stock_reservation_reference_product; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations_stock
    ADD CONSTRAINT uk_stock_reservation_reference_product UNIQUE (enterprise_id, reference_operation, produit_id);


--
-- Name: produits ukoyncipql0ff1rgjg0wjh3ib4h; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.produits
    ADD CONSTRAINT ukoyncipql0ff1rgjg0wjh3ib4h UNIQUE (enterprise_id, global_sku);


--
-- Name: inventaires fk640q1t3ltwasqcflsfk96jwaq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventaires
    ADD CONSTRAINT fk640q1t3ltwasqcflsfk96jwaq FOREIGN KEY (produit_id) REFERENCES public.produits(id_produit);


--
-- Name: mouvements_stock fk95adclkxbq313kboce96i1jte; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mouvements_stock
    ADD CONSTRAINT fk95adclkxbq313kboce96i1jte FOREIGN KEY (inventaire_id) REFERENCES public.inventaires(id);


--
-- Name: reservations_stock fkdtuey9mp5tmavrwlhonpstuq8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations_stock
    ADD CONSTRAINT fkdtuey9mp5tmavrwlhonpstuq8 FOREIGN KEY (inventaire_id) REFERENCES public.inventaires(id);


--
-- Name: inventaires fkhimf37mfjjhu7itx6vimos5pt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventaires
    ADD CONSTRAINT fkhimf37mfjjhu7itx6vimos5pt FOREIGN KEY (boutique_id) REFERENCES public.boutiques(id_boutique);


--
-- Name: inventaires fkocypudq5scdrlwi19qhrmbgen; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventaires
    ADD CONSTRAINT fkocypudq5scdrlwi19qhrmbgen FOREIGN KEY (regle_approvisionnement_id) REFERENCES public.regles_approvisionnement(id);


--
-- PostgreSQL database dump complete
--


