import logging
import time
import uuid
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

from app.auth import Principal, authenticated_principal
from app.config import Settings, get_settings
from app.models import AskRequest, AskResponse
from app.semantic import SUGGESTIONS
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
    _principal: Annotated[Principal, Depends(authenticated_principal)],
) -> dict[str, list[str]]:
    return {"suggestions": SUGGESTIONS}


@app.post("/api/v1/analytics/ask", response_model=AskResponse)
async def ask(
    request: AskRequest,
    principal: Annotated[Principal, Depends(authenticated_principal)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> AskResponse:
    try:
        return await answer_question(request.question.strip(), principal.enterprise_id, settings)
    except HTTPException:
        raise
    except ValueError:
        raise
    except Exception as exc:
        LOGGER.exception(
            "analytics_question_failed user_id=%s enterprise_id=%s",
            principal.user_id,
            principal.enterprise_id,
        )
        raise HTTPException(502, "Analytics query could not be completed") from exc
