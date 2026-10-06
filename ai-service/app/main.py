"""Création de l'application FastAPI.

Fabrique (``create_app``) plutôt qu'instance globale : la configuration n'est lue qu'au démarrage
et les tests peuvent fournir leurs propres paramètres sans dépendre du fichier .env.
Lancement : ``python -m app`` (voir ``app/__main__.py``).
"""

from fastapi import FastAPI

from app import SERVICE_NAME, __version__
from app.api.router import internal_router, public_router
from app.core.config import Settings, get_settings
from app.core.errors import register_exception_handlers
from app.core.logs import configure_logging, register_request_logging
from app.services.ml.model import load_model


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    configure_logging(settings.ai_log_level)

    docs = settings.ai_docs_enabled
    app = FastAPI(
        title="SmartRecruit AI - service IA",
        version=__version__,
        summary=f"{SERVICE_NAME} : service interne appelé uniquement par le backend Spring Boot",
        docs_url="/docs" if docs else None,
        redoc_url=None,
        openapi_url="/openapi.json" if docs else None,
    )
    app.state.settings = settings
    # Modèle chargé une seule fois ; None si absent ou invalide (le module ML est alors annoncé indisponible).
    app.state.turnover_model = load_model(settings.ai_ml_model_dir)

    register_exception_handlers(app)
    register_request_logging(app)
    app.include_router(public_router)
    app.include_router(internal_router)
    return app
