# IntelliOps analytics service — Sprint 1

This service owns the tenant-scoped reporting database for future conversational BI. Sprint 1
contains PostgreSQL migrations, read-only source extractors, idempotent projections, metric
definitions and isolation tests. It intentionally has no LLM or public API yet.

Copy `.env.example` to an untracked `.env` and replace its placeholders. Source accounts need only
`CONNECT`, schema `USAGE` and `SELECT`. The synchronizer needs writes to `analytics_db`. Do not use
the future read-only question-execution account for synchronization.

```powershell
python -m pip install -e ".[dev]"
python -m app.migrate
python -m app.sync.runner
pytest
ruff check .
```

The order checkpoint uses `commandes.created_at` because the source has no `updated_at`. A periodic
full reconciliation or event projection is required to capture later status changes to old orders.
