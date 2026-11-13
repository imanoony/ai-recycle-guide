from functools import lru_cache

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    gemini_api_key: SecretStr | None = None
    gemini_base_url: str = "https://generativelanguage.googleapis.com/v1beta"
    gemini_model: str = "gemini-3.8-flash"
    gemini_timeout_seconds: float = Field(default=60.0, gt=0, le=180)
    gemini_max_retries: int = Field(default=1, ge=0, le=2)


@lru_cache
def get_settings() -> Settings:
    return Settings()