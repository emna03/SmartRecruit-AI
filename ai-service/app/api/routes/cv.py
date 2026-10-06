import logging

from fastapi import APIRouter, HTTPException, Request, status
from starlette.concurrency import run_in_threadpool

from app.schemas.common import ApiErrorResponse
from app.schemas.cv import CvParseResponse
from app.services.cv_parser.errors import CvParsingError
from app.services.cv_parser.pipeline import parse_cv

router = APIRouter(prefix="/cv", tags=["cv"])
logger = logging.getLogger("smartrecruit_ai.cv")

PDF_MEDIA_TYPE = "application/pdf"


def _too_large(max_mb: int) -> HTTPException:
    return HTTPException(413, f"Le fichier dépasse la taille maximale de {max_mb} Mo.")


@router.post(
    "/parse",
    response_model=CvParseResponse,
    summary="Analyse un CV PDF (corps de la requête : le fichier, Content-Type application/pdf)",
    openapi_extra={"requestBody": {"required": True, "content": {
        PDF_MEDIA_TYPE: {"schema": {"type": "string", "format": "binary"}}}}},
    responses={code: {"model": ApiErrorResponse} for code in (400, 413, 415, 422)},
)
async def parse(request: Request) -> CvParseResponse:
    settings = request.app.state.settings
    media_type = request.headers.get("content-type", "").split(";")[0].strip().lower()
    if media_type != PDF_MEDIA_TYPE:
        raise HTTPException(status.HTTP_415_UNSUPPORTED_MEDIA_TYPE, "Le CV doit être envoyé au format PDF.")

    max_bytes = settings.max_cv_size_mb * 1024 * 1024
    declared = request.headers.get("content-length", "")
    if declared.isdigit() and int(declared) > max_bytes:
        raise _too_large(settings.max_cv_size_mb)
    body = bytearray()
    async for chunk in request.stream():
        body.extend(chunk)
        if len(body) > max_bytes:
            raise _too_large(settings.max_cv_size_mb)

    try:
        result = await run_in_threadpool(parse_cv, bytes(body), settings.ai_cv_max_pages)
    except CvParsingError as exc:
        logger.info("CV refusé : %s (%d octets)", exc.code, len(body))
        raise HTTPException(exc.status_code, exc.message) from None

    # Uniquement des compteurs : jamais le contenu du CV.
    logger.info("CV analysé : %d page(s), %d caractères, %d technologies, %d compétences, %d formations, "
                "%d expériences, %d langues, %d ms", result.page_count, result.text_length,
                len(result.technologies), len(result.skills), len(result.educations), len(result.experiences),
                len(result.languages), result.processing_time_ms)
    return result
