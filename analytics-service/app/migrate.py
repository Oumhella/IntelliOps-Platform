from pathlib import Path

from psycopg import sql

from app.config import get_settings
from app.db import connection


def apply_migrations(
    database_url: str,
    directory: Path | None = None,
    query_password: str = "",
    sync_password: str = "",
) -> None:
    migration_dir = directory or Path(__file__).resolve().parent.parent / "migrations"
    with connection(database_url) as conn:
        conn.execute(
            "CREATE TABLE IF NOT EXISTS schema_migrations "
            "(version text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())"
        )
        applied = {row["version"] for row in conn.execute("SELECT version FROM schema_migrations")}
        for path in sorted(migration_dir.glob("V*.sql")):
            if path.name in applied:
                continue
            conn.execute(path.read_text(encoding="utf-8"))
            conn.execute("INSERT INTO schema_migrations(version) VALUES (%s)", (path.name,))
        if query_password and sync_password:
            conn.execute(
                sql.SQL("ALTER ROLE analytics_query LOGIN PASSWORD {}").format(
                    sql.Literal(query_password)
                )
            )
            conn.execute(
                sql.SQL("ALTER ROLE analytics_sync LOGIN PASSWORD {}").format(
                    sql.Literal(sync_password)
                )
            )


def main() -> None:
    settings = get_settings()
    if not settings.migration_database_url:
        raise ValueError("MIGRATION_DATABASE_URL is required for schema migrations")
    apply_migrations(
        settings.migration_database_url,
        query_password=settings.analytics_query_password,
        sync_password=settings.analytics_sync_password,
    )


if __name__ == "__main__":
    main()
