from pathlib import Path


def test_every_reporting_table_has_forced_rls_policy() -> None:
    sql = (Path(__file__).parents[1] / "migrations" / "V2__tenant_security.sql").read_text()
    tables = ("dim_products", "dim_stores", "fact_orders", "fact_order_lines", "fact_inventory")
    assert all(table in sql for table in tables)
    assert "FORCE ROW LEVEL SECURITY" in sql
    assert "current_setting(''app.enterprise_id'', true)" in sql


def test_examples_rely_on_database_tenant_context() -> None:
    sql = (Path(__file__).parents[1] / "docs" / "example-queries.sql").read_text()
    assert "set_config('app.enterprise_id'" in sql
    assert "WHERE enterprise_id =" not in sql


def test_role_aware_tables_have_forced_rls() -> None:
    sql = (Path(__file__).parents[1] / "migrations" / "V5__role_aware_analytics.sql").read_text()
    assert "ALTER TABLE dim_leads FORCE ROW LEVEL SECURITY" in sql
    assert "ALTER TABLE fact_deliveries FORCE ROW LEVEL SECURITY" in sql
