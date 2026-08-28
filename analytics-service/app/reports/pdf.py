# ruff: noqa: E501

import io
from pathlib import Path
from typing import Any

import arabic_reshaper
from bidi.algorithm import get_display
from reportlab.graphics.shapes import Drawing, Rect, String
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

from app.reports.metrics import ReportData
from app.reports.periods import ReportPeriod

INK = colors.HexColor("#14213d")
INDIGO = colors.HexColor("#4f46e5")
CYAN = colors.HexColor("#06b6d4")
AMBER = colors.HexColor("#f59e0b")
MUTED = colors.HexColor("#64748b")
PANEL = colors.HexColor("#f4f7fb")
FONT_NAME = "DejaVuSans"


TEXT = {
    "en": {
        "title": "IntelliOps business performance report",
        "weekly": "Weekly operational review",
        "monthly": "Monthly executive review",
        "period": "Reporting period",
        "generated": "Generated from tenant-isolated ERP snapshots",
        "overview": "Performance overview",
        "orders": "Orders",
        "revenue": "Recorded paid revenue",
        "average": "Average paid order value",
        "delivered": "Delivered",
        "stock": "Low-stock items",
        "leads": "Current leads",
        "orders_chart": "Orders by status",
        "delivery_chart": "Current deliveries by status",
        "leads_chart": "Current leads by status",
        "stock_details": "Low-stock action list",
        "product": "Product", "location": "Location", "available": "Available", "threshold": "Threshold",
        "products_chart": "Top products by recorded revenue",
        "trend_chart": "Daily paid-revenue trend",
        "recommendations": "Evidence-based recommendations",
        "data_notes": "Data scope and quality",
        "freshness": "Latest synchronized source data",
        "closed_period": "This immutable report covers a closed period; later ERP changes do not rewrite it.",
        "empty": "No matching activity was recorded for this period.",
    },
    "fr": {
        "title": "Rapport de performance métier IntelliOps",
        "weekly": "Revue opérationnelle hebdomadaire",
        "monthly": "Revue exécutive mensuelle",
        "period": "Période du rapport",
        "generated": "Généré à partir d’instantanés ERP isolés par entreprise",
        "overview": "Vue d’ensemble des performances",
        "orders": "Commandes",
        "revenue": "Chiffre d’affaires payé enregistré",
        "average": "Valeur moyenne des commandes payées",
        "delivered": "Livraisons réussies",
        "stock": "Articles en stock faible",
        "leads": "Prospects actuels",
        "orders_chart": "Commandes par statut",
        "delivery_chart": "Livraisons actuelles par statut",
        "leads_chart": "Prospects actuels par statut",
        "stock_details": "Liste d’action des stocks faibles",
        "product": "Produit", "location": "Lieu", "available": "Disponible", "threshold": "Seuil",
        "products_chart": "Meilleurs produits par chiffre d’affaires enregistré",
        "trend_chart": "Évolution quotidienne du chiffre d’affaires payé",
        "recommendations": "Recommandations fondées sur les données",
        "data_notes": "Périmètre et qualité des données",
        "freshness": "Dernière synchronisation des sources",
        "closed_period": "Ce rapport immuable couvre une période clôturée ; les modifications ERP ultérieures ne le réécrivent pas.",
        "empty": "Aucune activité correspondante n’a été enregistrée pendant cette période.",
    },
    "ar": {
        "title": "تقرير أداء الأعمال من IntelliOps",
        "weekly": "المراجعة التشغيلية الأسبوعية",
        "monthly": "المراجعة التنفيذية الشهرية",
        "period": "فترة التقرير",
        "generated": "تم إنشاؤه من لقطات بيانات ERP المعزولة حسب المؤسسة",
        "overview": "نظرة عامة على الأداء",
        "orders": "الطلبات",
        "revenue": "الإيرادات المدفوعة المسجلة",
        "average": "متوسط قيمة الطلب المدفوع",
        "delivered": "عمليات التسليم الناجحة",
        "stock": "عناصر منخفضة المخزون",
        "leads": "العملاء المحتملون الحاليون",
        "orders_chart": "الطلبات حسب الحالة",
        "delivery_chart": "عمليات التسليم الحالية حسب الحالة",
        "leads_chart": "العملاء المحتملون الحاليون حسب الحالة",
        "stock_details": "قائمة إجراءات المخزون المنخفض",
        "product": "المنتج", "location": "الموقع", "available": "المتاح", "threshold": "الحد",
        "products_chart": "أفضل المنتجات حسب الإيرادات المسجلة",
        "trend_chart": "اتجاه الإيرادات المدفوعة اليومي",
        "recommendations": "توصيات مبنية على البيانات",
        "data_notes": "نطاق البيانات وجودتها",
        "freshness": "آخر مزامنة لبيانات المصدر",
        "closed_period": "يغطي هذا التقرير الثابت فترة مغلقة، ولا تعيد تغييرات ERP اللاحقة كتابته.",
        "empty": "لم يتم تسجيل نشاط مطابق خلال هذه الفترة.",
    },
}


