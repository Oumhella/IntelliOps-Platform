-- ============================================================
-- Init script: creates one database per microservice
-- Runs automatically on first Postgres container startup only
-- ============================================================

CREATE DATABASE erp_users;
CREATE DATABASE erp_abonnements;
CREATE DATABASE erp_leads;
CREATE DATABASE erp_stocks;
CREATE DATABASE erp_paiement;
CREATE DATABASE erp_deliveries;
CREATE DATABASE erp_notifications;
CREATE DATABASE erp_integrations;
