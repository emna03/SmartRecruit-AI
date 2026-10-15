"""Endpoint matching sémantique (Sentence Transformer pré-entraîné)."""

import logging

from fastapi import APIRouter, HTTPException, Request

from app.schemas.common import ApiErrorResponse
from app.schemas.semantic import SemanticMatchingRequest, SemanticMatchingResponse
from app.services.semantic.service import EmptyProfileError, SemanticMatchingService

router = APIRouter(prefix="/ml", tags=["ml", "semantic"])
logger = logging.getLogger("smartrecruit_ai.semantic")


@router.post(
    "/semantic-matching",
    response_model=SemanticMatchingResponse,
    summary="Similarité sémantique CV ↔ offre (Sentence Transformer pré-entraîné)",
    responses={400: {"model": ApiErrorResponse}, 503: {"model": ApiErrorResponse}},
)
def semantic_matching(payload: SemanticMatchingRequest, request: Request) -> SemanticMatchingResponse:
    embedder = getattr(request.app.state, "semantic_embedder", None)
    service = SemanticMatchingService(embedder=embedder) if embedder is not None else SemanticMatchingService()
    try:
        return service.match(payload)
    except EmptyProfileError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from None
    except Exception:
        logger.exception("Échec matching sémantique")
        raise HTTPException(
            status_code=503,
            detail="Matching sémantique indisponible (modèle Sentence Transformer non chargé).",
        ) from None
