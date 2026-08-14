from functools import lru_cache

from pydantic import Field, PositiveInt
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    analytics_database_url: str
    migration_database_url: str = ""
    sync_analytics_database_url: str = ""
    analytics_query_password: str = ""
    analytics_sync_password: str = ""
    lead_database_url: str = ""
    stock_database_url: str = ""
    sync_batch_size: PositiveInt = Field(default=100, le=5_000)
    sync_interval_seconds: PositiveInt = Field(default=300, le=86_400)
    jwt_secret: str
    llm_api_key: str = ""
    llm_base_url: str = "https://api.openai.com/v1"
    llm_model: str = "gpt-4o-mini"
    query_timeout_ms: PositiveInt = Field(default=5_000, le=30_000)
    query_max_rows: PositiveInt = Field(default=50, le=500)
    allowed_roles: str = "ROLE_ADMIN"

    @property
    def allowed_role_set(self) -> set[str]:
        return {role.strip() for role in self.allowed_roles.split(",") if role.strip()}


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]
