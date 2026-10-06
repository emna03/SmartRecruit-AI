from fastapi import APIRouter, Request

from app import SERVICE_NAME, __version__
from app.schemas.common import ServiceStatusResponse
from app.services import MODULES

router = APIRouter(tags=["internal"])


@router.get("/status", response_model=ServiceStatusResponse,
            summary="État du service et des modules IA (clé interne requise)")
def service_status(request: Request) -> ServiceStatusResponse:
    modules = dict(MODULES)
    # Le module ML n'est disponible que si un modèle entraîné et vérifié a été chargé.
    modules["ml"] = modules["ml"] and getattr(request.app.state, "turnover_model", None) is not None
    return ServiceStatusResponse(service=SERVICE_NAME, version=__version__, modules=modules)
