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
- dim_leads(enterprise_id, lead_id, assigned_csm_id, status, source, synchronized_at)
- fact_deliveries(enterprise_id, delivery_id, order_id, courier_id, status, carrier_type,
  shipped_at, delivered_at, synchronized_at)
Paid revenue excludes ANNULEE and RETOURNEE orders and requires payment_status=PAID.
Tenant isolation is enforced by PostgreSQL RLS. Never generate an enterprise_id predicate.
"""

ROLE_METRICS = {
    "ROLE_ADMIN": {
        "available_stock",
        "delivery_count",
        "deliveries_by_status",
        "inventory_item_count",
        "lead_count",
        "leads_by_status",
        "low_stock",
        "order_count",
        "orders_by_status",
        "paid_revenue",
        "product_count",
        "product_revenue",
    },
    "ROLE_CSM": {"lead_count", "leads_by_status", "order_count", "orders_by_status"},
    "ROLE_LOGISTIC": {
        "available_stock",
        "delivery_count",
        "deliveries_by_status",
        "inventory_item_count",
        "low_stock",
        "order_count",
        "orders_by_status",
        "product_count",
    },
}

ROLE_SUGGESTIONS = {
    "ROLE_ADMIN": [
        "What is my paid revenue this month?",
        "What are my top five products by revenue?",
        "Show orders grouped by status.",
        "Show deliveries grouped by status.",
        "Which products are low in stock?",
    ],
    "ROLE_CSM": [
        "How many leads are assigned to me?",
        "Show my assigned leads grouped by status.",
        "How many orders belong to my assigned leads?",
        "Show my orders grouped by status.",
    ],
    "ROLE_LOGISTIC": [
        "Show orders grouped by status.",
        "Show deliveries grouped by status.",
        "Which products are low in stock?",
        "Show available stock by location.",
    ],
}


def deterministic_plan(
    question: str,
    now: datetime,
    role: str = "ROLE_ADMIN",
    user_id: str | None = None,
) -> QueryPlan | None:
    normalized = _canonicalize_question(question)
    count_requested = any(
        phrase in normalized for phrase in ("how many", "count", "number of")
    )
    actor_id = int(user_id) if user_id and user_id.isdigit() else None
    personal = role == "ROLE_CSM"

    if "lead" in normalized and ("status" in normalized or "group" in normalized):
        where = " WHERE assigned_csm_id = %(actor_id)s" if personal else ""
        return QueryPlan(
            "leads_by_status",
            "SELECT status, COUNT(*) AS leads FROM dim_leads"
            f"{where} GROUP BY status ORDER BY leads DESC",
            {"actor_id": actor_id} if personal else {},
            "donut",
            ["Limited to leads assigned to the authenticated CSM."] if personal else [],
        )
    if "lead" in normalized and count_requested:
        where = " WHERE assigned_csm_id = %(actor_id)s" if personal else ""
        return QueryPlan(
            "lead_count",
            f"SELECT COUNT(*) AS leads FROM dim_leads{where}",
            {"actor_id": actor_id} if personal else {},
            "single_value",
            ["Limited to leads assigned to the authenticated CSM."] if personal else [],
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
        where = " WHERE assigned_csm_id = %(actor_id)s" if personal else ""
        return QueryPlan(
            "orders_by_status",
            "SELECT status, COUNT(*) AS orders FROM fact_orders"
            f"{where} GROUP BY status ORDER BY orders DESC",
            {"actor_id": actor_id} if personal else {},
            "donut",
            ["Limited to orders from leads assigned to the authenticated CSM."]
            if personal
            else [],
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
        where = " WHERE assigned_csm_id = %(actor_id)s" if personal else ""
        return QueryPlan(
            "order_count",
            f"SELECT COUNT(*) AS orders FROM fact_orders{where}",
            {"actor_id": actor_id} if personal else {},
            "single_value",
            ["Limited to orders from leads assigned to the authenticated CSM."]
            if personal
            else [],
        )
    if count_requested and ("inventory" in normalized or "stock item" in normalized):
        return QueryPlan(
            "inventory_item_count",
            "SELECT COUNT(*) AS inventory_items FROM fact_inventory",
            {},
            "single_value",
            [],
        )
    if "deliver" in normalized and ("status" in normalized or "group" in normalized):
        return QueryPlan(
            "deliveries_by_status",
            "SELECT status, COUNT(*) AS deliveries FROM fact_deliveries "
            "GROUP BY status ORDER BY deliveries DESC",
            {},
            "donut",
            [],
        )
    if "deliver" in normalized and count_requested:
        return QueryPlan(
            "delivery_count",
            "SELECT COUNT(*) AS deliveries FROM fact_deliveries",
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


def _canonicalize_question(question: str) -> str:
    normalized = re.sub(r"\s+", " ", question.lower()).strip()
    aliases = {
        "combien": "how many", "nombre de": "number of", "كم": "how many", "عدد": "count",
        "prospects": "leads", "prospect": "lead", "العملاء المحتملون": "leads",
        "statuts": "status", "statut": "status", "état": "status", "الحالة": "status", "حالة": "status",
        "regroupées": "group", "regroupés": "group", "groupées": "group", "مجمعة": "group",
        "produits": "products", "produit": "product", "المنتجات": "products", "منتج": "product",
        "commandes": "orders", "commande": "order", "الطلبات": "orders", "طلب": "order",
        "livraisons": "deliveries", "livraison": "delivery", "التوصيلات": "deliveries", "توصيل": "delivery",
        "chiffre d’affaires": "revenue", "chiffre d'affaires": "revenue", "revenu": "revenue",
        "رقم المعاملات": "revenue", "الإيرادات": "revenue",
        "مخزون": "stock", "faible stock": "low stock", "stock faible": "low stock", "مخزون منخفض": "low stock",
        "meilleurs": "top", "أفضل": "top",
    }
    for source, target in aliases.items():
        normalized = normalized.replace(source, target)
    return normalized


def suggestions_for_role(role: str) -> list[str]:
    return ROLE_SUGGESTIONS.get(role, [])


def ensure_metric_allowed(metric: str, role: str) -> None:
    if role == "ROLE_ADMIN":
        return
    if metric not in ROLE_METRICS.get(role, set()):
        raise PermissionError(f"The {role.removeprefix('ROLE_')} role cannot access {metric}.")
