from datetime import UTC, datetime

import pytest

from app.reports.metrics import ReportData
from app.reports.pdf import build_pdf, recommendations
from app.reports.periods import last_closed_period


def sample_data(**overrides) -> ReportData:
    current = {
        "orders": 20,
        "paid_orders": 16,
        "paid_revenue": 3200.0,
        "average_order_value": 200.0,
        "exceptions": 3,
        "deliveries": 10,
        "delivered": 7,
        "low_stock_items": 2,
        "products": 8,
        "leads": 12,
        **overrides,
    }
    return ReportData(
        current=current,
        previous={
            "orders": 30,
            "paid_orders": 25,
            "paid_revenue": 5000.0,
            "average_order_value": 200.0,
            "exceptions": 1,
            "deliveries": 12,
            "delivered": 11,
        },
        order_statuses=[{"status": "CONFIRMEE", "value": 12}, {"status": "ANNULEE", "value": 3}],
        delivery_statuses=[{"status": "LIVREE", "value": 7}, {"status": "ECHEC", "value": 3}],
        top_products=[{"label": "Snowboard", "value": 1800.0}],
        daily_revenue=[{"label": "2026-08-17", "value": 900.0}],
        lead_statuses=[{"status": "QUALIFIE", "value": 5}],
        low_stock=[{
            "product": "Snowboard", "location": "Warehouse",
            "available_quantity": 2, "alert_threshold": 5,
        }],
        freshness="2026-08-24T01:00:00+00:00",
    )


def test_closed_week_and_month_are_stable() -> None:
    now = datetime(2026, 8, 28, 12, tzinfo=UTC)
    week = last_closed_period("WEEKLY", now)
    month = last_closed_period("MONTHLY", now)
    assert (str(week.start), str(week.end)) == ("2026-08-17", "2026-08-24")
    assert (str(month.start), str(month.end)) == ("2026-07-01", "2026-08-01")


def test_recommendations_are_evidence_based_and_localized() -> None:
    french = recommendations(sample_data(), "fr", "ROLE_ADMIN")
    assert any("diminué" in item for item in french)
    assert any("seuil d’alerte" in item for item in french)
    assert len(french) <= 5


def test_csm_recommendations_do_not_leak_stock_or_delivery_scope() -> None:
    csm = recommendations(sample_data(orders=5), "en", "ROLE_CSM")
    assert not any("stock" in item.lower() for item in csm)
    assert not any("delivery completion" in item.lower() for item in csm)


@pytest.mark.parametrize("locale", ["en", "fr", "ar"])
def test_pdf_is_rendered_for_each_supported_locale(locale: str) -> None:
    period = last_closed_period("WEEKLY", datetime(2026, 8, 28, tzinfo=UTC))
    content, advice = build_pdf(1, "ROLE_ADMIN", period, locale, sample_data())
    assert content.startswith(b"%PDF-")
    assert len(content) > 5_000
    assert advice
