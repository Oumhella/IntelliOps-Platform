# IntelliOps analytics service

This service owns IntelliOps conversational BI. It synchronizes an RLS-protected reporting
database, answers bounded read-only questions, stores personal conversation history, returns
visualization specifications and exports authorized CSV reports.

Copy `.env.example` to an untracked `.env` and replace its placeholders. Source accounts need only
`CONNECT`, schema `USAGE` and `SELECT`. The synchronizer needs writes to the reporting database. Do
not use the read-only question-execution account for synchronization.

```powershell
python -m pip install -e ".[dev]"
python -m app.migrate
python -m app.sync.runner --once
pytest
ruff check .
```

Access is enforced from validated JWT claims. Admins can use the approved free-form SQL planner;
CSM and Logistic users use deterministic role catalogues. CSM queries include the authenticated
user ID and can see only assigned leads/orders. Logistic queries cover stock, fulfillment and
delivery operations without revenue or customer-sensitive fields.

The order checkpoint uses `commandes.created_at` because the source has no `updated_at`. A periodic
full reconciliation or event projection is required to capture later status changes to old orders.
# Historical PDF reporting

The service archives closed-period reports separately from conversational BI history:

- `WEEKLY` reports cover the previous Monday through Sunday and are intended for
  operational review.
- `MONTHLY` reports cover the previous calendar month and provide an executive summary.
- Reports contain role-scoped KPIs, status charts, product and revenue trends where the
  caller is authorized, data freshness, and up to five deterministic recommendations.
- Every PDF is stored with its tenant, audience, language, period, SHA-256 digest, and KPI
  snapshot. Historical documents are not regenerated when source data later changes.
- English, French, and Arabic PDFs use an embedded Unicode font; Arabic text is reshaped and
  rendered right-to-left.

API endpoints:

- `GET /api/v1/analytics/reports` lists visible report history.
- `POST /api/v1/analytics/reports/generate` generates the last closed weekly or monthly
  period in the requested locale.
- `GET /api/v1/analytics/reports/{id}/download` downloads a tenant-authorized PDF.

In Kubernetes, `analytics-report-weekly` runs Monday at 02:15 and
`analytics-report-monthly` runs on the first day of the month at 03:00, using
`Africa/Casablanca`. Scheduled reports are enterprise-wide administrator reports. CSM and
logistics users can create role-scoped reports from the BI screen; those reports remain
visible only to their creator and administrators.
