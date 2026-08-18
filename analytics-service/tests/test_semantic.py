from datetime import UTC, datetime

import pytest

from app.semantic import deterministic_plan


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
