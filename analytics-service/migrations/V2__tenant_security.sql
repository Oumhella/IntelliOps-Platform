DO $$
DECLARE table_name text;
BEGIN
  FOREACH table_name IN ARRAY ARRAY['dim_products','dim_stores','fact_orders','fact_order_lines','fact_inventory']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON %I USING (enterprise_id = NULLIF(current_setting(''app.enterprise_id'', true), '''')::bigint)',
      table_name
    );
    -- Database grants still decide who may write. These policies let the dedicated ETL role
    -- load all tenants while the future query role receives SELECT only and remains RLS-scoped.
    EXECUTE format('CREATE POLICY sync_insert ON %I FOR INSERT WITH CHECK (true)', table_name);
    EXECUTE format('CREATE POLICY sync_update ON %I FOR UPDATE USING (true) WITH CHECK (true)', table_name);
  END LOOP;
END $$;
