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
    # Le module LLM n'est disponible que si LLM_PROVIDER/LLM_API_KEY/LLM_MODEL sont configurés.
    settings = getattr(request.app.state, "settings", None)
    modules["llm"] = modules["llm"] and bool(getattr(settings, "llm_configured", False))
    # Sentence Transformer : disponible dès qu'un embedder est enregistré (chargement lazy).
    modules["semantic"] = modules["semantic"] and getattr(request.app.state, "semantic_embedder", None) is not None
    # K-Means : disponible seulement si un modèle entraîné a été chargé.
    modules["clustering"] = modules["clustering"] and getattr(request.app.state, "clustering_model", None) is not None
    return ServiceStatusResponse(service=SERVICE_NAME, version=__version__, modules=modules)
