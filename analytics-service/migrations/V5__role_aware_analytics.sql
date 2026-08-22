ALTER TABLE fact_orders ADD COLUMN IF NOT EXISTS lead_id BIGINT;
ALTER TABLE fact_orders ADD COLUMN IF NOT EXISTS assigned_csm_id BIGINT;

CREATE TABLE IF NOT EXISTS dim_leads (
    enterprise_id BIGINT NOT NULL,
    lead_id BIGINT NOT NULL,
    assigned_csm_id BIGINT,
    status VARCHAR(50) NOT NULL,
    source VARCHAR(50) NOT NULL,
    synchronized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (enterprise_id, lead_id)
);

CREATE TABLE IF NOT EXISTS fact_deliveries (
    enterprise_id BIGINT NOT NULL,
    delivery_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    courier_id BIGINT,
    status VARCHAR(50) NOT NULL,
    carrier_type VARCHAR(50) NOT NULL,
    shipped_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    synchronized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (enterprise_id, delivery_id)
);

CREATE INDEX IF NOT EXISTS idx_leads_tenant_csm
    ON dim_leads(enterprise_id, assigned_csm_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_tenant_csm
    ON fact_orders(enterprise_id, assigned_csm_id, status);
CREATE INDEX IF NOT EXISTS idx_deliveries_tenant_status
    ON fact_deliveries(enterprise_id, status);

ALTER TABLE dim_leads ENABLE ROW LEVEL SECURITY;
ALTER TABLE dim_leads FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON dim_leads
    USING (enterprise_id = NULLIF(current_setting('app.enterprise_id', true), '')::bigint);
CREATE POLICY sync_insert ON dim_leads FOR INSERT WITH CHECK (true);
CREATE POLICY sync_update ON dim_leads FOR UPDATE USING (true) WITH CHECK (true);

ALTER TABLE fact_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE fact_deliveries FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON fact_deliveries
    USING (enterprise_id = NULLIF(current_setting('app.enterprise_id', true), '')::bigint);
CREATE POLICY sync_insert ON fact_deliveries FOR INSERT WITH CHECK (true);
CREATE POLICY sync_update ON fact_deliveries FOR UPDATE USING (true) WITH CHECK (true);

GRANT SELECT ON dim_leads, fact_deliveries TO analytics_query;
GRANT SELECT, INSERT, UPDATE, DELETE ON dim_leads, fact_deliveries TO analytics_sync;