def _register_font() -> None:
    if FONT_NAME in pdfmetrics.getRegisteredFontNames():
        return
    candidates = [
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
        Path("C:/Windows/Fonts/DejaVu.ttf"),
        Path("C:/Windows/Fonts/arial.ttf"),
    ]
    font_path = next((path for path in candidates if path.exists()), None)
    if not font_path:
        raise RuntimeError("A Unicode font is required to generate localized PDF reports")
    pdfmetrics.registerFont(TTFont(FONT_NAME, str(font_path)))


def _display(value: object, locale: str) -> str:
    text = str(value)
    return get_display(arabic_reshaper.reshape(text)) if locale == "ar" else text


def _change(current: float, previous: float) -> float | None:
    if previous == 0:
        return None
    return round((current - previous) / previous * 100, 1)


def recommendations(data: ReportData, locale: str, role: str) -> list[str]:
    current = data.current
    previous = data.previous
    messages: list[tuple[str, str, str]] = []
    if current["orders"] == 0:
        messages.append((
            "No orders were recorded. Verify store synchronization and acquisition activity.",
            "Aucune commande n’a été enregistrée. Vérifiez la synchronisation des boutiques et l’activité d’acquisition.",
            "لم تُسجل أي طلبات. تحقق من مزامنة المتاجر ونشاط الاستحواذ.",
        ))
    order_delta = _change(current["orders"], previous["orders"])
    if order_delta is not None and order_delta <= -10:
        messages.append((
            f"Orders decreased by {abs(order_delta):.1f}% versus the previous period; review lead and store conversion funnels.",
            f"Les commandes ont diminué de {abs(order_delta):.1f} % par rapport à la période précédente ; analysez les parcours de conversion des prospects et des boutiques.",
            f"انخفضت الطلبات بنسبة {abs(order_delta):.1f}٪ مقارنة بالفترة السابقة؛ راجع مسارات تحويل العملاء المحتملين والمتاجر.",
        ))
    revenue_delta = _change(current["paid_revenue"], previous["paid_revenue"])
    if role == "ROLE_ADMIN" and revenue_delta is not None and revenue_delta <= -10:
        messages.append((
            f"Recorded paid revenue decreased by {abs(revenue_delta):.1f}% versus the previous period; review order volume, product mix, and payment completion.",
            f"Le chiffre d’affaires payé enregistré a diminué de {abs(revenue_delta):.1f} % ; analysez le volume de commandes, le mix produit et la finalisation des paiements.",
            f"انخفضت الإيرادات المدفوعة المسجلة بنسبة {abs(revenue_delta):.1f}٪؛ راجع حجم الطلبات ومزيج المنتجات وإتمام الدفع.",
        ))
    if current["orders"] and current["exceptions"] / current["orders"] >= 0.1:
        rate = current["exceptions"] / current["orders"] * 100
        messages.append((
            f"Cancelled or returned orders represent {rate:.1f}% of activity; inspect the dominant reasons and affected products.",
            f"Les commandes annulées ou retournées représentent {rate:.1f} % de l’activité ; analysez les motifs dominants et les produits concernés.",
            f"تمثل الطلبات الملغاة أو المرتجعة {rate:.1f}٪ من النشاط؛ حلّل الأسباب الرئيسية والمنتجات المتأثرة.",
        ))
    if (
        role in {"ROLE_ADMIN", "ROLE_LOGISTIC"}
        and current["deliveries"]
        and current["delivered"] / current["deliveries"] < 0.85
    ):
        rate = current["delivered"] / current["deliveries"] * 100
        messages.append((
            f"The observed delivery completion rate is {rate:.1f}%; prioritize stalled deliveries and validate addresses before dispatch.",
            f"Le taux de livraison observé est de {rate:.1f} % ; priorisez les livraisons bloquées et validez les adresses avant expédition.",
            f"بلغ معدل إتمام التسليم المرصود {rate:.1f}٪؛ أعط الأولوية للشحنات المتوقفة وتحقق من العناوين قبل الإرسال.",
        ))
    if role in {"ROLE_ADMIN", "ROLE_LOGISTIC"} and current["low_stock_items"]:
        messages.append((
            f"{current['low_stock_items']} item(s) are at or below their alert threshold; review replenishment using actual demand and supplier lead time.",
            f"{current['low_stock_items']} article(s) ont atteint leur seuil d’alerte ; planifiez le réapprovisionnement selon la demande réelle et le délai fournisseur.",
            f"وصل {current['low_stock_items']} عنصرًا إلى حد التنبيه؛ خطط للتزويد وفق الطلب الفعلي ومدة توريد المورد.",
        ))
    if role == "ROLE_CSM" and current["leads"] and current["orders"] == 0:
        messages.append((
            "Assigned leads produced no orders in this period; prioritize qualified leads with overdue follow-up.",
            "Les prospects attribués n’ont généré aucune commande ; priorisez les prospects qualifiés dont le suivi est en retard.",
            "لم ينتج العملاء المحتملون المسندون أي طلبات؛ أعط الأولوية للعملاء المؤهلين المتأخرين في المتابعة.",
        ))
    if not messages:
        messages.append((
            "No critical threshold was triggered. Maintain the current cadence and monitor period-over-period movement.",
            "Aucun seuil critique n’a été déclenché. Maintenez le rythme actuel et surveillez l’évolution entre les périodes.",
            "لم يتم تجاوز أي حد حرج. حافظ على الوتيرة الحالية وراقب التغير بين الفترات.",
        ))
    index = {"en": 0, "fr": 1, "ar": 2}[locale]
    return [message[index] for message in messages[:5]]


