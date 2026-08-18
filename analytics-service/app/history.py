import json

from app.config import Settings
from app.db import connection
from app.models import ConversationMessage, ConversationMessageCreate


def _url(settings: Settings) -> str:
    url = settings.sync_analytics_database_url or settings.migration_database_url
    if not url:
        raise ValueError(
            "A writable analytics database URL is required for conversation history"
        )
    return url


def list_messages(
    settings: Settings,
    tenant: str,
    user: str,
    surface: str,
    limit: int = 100,
) -> list[ConversationMessage]:
    query = """
        SELECT id, surface, role, content, payload, created_at
        FROM conversation_messages
        WHERE enterprise_id = %s AND user_id = %s AND surface = %s
        ORDER BY created_at ASC
        LIMIT %s
    """
    with connection(_url(settings)) as conn:
        rows = list(conn.execute(query, (tenant, user, surface, limit)))
    return [ConversationMessage(**row) for row in rows]


def add_message(
    settings: Settings,
    tenant: str,
    user: str,
    item: ConversationMessageCreate,
) -> ConversationMessage:
    query = """
        INSERT INTO conversation_messages(
            enterprise_id, user_id, surface, role, content, payload
        )
        VALUES (%s, %s, %s, %s, %s, %s::jsonb)
        RETURNING id, surface, role, content, payload, created_at
    """
    payload = json.dumps(item.payload) if item.payload is not None else None
    params = (tenant, user, item.surface, item.role, item.content, payload)
    with connection(_url(settings)) as conn:
        row = conn.execute(query, params).fetchone()
    return ConversationMessage(**row)


def clear_messages(settings: Settings, tenant: str, user: str, surface: str) -> None:
    query = """
        DELETE FROM conversation_messages
        WHERE enterprise_id = %s AND user_id = %s AND surface = %s
    """
    with connection(_url(settings)) as conn:
        conn.execute(query, (tenant, user, surface))
