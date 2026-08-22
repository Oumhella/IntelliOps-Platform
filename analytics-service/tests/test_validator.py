import pytest

from app.validator import validate_sql


def test_validator_adds_bound() -> None:
    sql = validate_sql("SELECT status, COUNT(*) FROM fact_orders GROUP BY status", 50)
    assert "LIMIT 50" in sql


def test_validator_allows_role_aware_reporting_tables() -> None:
    assert "dim_leads" in validate_sql("SELECT status FROM dim_leads", 50)
    assert "fact_deliveries" in validate_sql("SELECT status FROM fact_deliveries", 50)


@pytest.mark.parametrize(
    "sql",
    [
        "DELETE FROM fact_orders",
        "SELECT * FROM users",
        "SELECT * FROM fact_orders; SELECT 1",
        "SELECT * FROM fact_orders WHERE enterprise_id = 9",
        "DROP TABLE fact_orders",
    ],
)
def test_validator_rejects_unsafe_sql(sql: str) -> None:
    with pytest.raises(ValueError):
        validate_sql(sql, 50)
