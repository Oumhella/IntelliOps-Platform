from datetime import UTC, datetime

from app.config import Settings
from app.executor import execute_query
from app.llm import generate_plan
from app.models import AskResponse, Column, Metadata, QueryResult, Visualization
from app.semantic import deterministic_plan, ensure_metric_allowed
from app.validator import validate_sql


async def answer_question(
    question: str,
    tenant_id: int,
    role: str,
    user_id: str,
    settings: Settings,
) -> AskResponse:
    plan = deterministic_plan(question, datetime.now(UTC), role, user_id)
    if plan is None:
        if role != "ROLE_ADMIN":
            raise PermissionError(
                "This role can use only its approved operational analytics catalogue."
            )
        plan = await generate_plan(question, settings)
    ensure_metric_allowed(plan.metric, role)
    safe_sql = validate_sql(plan.sql, settings.query_max_rows)
    rows, freshness = execute_query(
        settings.analytics_database_url,
        tenant_id,
        safe_sql,
        plan.parameters,
        settings.query_timeout_ms,
    )
    truncated = len(rows) >= settings.query_max_rows
    columns = infer_columns(rows)
    visualization = choose_visualization(plan.visualization, columns, len(rows))
    return AskResponse(
        question=question,
        answer=summarize(plan.metric, rows),
        result=QueryResult(columns=columns, rows=rows),
        visualization=visualization,
        metadata=Metadata(
            metric=plan.metric,
            data_freshness=freshness,
            truncated=truncated,
            assumptions=plan.assumptions,
        ),
    )


def infer_columns(rows: list[dict]) -> list[Column]:
    if not rows:
        return []
    result = []
    for name, value in rows[0].items():
        if isinstance(value, bool):
            kind = "boolean"
        elif isinstance(value, int):
            kind = "integer"
        elif isinstance(value, float):
            kind = (
                "currency"
                if any(word in name for word in ("revenue", "amount", "price"))
                else "decimal"
            )
        elif isinstance(value, str) and "T" in value:
            kind = "datetime"
        else:
            kind = "string"
        result.append(Column(name=name, type=kind))
    return result


def choose_visualization(requested: str, columns: list[Column], row_count: int) -> Visualization:
    if not columns or row_count == 0:
        return Visualization(type="none")
    if row_count == 1 and len(columns) == 1:
        return Visualization(type="single_value", y=columns[0].name)
    numeric = [
        column.name for column in columns if column.type in {"integer", "decimal", "currency"}
    ]
    labels = [column.name for column in columns if column.name not in numeric]
    if requested in {"bar", "line", "donut"} and numeric and labels:
        return Visualization(type=requested, x=labels[0], y=numeric[0])
    return Visualization(type="table")


def summarize(metric: str, rows: list[dict]) -> str:
    if not rows:
        return "No matching data was found for this question."
    if len(rows) == 1 and len(rows[0]) == 1:
        label, value = next(iter(rows[0].items()))
        return f"{label.replace('_', ' ').capitalize()}: {value}."
    leader = rows[0]
    details = ", ".join(f"{key.replace('_', ' ')}: {value}" for key, value in leader.items())
    return f"The query returned {len(rows)} result(s). The first is {details}."
