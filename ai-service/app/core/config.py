"""Configuration lue depuis les variables d'environnement.

Ordre de priorité (du plus fort au plus faible) : variables d'environnement du processus,
``ai-service/.env``, ``.env`` à la racine du dépôt (partagé avec Spring Boot), valeurs par défaut.
"""

from functools import lru_cache
from pathlib import Path
from urllib.parse import urlparse

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

SERVICE_DIR = Path(__file__).resolve().parents[2]
REPOSITORY_DIR = SERVICE_DIR.parent

MIN_API_KEY_LENGTH = 16
DEFAULT_PORT = 8000


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(REPOSITORY_DIR / ".env", SERVICE_DIR / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # Clé partagée avec Spring Boot (en-tête X-Internal-Api-Key). Obligatoire : pas de valeur par défaut.
    ai_internal_api_key: SecretStr
    ai_service_url: str = "http://127.0.0.1:8000"
    # 127.0.0.1 par défaut : le service n'est pas exposé sur le réseau.
    ai_service_host: str = "127.0.0.1"
    # Si absent, le port est déduit de AI_SERVICE_URL (même valeur que celle utilisée par Spring Boot).
    ai_service_port: int | None = Field(default=None, ge=1, le=65535)
    ai_log_level: str = "info"
    # Swagger UI / OpenAPI désactivés par défaut (service interne).
    ai_docs_enabled: bool = False

    @field_validator("ai_internal_api_key")
    @classmethod
    def _check_api_key(cls, value: SecretStr) -> SecretStr:
        key = value.get_secret_value().strip()
        if len(key) < MIN_API_KEY_LENGTH:
            raise ValueError(f"AI_INTERNAL_API_KEY doit contenir au moins {MIN_API_KEY_LENGTH} caractères")
        if key.lower().startswith("change_me"):
            raise ValueError("AI_INTERNAL_API_KEY contient encore la valeur d'exemple de .env.example")
        return SecretStr(key)

    @field_validator("ai_log_level")
    @classmethod
    def _check_log_level(cls, value: str) -> str:
        level = value.strip().lower()
        if level not in {"critical", "error", "warning", "info", "debug"}:
            raise ValueError("AI_LOG_LEVEL doit valoir critical, error, warning, info ou debug")
        return level

    @property
    def port(self) -> int:
        if self.ai_service_port is not None:
            return self.ai_service_port
        return urlparse(self.ai_service_url).port or DEFAULT_PORT


@lru_cache
def get_settings() -> Settings:
    return Settings()
