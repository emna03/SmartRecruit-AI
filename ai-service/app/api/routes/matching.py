import logging

from fastapi import APIRouter

from app.schemas.common import ApiErrorResponse
from app.schemas.matching import MatchingRequest, MatchingResponse
from app.services.matching.engine import compute_match

router = APIRouter(prefix="/matching", tags=["matching"])
logger = logging.getLogger("smartrecruit_ai.matching")


@router.post(
    "/score",
    response_model=MatchingResponse,
    summary="Score de compatibilité explicable (0-100) entre les données extraites d'un CV et une offre",
    responses={400: {"model": ApiErrorResponse}},
)
def score(request: MatchingRequest) -> MatchingResponse:
    result = compute_match(request)
    # Compteurs et score uniquement (aucun nom de compétence ni donnée du candidat).
    logger.info("Matching calculé : score %.1f, %d/%d compétences, version %s", result.score,
                len(result.matched_skills), result.required_skills_count, result.algorithm_version)
    return result
