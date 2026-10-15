import logging

from fastapi import APIRouter, HTTPException, Request

from app.schemas.common import ApiErrorResponse
from app.schemas.llm import ProfessionalSummaryRequest, ProfessionalSummaryResponse
from app.services.llm.errors import LlmError
from app.services.llm.gemini_client import GeminiClient
from app.services.llm.service import ProfessionalSummaryService

router = APIRouter(prefix="/llm", tags=["llm"])
logger = logging.getLogger("smartrecruit_ai.llm")


@router.post(
    "/professional-summary",
    response_model=ProfessionalSummaryResponse,
    response_model_by_alias=True,
    summary="Génère un résumé professionnel via Gemini à partir de données CV anonymisées",
    responses={
        400: {"model": ApiErrorResponse},
        422: {"model": ApiErrorResponse},
        502: {"model": ApiErrorResponse},
        503: {"model": ApiErrorResponse},
    },
)
def professional_summary(payload: ProfessionalSummaryRequest, request: Request) -> ProfessionalSummaryResponse:
    settings = request.app.state.settings
    generate_fn = getattr(request.app.state, "llm_generate_fn", None)
    client = GeminiClient(settings, generate_fn=generate_fn)
    service = ProfessionalSummaryService(settings, client=client)
    try:
        return service.generate(payload)
    except LlmError as exc:
        # Jamais la clé ni le prompt dans les journaux.
        logger.info("Échec LLM : code=%s, http=%s", exc.code, exc.status_code)
        raise HTTPException(status_code=exc.status_code, detail=exc.message) from None
