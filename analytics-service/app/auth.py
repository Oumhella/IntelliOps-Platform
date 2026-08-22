from dataclasses import dataclass
from typing import Annotated

import jwt
from fastapi import Depends, Header, HTTPException, status

from app.config import Settings, get_settings


@dataclass(frozen=True)
class Principal:
    user_id: str
    enterprise_id: int
    role: str


def authenticated_principal(
    authorization: Annotated[str | None, Header()] = None,
    forwarded_enterprise: Annotated[str | None, Header(alias="X-Enterprise-Id")] = None,
    settings: Annotated[Settings, Depends(get_settings)] = None,
) -> Principal:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Bearer token required")
    try:
        claims = jwt.decode(authorization[7:], settings.jwt_secret, algorithms=["HS256"])
        enterprise_id = int(claims["enterpriseId"])
        role = str(claims["role"])
        user_id = str(claims.get("userId") or claims.get("sub") or "")
    except (jwt.PyJWTError, KeyError, TypeError, ValueError) as exc:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or expired token") from exc
    if role not in settings.allowed_role_set:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Role is not allowed to use analytics")
    if forwarded_enterprise and forwarded_enterprise != str(enterprise_id):
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED, "Gateway tenant header does not match JWT"
        )
    return Principal(user_id=user_id, enterprise_id=enterprise_id, role=role)
