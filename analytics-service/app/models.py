from datetime import datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, Field


class AskRequest(BaseModel):
    question: str = Field(min_length=3, max_length=1_000)


class Column(BaseModel):
    name: str
    type: Literal["string", "integer", "decimal", "currency", "date", "datetime", "boolean"]


class QueryResult(BaseModel):
    columns: list[Column]
    rows: list[dict[str, str | int | float | bool | None]]


class Visualization(BaseModel):
    type: Literal["none", "single_value", "table", "bar", "line"]
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


JsonScalar = str | int | float | bool | None | Decimal | datetime
