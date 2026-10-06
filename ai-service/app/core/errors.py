"""Format d'erreur unique, identique à l'ApiError du backend Spring Boot.

``{timestamp, status, error, message, path, fieldErrors?}`` : Spring Boot peut ainsi lire
le message de n'importe quelle erreur renvoyée par le service IA.
"""

import logging
from datetime import UTC, datetime
from http import HTTPStatus

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

logger = logging.getLogger("smartrecruit_ai.errors")

DEFAULT_MESSAGES = {
    404: "Ressource introuvable",
    405: "Méthode non autorisée",
}


def error_response(status: int, message: str, path: str, field_errors: dict[str, str] | None = None,
                   headers: dict[str, str] | None = None) -> JSONResponse:
    body: dict = {
        "timestamp": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "status": status,
        "error": HTTPStatus(status).phrase,
        "message": message,
        "path": path,
    }
    if field_errors:
        body["fieldErrors"] = field_errors
    return JSONResponse(status_code=status, content=body, headers=headers)


async def _http_exception_handler(request: Request, exc: StarletteHTTPException) -> JSONResponse:
    message = exc.detail if isinstance(exc.detail, str) and exc.detail != HTTPStatus(exc.status_code).phrase \
        else DEFAULT_MESSAGES.get(exc.status_code, HTTPStatus(exc.status_code).phrase)
    return error_response(exc.status_code, message, request.url.path, headers=getattr(exc, "headers", None))


async def _validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    fields: dict[str, str] = {}
    for error in exc.errors():
        location = [str(part) for part in error.get("loc", ()) if part not in ("body", "query", "path", "header")]
        fields.setdefault(".".join(location) or "body", error.get("msg", "Valeur invalide"))
    return error_response(400, "Données invalides", request.url.path, fields)


async def _unexpected_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    logger.exception("Erreur inattendue sur %s %s", request.method, request.url.path)
    return error_response(500, "Erreur interne du service IA", request.url.path)


def register_exception_handlers(app: FastAPI) -> None:
    app.add_exception_handler(StarletteHTTPException, _http_exception_handler)
    app.add_exception_handler(RequestValidationError, _validation_exception_handler)
    app.add_exception_handler(Exception, _unexpected_exception_handler)
