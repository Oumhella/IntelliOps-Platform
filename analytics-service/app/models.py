from datetime import date, datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, Field


class AskRequest(BaseModel):
    question: str = Field(min_length=3, max_length=1_000)
    locale: Literal["en", "fr", "ar"] = "en"


class Column(BaseModel):
    name: str
    type: Literal["string", "integer", "decimal", "currency", "date", "datetime", "boolean"]


class QueryResult(BaseModel):
    columns: list[Column]
    rows: list[dict[str, str | int | float | bool | None]]


class Visualization(BaseModel):
    type: Literal["none", "single_value", "table", "bar", "line", "donut"]
    title: str | None = None
    x: str | None = None
    y: str | None = None


class Metadata(BaseModel):
    metric: str
    data_freshness: datetime | None = None
    truncated: bool = False
    assumptions: list[str] = []


class AskResponse(BaseModel):
    question: str
    answer: str
    result: QueryResult
    visualization: Visualization
    metadata: Metadata


class SuggestionsResponse(BaseModel):
    role: str
    suggestions: list[str]


class ReportGenerateRequest(BaseModel):
    period_type: Literal["WEEKLY", "MONTHLY"] = "WEEKLY"
    locale: Literal["en", "fr", "ar"] = "en"


class ReportSummary(BaseModel):
    orders: int = 0
    paid_revenue: float = 0
    average_order_value: float = 0
    delivered: int = 0
    low_stock_items: int = 0
    recommendations: list[str] = []


class HistoricalReport(BaseModel):
    id: str
    audience_role: str
    period_type: Literal["WEEKLY", "MONTHLY"]
    period_start: date
    period_end: date
    locale: Literal["en", "fr", "ar"]
    generated_at: datetime
    file_name: str
    summary: ReportSummary

class ConversationMessageCreate(BaseModel):
    surface: Literal["ASSISTANT", "BI"]
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=12_000)
    payload: dict | None = None

class ConversationMessage(ConversationMessageCreate):
    id: int
    created_at: datetime


JsonScalar = str | int | float | bool | None | Decimal | datetime
