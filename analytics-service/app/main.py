import csv
import io
import logging
import time
import uuid
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, Response

from app.auth import Principal, authenticated_principal
from app.config import Settings, get_settings
from app.history import add_message, clear_messages, list_messages
from app.models import (
    AskRequest,
    AskResponse,
    ConversationMessage,
    ConversationMessageCreate,
    SuggestionsResponse,
)
from app.semantic import suggestions_for_role
from app.service import answer_question

LOGGER = logging.getLogger("analytics")
app = FastAPI(title="IntelliOps Analytics Service", version="0.2.0")


@app.middleware("http")
async def request_context(request: Request, call_next):
    request_id = request.headers.get("X-Request-Id") or str(uuid.uuid4())
    started = time.monotonic()
    response = await call_next(request)
    response.headers["X-Request-Id"] = request_id
    LOGGER.info(
        "analytics_request request_id=%s path=%s status=%s duration_ms=%d",
        request_id,
        request.url.path,
        response.status_code,
        (time.monotonic() - started) * 1000,
    )
    return response


@app.exception_handler(ValueError)
async def value_error_handler(_request: Request, exc: ValueError) -> JSONResponse:
    return JSONResponse(status_code=422, content={"detail": str(exc)})


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/api/v1/analytics/suggestions")
def suggestions(
    principal: Annotated[Principal, Depends(authenticated_principal)],
) -> SuggestionsResponse:
    return SuggestionsResponse(
        role=principal.role,
        suggestions=suggestions_for_role(principal.role),
    )


@app.post("/api/v1/analytics/ask", response_model=AskResponse)
async def ask(
    request: AskRequest,
    principal: Annotated[Principal, Depends(authenticated_principal)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> AskResponse:
    try:
        return await answer_question(
            request.question.strip(),
            principal.enterprise_id,
            principal.role,
            principal.user_id,
            settings,
            request.locale,
        )
    except HTTPException:
        raise
    except ValueError:
        raise
    except PermissionError as exc:
        raise HTTPException(403, str(exc)) from exc
    except Exception as exc:
        LOGGER.exception(
            "analytics_question_failed user_id=%s enterprise_id=%s",
            principal.user_id,
            principal.enterprise_id,
        )
        raise HTTPException(502, "Analytics query could not be completed") from exc


@app.post("/api/v1/analytics/reports/csv")
async def export_csv_report(
    request: AskRequest,
    principal: Annotated[Principal, Depends(authenticated_principal)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> Response:
    try:
        answer = await answer_question(
            request.question.strip(),
            principal.enterprise_id,
            principal.role,
            principal.user_id,
            settings,
            request.locale,
        )
    except PermissionError as exc:
        raise HTTPException(403, str(exc)) from exc
    except ValueError:
        raise
    except Exception as exc:
        LOGGER.exception("analytics_report_failed enterprise_id=%s", principal.enterprise_id)
        raise HTTPException(502, "Analytics report could not be generated") from exc

    output = io.StringIO()
    writer = csv.writer(output)
    writer.writerow(["IntelliOps analytics report"])
    writer.writerow(["Question", answer.question])
    writer.writerow(["Metric", answer.metadata.metric])
    writer.writerow(["Generated for role", principal.role])
    writer.writerow(["Data freshness", answer.metadata.data_freshness or "not available"])
    writer.writerow([])
    writer.writerow([column.name for column in answer.result.columns])
    for row in answer.result.rows:
        writer.writerow([row.get(column.name) for column in answer.result.columns])
    filename = f"intelliops-{answer.metadata.metric}.csv"
    return Response(
        output.getvalue(),
        media_type="text/csv; charset=utf-8",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )

@app.get(
    "/api/v1/analytics/conversations/{surface}",
    response_model=list[ConversationMessage],
)
def history(
    surface: str,
    principal: Annotated[Principal, Depends(authenticated_principal)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> list[ConversationMessage]:
    surface = surface.upper()
    if surface not in {"ASSISTANT", "BI"}:
        raise HTTPException(400, "Invalid conversation surface")
    return list_messages(settings, principal.enterprise_id, principal.user_id, surface)


@app.post(
    "/api/v1/analytics/conversations",
    response_model=ConversationMessage,
    status_code=201,
)
def store(
    item: ConversationMessageCreate,
    principal: Annotated[Principal, Depends(authenticated_principal)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> ConversationMessage:
    return add_message(settings, principal.enterprise_id, principal.user_id, item)


@app.delete("/api/v1/analytics/conversations/{surface}", status_code=204)
def clear(
    surface: str,
    principal: Annotated[Principal, Depends(authenticated_principal)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> None:
    surface = surface.upper()
    if surface not in {"ASSISTANT", "BI"}:
        raise HTTPException(400, "Invalid conversation surface")
    clear_messages(settings, principal.enterprise_id, principal.user_id, surface)
