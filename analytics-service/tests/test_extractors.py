from unittest.mock import MagicMock, patch

from app.sync.extractors import extract_orders


def test_order_checkpoint_has_an_explicit_postgres_type() -> None:
    cursor = MagicMock()
    cursor.__iter__.return_value = iter(())
    database = MagicMock()
    database.execute.return_value = cursor
    context = MagicMock()
    context.__enter__.return_value = database

    with patch("app.sync.extractors.connection", return_value=context):
        assert list(extract_orders("postgresql://unused", None, 100)) == []

    query = database.execute.call_args.args[0]
    assert "%(after)s::timestamp IS NULL" in query
    assert "c.created_at > %(after)s::timestamp" in query
