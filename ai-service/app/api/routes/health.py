from fastapi import APIRouter

from app import SERVICE_NAME, __version__
from app.schemas.common import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse, summary="Vérification de disponibilité (publique)")
def health() -> HealthResponse:
    return HealthResponse(service=SERVICE_NAME, version=__version__)
