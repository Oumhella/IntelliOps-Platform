SELECT 'CREATE DATABASE erp_users'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'erp_users')\gexec
SELECT 'CREATE DATABASE erp_abonnements'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'erp_abonnements')\gexec
SELECT 'CREATE DATABASE erp_leads'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'erp_leads')\gexec
SELECT 'CREATE DATABASE erp_stocks'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'erp_stocks')\gexec
SELECT 'CREATE DATABASE erp_paiement'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'erp_paiement')\gexec
SELECT 'CREATE DATABASE erp_deliveries'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'erp_deliveries')\gexec
SELECT 'CREATE DATABASE erp_notifications'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'erp_notifications')\gexec
SELECT 'CREATE DATABASE erp_integrations'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'erp_integrations')\gexec
SELECT 'CREATE DATABASE erp_analytics'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'erp_analytics')\gexec

\connect erp_stocks
ALTER TABLE IF EXISTS boutiques DROP COLUMN IF EXISTS cle_api;
