from fastapi import APIRouter

from app import SERVICE_NAME, __version__
from app.schemas.common import ServiceStatusResponse
from app.services import MODULES

router = APIRouter(tags=["internal"])


@router.get("/status", response_model=ServiceStatusResponse,
            summary="État du service et des modules IA (clé interne requise)")
def service_status() -> ServiceStatusResponse:
    return ServiceStatusResponse(service=SERVICE_NAME, version=__version__, modules=dict(MODULES))
