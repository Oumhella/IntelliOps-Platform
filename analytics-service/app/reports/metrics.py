from dataclasses import dataclass
from decimal import Decimal
from typing import Any

from app.config import Settings
from app.db import connection
from app.reports.periods import ReportPeriod
from app.reports.repository import writable_url


@dataclass(frozen=True)
class ReportData:
    current: dict[str, Any]
    previous: dict[str, Any]
    order_statuses: list[dict[str, Any]]
    delivery_statuses: list[dict[str, Any]]
    top_products: list[dict[str, Any]]
    daily_revenue: list[dict[str, Any]]
    lead_statuses: list[dict[str, Any]]
    low_stock: list[dict[str, Any]]
    freshness: str | None


def _scalar(row: dict[str, Any], key: str) -> int | float:
    value = row.get(key) or 0
    if isinstance(value, Decimal):
        return float(value)
    return value


def _scope(role: str, user_id: str, alias: str = "o") -> tuple[str, dict[str, Any]]:
    if role == "ROLE_CSM" and user_id.isdigit():
        return f" AND {alias}.assigned_csm_id = %(actor_id)s", {"actor_id": int(user_id)}
    return "", {}


def collect_report_data(
    settings: Settings,
    enterprise_id: int,
    role: str,
    user_id: str,
    period: ReportPeriod,
) -> ReportData:
    previous = period.previous
    scope, actor = _scope(role, user_id)
    base = {"enterprise_id": enterprise_id, **actor}

    def period_params(item: ReportPeriod) -> dict[str, Any]:
        return {**base, "start": item.start, "end": item.end}

    def aggregate(conn, item: ReportPeriod) -> dict[str, Any]:
        row = conn.execute(
            f"""
            SELECT COUNT(*) AS orders,
                   COUNT(*) FILTER (WHERE payment_status = 'PAID'
                     AND status NOT IN ('ANNULEE', 'RETOURNEE')) AS paid_orders,
                   COALESCE(SUM(total_amount) FILTER (WHERE payment_status = 'PAID'
                     AND status NOT IN ('ANNULEE', 'RETOURNEE')), 0) AS paid_revenue,
                   COUNT(*) FILTER (WHERE status IN ('ANNULEE', 'RETOURNEE')) AS exceptions
            FROM fact_orders o
            WHERE o.enterprise_id = %(enterprise_id)s
              AND o.source_updated_at >= %(start)s AND o.source_updated_at < %(end)s
              {scope}
            """,
            period_params(item),
        ).fetchone()
        delivery_scope = ""
        if role == "ROLE_CSM" and user_id.isdigit():
            delivery_scope = (
                " AND EXISTS (SELECT 1 FROM fact_orders o WHERE "
                "o.enterprise_id = d.enterprise_id AND o.order_id = d.order_id "
                "AND o.assigned_csm_id = %(actor_id)s)"
            )
        delivery = conn.execute(
            f"""
            SELECT COUNT(*) AS deliveries,
                   COUNT(*) FILTER (WHERE d.status = 'LIVREE') AS delivered
            FROM fact_deliveries d
            WHERE d.enterprise_id = %(enterprise_id)s
              AND COALESCE(d.delivered_at, d.shipped_at, d.synchronized_at) >= %(start)s
              AND COALESCE(d.delivered_at, d.shipped_at, d.synchronized_at) < %(end)s
              {delivery_scope}
            """,
            period_params(item),
        ).fetchone()
        paid_orders = int(_scalar(row, "paid_orders"))
        paid_revenue = float(_scalar(row, "paid_revenue"))
        return {
            "orders": int(_scalar(row, "orders")),
            "paid_orders": paid_orders,
            "paid_revenue": paid_revenue,
            "average_order_value": round(paid_revenue / paid_orders, 2) if paid_orders else 0,
            "exceptions": int(_scalar(row, "exceptions")),
            "deliveries": int(_scalar(delivery, "deliveries")),
            "delivered": int(_scalar(delivery, "delivered")),
        }

    with connection(writable_url(settings), readonly=True) as conn:
        current = aggregate(conn, period)
        previous_values = aggregate(conn, previous)
        current["low_stock_items"] = int(
            conn.execute(
                """
                SELECT COUNT(*) AS value FROM fact_inventory
                WHERE enterprise_id = %(enterprise_id)s
                  AND alert_threshold IS NOT NULL
                  AND available_quantity <= alert_threshold
                """,
                base,
            ).fetchone()["value"]
        )
        current["products"] = int(
            conn.execute(
                "SELECT COUNT(*) AS value FROM dim_products "
                "WHERE enterprise_id = %(enterprise_id)s",
                base,
            ).fetchone()["value"]
        )
        current["leads"] = int(
            conn.execute(
                "SELECT COUNT(*) AS value FROM dim_leads l "
                "WHERE enterprise_id = %(enterprise_id)s"
                + (" AND l.assigned_csm_id = %(actor_id)s" if role == "ROLE_CSM" else ""),
                base,
            ).fetchone()["value"]
        )
        statuses = list(conn.execute(
            f"""
            SELECT status, COUNT(*) AS value FROM fact_orders o
            WHERE o.enterprise_id = %(enterprise_id)s
              AND o.source_updated_at >= %(start)s AND o.source_updated_at < %(end)s
              {scope}
            GROUP BY status ORDER BY value DESC
            """,
            period_params(period),
        ))
        deliveries = list(conn.execute(
            """
            SELECT status, COUNT(*) AS value FROM fact_deliveries
            WHERE enterprise_id = %(enterprise_id)s
            GROUP BY status ORDER BY value DESC
            """,
            base,
        ))
        products = list(conn.execute(
            f"""
            SELECT p.name AS label, SUM(l.quantity * l.unit_price) AS value
            FROM fact_order_lines l
            JOIN fact_orders o USING (enterprise_id, order_id)
            JOIN dim_products p USING (enterprise_id, product_id)
            WHERE o.enterprise_id = %(enterprise_id)s
              AND o.payment_status = 'PAID'
              AND o.status NOT IN ('ANNULEE', 'RETOURNEE')
              AND o.source_updated_at >= %(start)s AND o.source_updated_at < %(end)s
              {scope}
            GROUP BY p.product_id, p.name ORDER BY value DESC LIMIT 5
            """,
            period_params(period),
        ))
        daily = list(conn.execute(
            f"""
            SELECT o.source_updated_at::date AS label,
                   SUM(o.total_amount) AS value
            FROM fact_orders o
            WHERE o.enterprise_id = %(enterprise_id)s
              AND o.payment_status = 'PAID'
              AND o.status NOT IN ('ANNULEE', 'RETOURNEE')
              AND o.source_updated_at >= %(start)s AND o.source_updated_at < %(end)s
              {scope}
            GROUP BY o.source_updated_at::date ORDER BY label
            """,
            period_params(period),
        ))
        leads = list(conn.execute(
            "SELECT status, COUNT(*) AS value FROM dim_leads l "
            "WHERE enterprise_id = %(enterprise_id)s"
            + (" AND l.assigned_csm_id = %(actor_id)s" if role == "ROLE_CSM" else "")
            + " GROUP BY status ORDER BY value DESC",
            base,
        ))
        low_stock = []
        if role in {"ROLE_ADMIN", "ROLE_LOGISTIC"}:
            low_stock = list(conn.execute(
                """
                SELECT p.name AS product, s.name AS location,
                       i.available_quantity, i.alert_threshold
                FROM fact_inventory i
                JOIN dim_products p USING (enterprise_id, product_id)
                JOIN dim_stores s USING (enterprise_id, store_id)
                WHERE i.enterprise_id = %(enterprise_id)s
                  AND i.alert_threshold IS NOT NULL
                  AND i.available_quantity <= i.alert_threshold
                ORDER BY i.available_quantity, p.name LIMIT 10
                """,
                base,
            ))
        freshness_row = conn.execute(
            """
            SELECT MAX(value) AS freshness FROM (
                SELECT MAX(synchronized_at) AS value FROM fact_orders
                 WHERE enterprise_id = %(enterprise_id)s
                UNION ALL SELECT MAX(synchronized_at) FROM fact_inventory
                 WHERE enterprise_id = %(enterprise_id)s
                UNION ALL SELECT MAX(synchronized_at) FROM fact_deliveries
                 WHERE enterprise_id = %(enterprise_id)s
            ) snapshots
            """,
            base,
        ).fetchone()
    return ReportData(
        current=current,
        previous=previous_values,
        order_statuses=statuses,
        delivery_statuses=deliveries,
        top_products=[{"label": row["label"], "value": float(row["value"])} for row in products],
        daily_revenue=[{"label": str(row["label"]), "value": float(row["value"])} for row in daily],
        lead_statuses=leads,
        low_stock=low_stock,
        freshness=str(freshness_row["freshness"]) if freshness_row["freshness"] else None,
    )
