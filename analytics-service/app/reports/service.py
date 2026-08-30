import hashlib
from datetime import datetime
from uuid import uuid4

from app.config import Settings
from app.models import HistoricalReport
from app.reports.metrics import collect_report_data
from app.reports.pdf import build_pdf
from app.reports.periods import PeriodType, last_closed_period
from app.reports.repository import save_report


def generate_report(
    settings: Settings,
    *,
    enterprise_id: int,
    user_id: str | None,
    role: str,
    period_type: PeriodType,
    locale: str,
    now: datetime | None = None,
) -> HistoricalReport:
    period = last_closed_period(period_type, now)
    actor = user_id or ""
    data = collect_report_data(settings, enterprise_id, role, actor, period)
    pdf, advice = build_pdf(enterprise_id, role, period, locale, data)
    report_id = uuid4()
    cadence = "weekly" if period_type == "WEEKLY" else "monthly"
    file_name = f"intelliops-{cadence}-{period.start.isoformat()}-{locale}.pdf"
    summary = {
        "orders": data.current["orders"],
        "paid_revenue": data.current["paid_revenue"] if role == "ROLE_ADMIN" else 0,
        "average_order_value": (
            data.current["average_order_value"] if role == "ROLE_ADMIN" else 0
        ),
        "delivered": data.current["delivered"],
        "low_stock_items": (
            data.current["low_stock_items"]
            if role in {"ROLE_ADMIN", "ROLE_LOGISTIC"}
            else 0
        ),
        "recommendations": advice,
    }
    return save_report(
        settings,
        report_id=report_id,
        enterprise_id=enterprise_id,
        requested_by=user_id,
        audience_role=role,
        period_type=period_type,
        period_start=period.start,
        period_end=period.end,
        locale=locale,
        file_name=file_name,
        content=pdf,
        content_sha256=hashlib.sha256(pdf).hexdigest(),
        summary=summary,
    )
