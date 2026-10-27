from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    app_name: str = "AI 编程助手 AI 服务"
    host: str = "0.0.0.0"
    port: int = 8001
    redis_url: str = "redis://localhost:6379/1"
    database_url: str = "postgresql://postgres:postgres@localhost:5432/aicoding"
    model_provider: str = "mock"
    model_base_url: str = ""
    model_api_key: str = ""
    model_name: str = "gpt-4o-mini"
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

@lru_cache
def get_settings() -> Settings:
    return Settings()

