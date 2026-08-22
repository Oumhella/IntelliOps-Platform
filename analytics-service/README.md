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
