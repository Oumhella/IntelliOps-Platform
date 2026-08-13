-- Run this migration as the analytics database owner. Passwords are supplied separately by Vault.
DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'analytics_query') THEN
    CREATE ROLE analytics_query NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'analytics_sync') THEN
    CREATE ROLE analytics_sync NOLOGIN BYPASSRLS;
  END IF;
END $$;
GRANT USAGE ON SCHEMA public TO analytics_query, analytics_sync;
GRANT SELECT ON dim_products, dim_stores, fact_orders, fact_order_lines, fact_inventory
  TO analytics_query;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO analytics_sync;
ALTER ROLE analytics_query SET default_transaction_read_only = on;
ALTER ROLE analytics_query SET statement_timeout = '5s';
