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
    # Même variable que Spring Boot (MAX_CV_SIZE_MB) : taille maximale d'un CV accepté.
    max_cv_size_mb: int = Field(default=5, ge=1, le=20)
    ai_cv_max_pages: int = Field(default=10, ge=1, le=50)
    # Dossier du modèle ML produit par « python -m ml.train » (fichiers non versionnés).
    ai_ml_model_dir: Path = SERVICE_DIR / "ml" / "artifacts"
    # LLM externe (étape 12.5) : optionnel. Absent ⇒ endpoints LLM → 503, le reste du service reste utilisable.
    llm_provider: str = ""
    llm_api_key: SecretStr | None = None
    llm_model: str = ""
    llm_timeout_seconds: int = Field(default=15, ge=1, le=120)

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

    @field_validator("llm_provider")
    @classmethod
    def _check_llm_provider(cls, value: str) -> str:
        provider = value.strip().lower()
        if provider and provider != "gemini":
            raise ValueError("LLM_PROVIDER doit être vide ou « gemini »")
        return provider

    @field_validator("llm_model")
    @classmethod
    def _check_llm_model(cls, value: str) -> str:
        return value.strip()

    @property
    def llm_configured(self) -> bool:
        key = self.llm_api_key.get_secret_value().strip() if self.llm_api_key else ""
        return self.llm_provider == "gemini" and bool(key) and bool(self.llm_model)

    @property
    def port(self) -> int:
        if self.ai_service_port is not None:
            return self.ai_service_port
        return urlparse(self.ai_service_url).port or DEFAULT_PORT


@lru_cache
def get_settings() -> Settings:
    return Settings()