def _bar_chart(rows: list[dict[str, Any]], locale: str, width: float = 170 * mm) -> Drawing:
    height = max(30 * mm, (len(rows) * 9 + 10) * mm)
    drawing = Drawing(width, height)
    if not rows:
        return drawing
    maximum = max(float(row.get("value") or 0) for row in rows) or 1
    colorset = [INDIGO, CYAN, AMBER]
    for index, row in enumerate(rows[:8]):
        y = height - (index + 1) * 9 * mm
        label = _display(str(row.get("label") or row.get("status") or "")[:28], locale)
        value = float(row.get("value") or 0)
        drawing.add(String(0, y + 2, label, fontName=FONT_NAME, fontSize=7, fillColor=INK))
        drawing.add(Rect(52 * mm, y, (value / maximum) * 92 * mm, 5 * mm,
                         fillColor=colorset[index % len(colorset)], strokeColor=None))
        drawing.add(String(147 * mm, y + 2, f"{value:,.2f}".rstrip("0").rstrip("."),
                           fontName=FONT_NAME, fontSize=7, fillColor=INK))
    return drawing


def _with_delta(value: int | float, previous: int | float, decimals: int = 0) -> str:
    formatted = f"{value:,.{decimals}f}"
    delta = _change(float(value), float(previous))
    if delta is None:
        return formatted
    arrow = "▲" if delta >= 0 else "▼"
    return f"{formatted}  {arrow} {abs(delta):.1f}%"


