import json
from app.db import connection
from app.models import ConversationMessage, ConversationMessageCreate

def _url(settings):
    url=settings.sync_analytics_database_url or settings.migration_database_url
    if not url: raise ValueError("A writable analytics database URL is required for conversation history")
    return url
def list_messages(settings,tenant,user,surface,limit=100):
    with connection(_url(settings)) as conn: rows=list(conn.execute("SELECT id,surface,role,content,payload,created_at FROM conversation_messages WHERE enterprise_id=%s AND user_id=%s AND surface=%s ORDER BY created_at ASC LIMIT %s",(tenant,user,surface,limit)))
    return [ConversationMessage(**row) for row in rows]
def add_message(settings,tenant,user,item:ConversationMessageCreate):
    with connection(_url(settings)) as conn: row=conn.execute("INSERT INTO conversation_messages(enterprise_id,user_id,surface,role,content,payload) VALUES(%s,%s,%s,%s,%s,%s::jsonb) RETURNING id,surface,role,content,payload,created_at",(tenant,user,item.surface,item.role,item.content,json.dumps(item.payload) if item.payload is not None else None)).fetchone()
    return ConversationMessage(**row)
def clear_messages(settings,tenant,user,surface):
    with connection(_url(settings)) as conn: conn.execute("DELETE FROM conversation_messages WHERE enterprise_id=%s AND user_id=%s AND surface=%s",(tenant,user,surface))
