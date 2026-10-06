from typing import Literal

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: Literal["UP"] = "UP"
    service: str
    version: str


class ServiceStatusResponse(HealthResponse):
    # Modules IA et disponibilité : chaque module passe à True quand il est implémenté.
    modules: dict[str, bool]


class ApiErrorResponse(BaseModel):
    timestamp: str
    status: int
    error: str
    message: str
    path: str
    field_errors: dict[str, str] | None = Field(default=None, alias="fieldErrors")
