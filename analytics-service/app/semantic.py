import re
from dataclasses import dataclass
from datetime import datetime


@dataclass(frozen=True)
class QueryPlan:
    metric: str
    sql: str
    parameters: dict[str, object]
    visualization: str
    assumptions: list[str]


CATALOG = """
Allowed PostgreSQL reporting tables:
- fact_orders(enterprise_id, order_id, reference, store_id, status, payment_status,
  total_amount, source_updated_at, synchronized_at)
- fact_order_lines(enterprise_id, order_line_id, order_id, product_id, quantity, unit_price)
- dim_products(enterprise_id, product_id, global_sku, name, purchase_price, sale_price)
- dim_stores(enterprise_id, store_id, name, platform)
- fact_inventory(enterprise_id, inventory_id, store_id, product_id, available_quantity,
  reserved_quantity, alert_threshold, synchronized_at)
Paid revenue excludes ANNULEE and RETOURNEE orders and requires payment_status=PAID.
Tenant isolation is enforced by PostgreSQL RLS. Never generate an enterprise_id predicate.
"""


def deterministic_plan(question: str, now: datetime) -> QueryPlan | None:
    normalized = re.sub(r"\s+", " ", question.lower()).strip()
    count_requested = any(
        phrase in normalized for phrase in ("how many", "count", "number of")
    )
    if "low stock" in normalized or "below" in normalized and "stock" in normalized:
        return QueryPlan(
            "low_stock",
            """SELECT s.name AS store, p.name AS product, i.available_quantity,
                      i.alert_threshold FROM fact_inventory i
               JOIN dim_stores s USING (enterprise_id, store_id)
               JOIN dim_products p USING (enterprise_id, product_id)
               WHERE i.alert_threshold IS NOT NULL
                 AND i.available_quantity <= i.alert_threshold
               ORDER BY i.available_quantity, p.name LIMIT 50""",
            {},
            "table",
            [],
        )
    if "top" in normalized and "product" in normalized:
        return QueryPlan(
            "product_revenue",
            """SELECT p.name AS product, SUM(l.quantity * l.unit_price) AS revenue
               FROM fact_order_lines l JOIN fact_orders o USING (enterprise_id, order_id)
               JOIN dim_products p USING (enterprise_id, product_id)
               WHERE o.payment_status='PAID' AND o.status NOT IN ('ANNULEE','RETOURNEE')
               GROUP BY p.product_id, p.name ORDER BY revenue DESC LIMIT 5""",
            {},
            "bar",
            [],
        )
    if "revenue" in normalized:
        start = datetime(now.year, now.month, 1, tzinfo=now.tzinfo)
        return QueryPlan(
            "paid_revenue",
            """SELECT COALESCE(SUM(total_amount), 0) AS paid_revenue FROM fact_orders
               WHERE payment_status='PAID' AND status NOT IN ('ANNULEE','RETOURNEE')
                 AND source_updated_at >= %(start)s AND source_updated_at < %(end)s""",
            {"start": start, "end": now},
            "single_value",
            ["Interpreted the period as the current month to date."],
        )
    if "order" in normalized and ("status" in normalized or "distribution" in normalized):
        return QueryPlan(
            "orders_by_status",
            "SELECT status, COUNT(*) AS orders FROM fact_orders "
            "GROUP BY status ORDER BY orders DESC",
            {},
            "bar",
            [],
        )
    if count_requested and "product" in normalized:
        return QueryPlan(
            "product_count",
            "SELECT COUNT(*) AS products FROM dim_products",
            {},
            "single_value",
            [],
        )
    if count_requested and "order" in normalized:
        return QueryPlan(
            "order_count",
            "SELECT COUNT(*) AS orders FROM fact_orders",
            {},
            "single_value",
            [],
        )
    if count_requested and ("inventory" in normalized or "stock item" in normalized):
        return QueryPlan(
            "inventory_item_count",
            "SELECT COUNT(*) AS inventory_items FROM fact_inventory",
            {},
            "single_value",
            [],
        )
    if "stock" in normalized:
        return QueryPlan(
            "available_stock",
            """SELECT s.name AS store, p.name AS product, i.available_quantity,
                      i.reserved_quantity FROM fact_inventory i
               JOIN dim_stores s USING (enterprise_id, store_id)
               JOIN dim_products p USING (enterprise_id, product_id)
               ORDER BY s.name, p.name LIMIT 50""",
            {},
            "table",
            [],
        )
    return None


SUGGESTIONS = [
    "What is my paid revenue this month?",
    "What are my top five products by revenue?",
    "Show orders grouped by status.",
    "Which products are low in stock?",
]
