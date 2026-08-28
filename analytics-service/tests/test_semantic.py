from datetime import UTC, datetime

import pytest

from app.semantic import deterministic_plan, ensure_metric_allowed, suggestions_for_role


@pytest.mark.parametrize(
    ("question", "metric", "table"),
    [
        ("How many products do we have?", "product_count", "dim_products"),
        ("Give me the number of orders", "order_count", "fact_orders"),
        ("Count the inventory items", "inventory_item_count", "fact_inventory"),
    ],
)
def test_common_count_questions_do_not_require_the_llm(
    question: str, metric: str, table: str
) -> None:
    plan = deterministic_plan(question, datetime.now(UTC))

    assert plan is not None
    assert plan.metric == metric
    assert table in plan.sql
    assert plan.visualization == "single_value"


def test_order_status_remains_a_grouped_metric() -> None:
    plan = deterministic_plan("Count orders by status", datetime.now(UTC))

    assert plan is not None
    assert plan.metric == "orders_by_status"


@pytest.mark.parametrize(
    ("question", "metric"),
    [
        ("Combien de produits avons-nous ?", "product_count"),
        ("Montre les commandes regroupées par statut", "orders_by_status"),
        ("كم عدد المنتجات؟", "product_count"),
        ("اعرض الطلبات مجمعة حسب الحالة", "orders_by_status"),
        ("ما هو رقم المعاملات؟", "paid_revenue"),
    ],
)
def test_french_and_arabic_questions_use_the_same_approved_catalogue(
    question: str, metric: str
) -> None:
    plan = deterministic_plan(question, datetime.now(UTC))

    assert plan is not None
    assert plan.metric == metric


def test_csm_order_metrics_are_scoped_to_authenticated_user() -> None:
    plan = deterministic_plan(
        "Show my orders grouped by status",
        datetime.now(UTC),
        role="ROLE_CSM",
        user_id="42",
    )

    assert plan is not None
    assert "assigned_csm_id = %(actor_id)s" in plan.sql
    assert plan.parameters == {"actor_id": 42}


def test_csm_cannot_access_revenue_metric() -> None:
    with pytest.raises(PermissionError, match="cannot access"):
        ensure_metric_allowed("paid_revenue", "ROLE_CSM")


def test_logistic_suggestions_are_operational() -> None:
    suggestions = suggestions_for_role("ROLE_LOGISTIC")

    assert any("stock" in item.lower() for item in suggestions)
    assert all("revenue" not in item.lower() for item in suggestions)
