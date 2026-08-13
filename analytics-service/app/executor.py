from datetime import date, datetime
from decimal import Decimal

from app.db import connection


def execute_query(
    url: str, tenant_id: int, sql: str, parameters: dict, timeout_ms: int
) -> tuple[list[dict], datetime | None]:
    with connection(url, readonly=True) as conn:
        conn.execute("SELECT set_config('app.enterprise_id', %s, true)", (str(tenant_id),))
        conn.execute("SELECT set_config('statement_timeout', %s, true)", (str(timeout_ms),))
        rows = list(conn.execute(sql, parameters))
        freshness = conn.execute(
            "SELECT MAX(synchronized_at) AS freshness FROM fact_orders"
        ).fetchone()["freshness"]
    return [serialize_row(row) for row in rows], freshness


def serialize_row(row: dict) -> dict:
    return {
        key: float(value)
        if isinstance(value, Decimal)
        else value.isoformat()
        if isinstance(value, (date, datetime))
        else value
        for key, value in row.items()
    }
