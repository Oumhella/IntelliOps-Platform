from datetime import UTC, datetime, timedelta

import jwt
import pytest
from fastapi import HTTPException

from app.auth import authenticated_principal
from app.config import Settings


def settings() -> Settings:
    return Settings(analytics_database_url="postgresql://unused", jwt_secret="x" * 32)


def test_authentication_derives_tenant_from_signed_claims() -> None:
    token = jwt.encode(
        {
            "sub": "a@b.test",
            "userId": 9,
            "enterpriseId": 42,
            "role": "ROLE_ADMIN",
            "exp": datetime.now(UTC) + timedelta(minutes=5),
        },
        "x" * 32,
        algorithm="HS256",
    )
    principal = authenticated_principal(f"Bearer {token}", "42", settings())
    assert principal.enterprise_id == 42
    assert principal.user_id == "9"


def test_authentication_rejects_forged_gateway_tenant() -> None:
    token = jwt.encode(
        {"sub": "a@b.test", "enterpriseId": 42, "role": "ROLE_ADMIN"}, "x" * 32, algorithm="HS256"
    )
    with pytest.raises(HTTPException) as error:
        authenticated_principal(f"Bearer {token}", "99", settings())
    assert error.value.status_code == 401


def test_authentication_rejects_non_admin_business_role() -> None:
    token = jwt.encode(
        {
            "sub": "csm@example.test",
            "userId": 12,
            "enterpriseId": 42,
            "role": "ROLE_CSM",
            "exp": datetime.now(UTC) + timedelta(minutes=5),
        },
        "x" * 32,
        algorithm="HS256",
    )
    with pytest.raises(HTTPException) as error:
        authenticated_principal(f"Bearer {token}", "42", settings())
    assert error.value.status_code == 403
