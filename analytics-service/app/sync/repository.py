from collections.abc import Iterable
from datetime import datetime

from psycopg import Connection


class ReportingRepository:
    def __init__(self, conn: Connection):
        self.conn = conn

    def checkpoint(self, source: str) -> datetime | None:
        row = self.conn.execute(
            "SELECT last_source_updated_at FROM sync_checkpoints WHERE source_name = %s", (source,)
        ).fetchone()
        return row["last_source_updated_at"] if row else None

    def upsert(self, table: str, rows: Iterable[dict], keys: tuple[str, ...]) -> int:
        allowed = {
            "fact_orders",
            "fact_order_lines",
            "dim_products",
            "dim_stores",
            "fact_inventory",
        }
        if table not in allowed:
            raise ValueError(f"Unsupported reporting table: {table}")
        materialized = list(rows)
        if not materialized:
            return 0
        columns = tuple(materialized[0])
        if any(tuple(row) != columns for row in materialized):
            raise ValueError("All rows in a batch must have identical columns")
        placeholders = ", ".join(["%s"] * len(columns))
        updates = ", ".join(
            f"{column}=EXCLUDED.{column}" for column in columns if column not in keys
        )
        sql = (
            f"INSERT INTO {table} ({', '.join(columns)}) VALUES ({placeholders}) "
            f"ON CONFLICT ({', '.join(keys)}) DO UPDATE SET {updates}, synchronized_at=now()"
        )
        parameters = [tuple(row[column] for column in columns) for row in materialized]
        with self.conn.cursor() as cursor:
            cursor.executemany(sql, parameters)
        return len(materialized)

    def save_checkpoint(self, source: str, value: datetime) -> None:
        self.conn.execute(
            """
            INSERT INTO sync_checkpoints(source_name, last_source_updated_at, last_success_at)
            VALUES (%s, %s, now()) ON CONFLICT (source_name) DO UPDATE SET
            last_source_updated_at=EXCLUDED.last_source_updated_at, last_success_at=now()
            """,
            (source, value),
        )
