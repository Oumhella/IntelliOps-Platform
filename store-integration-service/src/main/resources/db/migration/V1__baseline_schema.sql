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
-- Name: integration_oauth_states; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integration_oauth_states (
    id bigint NOT NULL,
    consumed_at timestamp(6) with time zone,
    display_name character varying(255) NOT NULL,
    enterprise_id bigint NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    platform character varying(24) NOT NULL,
    state_hash character varying(64) NOT NULL,
    stock_location_id bigint NOT NULL,
    store_url character varying(500) NOT NULL,
    user_id bigint NOT NULL,
    CONSTRAINT integration_oauth_states_platform_check CHECK (((platform)::text = ANY ((ARRAY['SHOPIFY'::character varying, 'WOOCOMMERCE'::character varying])::text[])))
);


--
-- Name: integration_oauth_states_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.integration_oauth_states_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: integration_oauth_states_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.integration_oauth_states_id_seq OWNED BY public.integration_oauth_states.id;


--
-- Name: integration_product_mappings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integration_product_mappings (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    enterprise_id bigint NOT NULL,
    external_name character varying(255) NOT NULL,
    external_product_id character varying(255) NOT NULL,
    external_sku character varying(255),
    external_variant_id character varying(255) NOT NULL,
    internal_product_id bigint NOT NULL,
    connection_id bigint NOT NULL
);


--
-- Name: integration_product_mappings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.integration_product_mappings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: integration_product_mappings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.integration_product_mappings_id_seq OWNED BY public.integration_product_mappings.id;


--
-- Name: integration_webhook_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integration_webhook_events (
    id bigint NOT NULL,
    error_message character varying(1200),
    external_event_id character varying(255) NOT NULL,
    payload_hash character varying(64) NOT NULL,
    processed_at timestamp(6) with time zone,
    received_at timestamp(6) with time zone NOT NULL,
    status character varying(32) NOT NULL,
    topic character varying(255) NOT NULL,
    connection_id bigint NOT NULL,
    CONSTRAINT integration_webhook_events_status_check CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'PROCESSED'::character varying, 'ACTION_REQUIRED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: integration_webhook_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.integration_webhook_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: integration_webhook_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.integration_webhook_events_id_seq OWNED BY public.integration_webhook_events.id;


--
-- Name: store_connections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_connections (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    display_name character varying(255) NOT NULL,
    encrypted_credentials text NOT NULL,
    enterprise_id bigint NOT NULL,
    last_error character varying(1000),
    last_sync_at timestamp(6) with time zone,
    platform character varying(24) NOT NULL,
    status character varying(32) NOT NULL,
    stock_location_id bigint NOT NULL,
    store_url character varying(500) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    webhooks_active boolean NOT NULL,
    CONSTRAINT store_connections_platform_check CHECK (((platform)::text = ANY ((ARRAY['SHOPIFY'::character varying, 'WOOCOMMERCE'::character varying])::text[]))),
    CONSTRAINT store_connections_status_check CHECK (((status)::text = ANY ((ARRAY['CONNECTED'::character varying, 'ACTION_REQUIRED'::character varying, 'ERROR'::character varying, 'DISCONNECTED'::character varying])::text[])))
);


--
-- Name: store_connections_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.store_connections_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: store_connections_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.store_connections_id_seq OWNED BY public.store_connections.id;


--
-- Name: integration_oauth_states id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_oauth_states ALTER COLUMN id SET DEFAULT nextval('public.integration_oauth_states_id_seq'::regclass);


--
-- Name: integration_product_mappings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_product_mappings ALTER COLUMN id SET DEFAULT nextval('public.integration_product_mappings_id_seq'::regclass);


--
-- Name: integration_webhook_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_webhook_events ALTER COLUMN id SET DEFAULT nextval('public.integration_webhook_events_id_seq'::regclass);


--
-- Name: store_connections id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_connections ALTER COLUMN id SET DEFAULT nextval('public.store_connections_id_seq'::regclass);


--
-- Name: integration_oauth_states idx_oauth_state_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_oauth_states
    ADD CONSTRAINT idx_oauth_state_hash UNIQUE (state_hash);


--
-- Name: integration_oauth_states integration_oauth_states_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_oauth_states
    ADD CONSTRAINT integration_oauth_states_pkey PRIMARY KEY (id);


--
-- Name: integration_product_mappings integration_product_mappings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_product_mappings
    ADD CONSTRAINT integration_product_mappings_pkey PRIMARY KEY (id);


--
-- Name: integration_webhook_events integration_webhook_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_webhook_events
    ADD CONSTRAINT integration_webhook_events_pkey PRIMARY KEY (id);


--
-- Name: store_connections store_connections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_connections
    ADD CONSTRAINT store_connections_pkey PRIMARY KEY (id);


--
-- Name: integration_webhook_events uk488asoiqhso0bxb2wl1u5qnk6; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_webhook_events
    ADD CONSTRAINT uk488asoiqhso0bxb2wl1u5qnk6 UNIQUE (connection_id, external_event_id);


--
-- Name: integration_product_mappings uk5ny02tcawm7tq0wsa737rrh9f; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_product_mappings
    ADD CONSTRAINT uk5ny02tcawm7tq0wsa737rrh9f UNIQUE (connection_id, internal_product_id);


--
-- Name: integration_product_mappings ukeuh7wx5ebree24hf243ljjjc2; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_product_mappings
    ADD CONSTRAINT ukeuh7wx5ebree24hf243ljjjc2 UNIQUE (connection_id, external_variant_id);


--
-- Name: store_connections ukrkae6xwyl9vqfw2l3319kf5di; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_connections
    ADD CONSTRAINT ukrkae6xwyl9vqfw2l3319kf5di UNIQUE (enterprise_id, platform, store_url);


--
-- Name: integration_webhook_events fkip0dfs4t46p3y7tpnq08ubqn3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_webhook_events
    ADD CONSTRAINT fkip0dfs4t46p3y7tpnq08ubqn3 FOREIGN KEY (connection_id) REFERENCES public.store_connections(id);


--
-- Name: integration_product_mappings fkketyo5xaqu3lepkbuic3x742k; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_product_mappings
    ADD CONSTRAINT fkketyo5xaqu3lepkbuic3x742k FOREIGN KEY (connection_id) REFERENCES public.store_connections(id);


--
-- PostgreSQL database dump complete
--


