import json
from dataclasses import dataclass
from datetime import date
from typing import Any
from uuid import UUID

from app.config import Settings
from app.db import connection
from app.models import HistoricalReport


@dataclass(frozen=True)
class StoredPdf:
    report: HistoricalReport
    content: bytes
    content_type: str


def writable_url(settings: Settings) -> str:
    url = settings.sync_analytics_database_url or settings.migration_database_url
    if not url:
        raise ValueError("A writable analytics database URL is required for reports")
    return url


def list_enterprise_ids(settings: Settings) -> list[int]:
    query = """
        SELECT DISTINCT enterprise_id FROM (
            SELECT enterprise_id FROM fact_orders
            UNION SELECT enterprise_id FROM dim_products
            UNION SELECT enterprise_id FROM dim_leads
            UNION SELECT enterprise_id FROM fact_deliveries
        ) tenants ORDER BY enterprise_id
    """
    with connection(writable_url(settings), readonly=True) as conn:
        return [int(row["enterprise_id"]) for row in conn.execute(query)]


def save_report(
    settings: Settings,
    *,
    report_id: UUID,
    enterprise_id: int,
    requested_by: str | None,
    audience_role: str,
    period_type: str,
    period_start: date,
    period_end: date,
    locale: str,
    file_name: str,
    content: bytes,
    content_sha256: str,
    summary: dict[str, Any],
) -> HistoricalReport:
    query = """
        INSERT INTO analytics_reports(
            id, enterprise_id, requested_by, audience_role, period_type,
            period_start, period_end, locale, file_name, pdf_content,
            content_sha256, summary
        ) VALUES (
            %(id)s, %(enterprise_id)s, %(requested_by)s, %(audience_role)s,
            %(period_type)s, %(period_start)s, %(period_end)s, %(locale)s,
            %(file_name)s, %(content)s, %(content_sha256)s, %(summary)s::jsonb
        )
        ON CONFLICT (enterprise_id, audience_role, period_type, period_start, locale)
            WHERE requested_by IS NULL
        DO UPDATE SET id = analytics_reports.id
        RETURNING id::text, audience_role, period_type, period_start, period_end,
                  locale, generated_at, file_name, summary
    """
    params = {
        "id": report_id,
        "enterprise_id": enterprise_id,
        "requested_by": requested_by,
        "audience_role": audience_role,
        "period_type": period_type,
        "period_start": period_start,
        "period_end": period_end,
        "locale": locale,
        "file_name": file_name,
        "content": content,
        "content_sha256": content_sha256,
        "summary": json.dumps(summary),
    }
    with connection(writable_url(settings)) as conn:
        row = conn.execute(query, params).fetchone()
    return HistoricalReport(**row)


def list_reports(
    settings: Settings,
    enterprise_id: int,
    user_id: str,
    role: str,
    locale: str | None,
    period_type: str | None,
    limit: int,
) -> list[HistoricalReport]:
    predicates = ["enterprise_id = %(enterprise_id)s", "status = 'READY'"]
    params: dict[str, Any] = {"enterprise_id": enterprise_id, "limit": limit}
    if role != "ROLE_ADMIN":
        predicates.extend([
            "requested_by = %(user_id)s",
            "audience_role = %(role)s",
        ])
        params.update(user_id=user_id, role=role)
    if locale:
        predicates.append("locale = %(locale)s")
        params["locale"] = locale
    if period_type:
        predicates.append("period_type = %(period_type)s")
        params["period_type"] = period_type
    query = f"""
        SELECT id::text, audience_role, period_type, period_start, period_end,
               locale, generated_at, file_name, summary
        FROM analytics_reports
        WHERE {' AND '.join(predicates)}
        ORDER BY period_start DESC, generated_at DESC
        LIMIT %(limit)s
    """
    with connection(writable_url(settings), readonly=True) as conn:
        rows = list(conn.execute(query, params))
    return [HistoricalReport(**row) for row in rows]


def get_report(
    settings: Settings,
    report_id: UUID,
    enterprise_id: int,
    user_id: str,
    role: str,
) -> StoredPdf | None:
    visibility = ""
    params: dict[str, Any] = {"id": report_id, "enterprise_id": enterprise_id}
    if role != "ROLE_ADMIN":
        visibility = " AND requested_by = %(user_id)s AND audience_role = %(role)s"
        params.update(user_id=user_id, role=role)
    query = f"""
        SELECT id::text, audience_role, period_type, period_start, period_end,
               locale, generated_at, file_name, summary, pdf_content, content_type
        FROM analytics_reports
        WHERE id = %(id)s AND enterprise_id = %(enterprise_id)s
          AND status = 'READY'{visibility}
    """
    with connection(writable_url(settings), readonly=True) as conn:
        row = conn.execute(query, params).fetchone()
    if not row:
        return None
    content = bytes(row.pop("pdf_content"))
    content_type = row.pop("content_type")
    return StoredPdf(HistoricalReport(**row), content, content_type)
