CREATE TABLE IF NOT EXISTS analytics_reports (
    id UUID PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    requested_by VARCHAR(255),
    audience_role VARCHAR(50) NOT NULL,
    period_type VARCHAR(20) NOT NULL CHECK (period_type IN ('WEEKLY', 'MONTHLY')),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    locale VARCHAR(5) NOT NULL CHECK (locale IN ('en', 'fr', 'ar')),
    status VARCHAR(20) NOT NULL DEFAULT 'READY' CHECK (status IN ('READY', 'FAILED')),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    pdf_content BYTEA NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    CONSTRAINT valid_report_period CHECK (period_end > period_start)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_scheduled_enterprise_report
    ON analytics_reports(enterprise_id, audience_role, period_type, period_start, locale)
    WHERE requested_by IS NULL;
CREATE INDEX IF NOT EXISTS idx_reports_tenant_generated
    ON analytics_reports(enterprise_id, generated_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_tenant_period
    ON analytics_reports(enterprise_id, period_type, period_start DESC);

ALTER TABLE analytics_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE analytics_reports FORCE ROW LEVEL SECURITY;
CREATE POLICY report_tenant_isolation ON analytics_reports
    USING (
        enterprise_id = NULLIF(current_setting('app.enterprise_id', true), '')::bigint
    );

GRANT SELECT ON analytics_reports TO analytics_query;
GRANT SELECT, INSERT, UPDATE, DELETE ON analytics_reports TO analytics_sync;