def build_pdf(
    enterprise_id: int,
    role: str,
    period: ReportPeriod,
    locale: str,
    data: ReportData,
) -> tuple[bytes, list[str]]:
    _register_font()
    strings = TEXT[locale]
    rtl = locale == "ar"
    alignment = TA_RIGHT if rtl else TA_LEFT
    styles = getSampleStyleSheet()
    title = ParagraphStyle(
        "ReportTitle", parent=styles["Title"], fontName=FONT_NAME,
        fontSize=22, leading=28, textColor=INK, alignment=alignment,
    )
    heading = ParagraphStyle(
        "ReportHeading", parent=styles["Heading2"], fontName=FONT_NAME,
        fontSize=13, leading=18, textColor=INDIGO, alignment=alignment,
        spaceBefore=8 * mm, spaceAfter=3 * mm,
    )
    body = ParagraphStyle(
        "ReportBody", parent=styles["BodyText"], fontName=FONT_NAME,
        fontSize=9, leading=14, textColor=INK, alignment=alignment,
    )
    small = ParagraphStyle(
        "ReportSmall", parent=body, fontSize=7.5, leading=11, textColor=MUTED,
    )
    output = io.BytesIO()
    document = SimpleDocTemplate(
        output, pagesize=A4, rightMargin=18 * mm, leftMargin=18 * mm,
        topMargin=16 * mm, bottomMargin=16 * mm,
        title=strings["title"], author="IntelliOps",
    )
    story: list[Any] = [
        Paragraph(_display(strings["title"], locale), title),
        Spacer(1, 2 * mm),
        Paragraph(_display(strings["weekly"] if period.kind == "WEEKLY" else strings["monthly"], locale), heading),
        Paragraph(_display(
            f"{strings['period']}: {period.start.isoformat()} — {(period.end.fromordinal(period.end.toordinal() - 1)).isoformat()}",
            locale,
        ), body),
        Paragraph(_display(f"Enterprise #{enterprise_id} · {role.removeprefix('ROLE_')}", locale), small),
        Paragraph(_display(strings["generated"], locale), small),
        Paragraph(_display(strings["overview"], locale), heading),
    ]
    kpis = [
        (strings["orders"], _with_delta(data.current["orders"], data.previous["orders"])),
        (strings["delivered"], _with_delta(data.current["delivered"], data.previous["delivered"])),
    ]
    if role == "ROLE_ADMIN":
        kpis.extend([
            (strings["revenue"], _with_delta(
                data.current["paid_revenue"], data.previous["paid_revenue"], 2
            )),
            (strings["average"], _with_delta(
                data.current["average_order_value"], data.previous["average_order_value"], 2
            )),
        ])
    if role in {"ROLE_ADMIN", "ROLE_LOGISTIC"}:
        kpis.append((strings["stock"], data.current["low_stock_items"]))
    if role in {"ROLE_ADMIN", "ROLE_CSM"}:
        kpis.append((strings["leads"], data.current["leads"]))
    cells = [[Paragraph(_display(label, locale), small), Paragraph(_display(value, locale), body)] for label, value in kpis]
    table = Table(cells, colWidths=[85 * mm, 75 * mm], hAlign="RIGHT" if rtl else "LEFT")
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), PANEL),
        ("BOX", (0, 0), (-1, -1), 0.4, colors.HexColor("#dbe3ee")),
        ("INNERGRID", (0, 0), (-1, -1), 0.3, colors.HexColor("#dbe3ee")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
    ]))
    story.extend([table, Paragraph(_display(strings["orders_chart"], locale), heading)])
    story.append(_bar_chart(data.order_statuses, locale))
    if role in {"ROLE_ADMIN", "ROLE_LOGISTIC"}:
        story.extend([
            Paragraph(_display(strings["delivery_chart"], locale), heading),
            _bar_chart(data.delivery_statuses, locale),
        ])
    if role in {"ROLE_ADMIN", "ROLE_CSM"}:
        story.extend([
            Paragraph(_display(strings["leads_chart"], locale), heading),
            _bar_chart(data.lead_statuses, locale),
        ])
    if role in {"ROLE_ADMIN", "ROLE_LOGISTIC"} and data.low_stock:
        stock_rows = [[
            Paragraph(_display(strings["product"], locale), small),
            Paragraph(_display(strings["location"], locale), small),
            Paragraph(_display(strings["available"], locale), small),
            Paragraph(_display(strings["threshold"], locale), small),
        ]]
        stock_rows.extend([
            [
                Paragraph(_display(row["product"], locale), small),
                Paragraph(_display(row["location"], locale), small),
                Paragraph(_display(row["available_quantity"], locale), small),
                Paragraph(_display(row["alert_threshold"], locale), small),
            ]
            for row in data.low_stock
        ])
        stock_table = Table(stock_rows, colWidths=[58 * mm, 48 * mm, 27 * mm, 27 * mm])
        stock_table.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#e0e7ff")),
            ("GRID", (0, 0), (-1, -1), 0.3, colors.HexColor("#dbe3ee")),
            ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ("LEFTPADDING", (0, 0), (-1, -1), 5),
            ("RIGHTPADDING", (0, 0), (-1, -1), 5),
            ("TOPPADDING", (0, 0), (-1, -1), 5),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ]))
        story.extend([
            Paragraph(_display(strings["stock_details"], locale), heading),
            stock_table,
        ])
    if role == "ROLE_ADMIN" and data.top_products:
        story.extend([
            PageBreak(),
            Paragraph(_display(strings["products_chart"], locale), heading),
            _bar_chart(data.top_products, locale),
        ])
    if role == "ROLE_ADMIN" and data.daily_revenue:
        story.extend([
            Paragraph(_display(strings["trend_chart"], locale), heading),
            _bar_chart(data.daily_revenue, locale),
        ])
    advice = recommendations(data, locale, role)
    story.append(Paragraph(_display(strings["recommendations"], locale), heading))
    for index, item in enumerate(advice, 1):
        story.append(Paragraph(_display(f"{index}. {item}", locale), body))
        story.append(Spacer(1, 1.5 * mm))
    story.extend([
        Paragraph(_display(strings["data_notes"], locale), heading),
        Paragraph(_display(strings["closed_period"], locale), body),
        Paragraph(_display(f"{strings['freshness']}: {data.freshness or '—'}", locale), small),
    ])
    if not data.current["orders"]:
        story.append(Paragraph(_display(strings["empty"], locale), small))
    document.build(story)
    return output.getvalue(), advice
