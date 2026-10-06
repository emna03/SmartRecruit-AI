"""Assemblage des routes.

- ``/health`` : publique, sans aucune information sensible (supervision, Docker).
- ``/api/**`` : réservée à Spring Boot, chaque route exige l'en-tête ``X-Internal-Api-Key``.
  Les routes des modules IA (cv, matching, chatbot, ml) sont ajoutées à ``internal_router``.
"""

from fastapi import APIRouter, Depends

from app.api.routes import chatbot, cv, health, matching, ml, status
from app.core.security import require_internal_key
from app.schemas.common import ApiErrorResponse

public_router = APIRouter()
public_router.include_router(health.router)

internal_router = APIRouter(
    prefix="/api",
    dependencies=[Depends(require_internal_key)],
    responses={401: {"model": ApiErrorResponse, "description": "Clé d'API interne manquante ou invalide"}},
)
internal_router.include_router(status.router)
internal_router.include_router(cv.router)
internal_router.include_router(matching.router)
internal_router.include_router(chatbot.router)
internal_router.include_router(ml.router)
