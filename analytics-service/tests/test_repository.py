from unittest.mock import MagicMock

import pytest

from app.sync.repository import ReportingRepository


def test_upsert_is_tenant_keyed_and_idempotent() -> None:
    db = MagicMock()
    rows = [{"enterprise_id": 101, "product_id": 7, "name": "A"}]
    assert (
        ReportingRepository(db).upsert("dim_products", rows, ("enterprise_id", "product_id")) == 1
    )
    cursor = db.cursor.return_value.__enter__.return_value
    sql = cursor.executemany.call_args.args[0]
    assert "ON CONFLICT (enterprise_id, product_id)" in sql
    assert cursor.executemany.call_args.args[1] == [(101, 7, "A")]
    db.executemany.assert_not_called()


def test_upsert_rejects_unapproved_table() -> None:
    with pytest.raises(ValueError, match="Unsupported"):
        ReportingRepository(MagicMock()).upsert("users", [{"id": 1}], ("id",))
