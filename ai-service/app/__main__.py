"""Point d'entrée : ``python -m app`` (depuis ``ai-service/``, environnement virtuel activé)."""

import sys

import uvicorn
from pydantic import ValidationError

from app.core.config import get_settings


def main() -> None:
    try:
        settings = get_settings()
    except ValidationError as exc:
        problems = "\n".join(f"  - {e['loc'][0] if e['loc'] else 'config'}: {e['msg']}" for e in exc.errors())
        sys.exit(f"Configuration invalide du service IA (voir .env.example) :\n{problems}")

    uvicorn.run(
        "app.main:create_app",
        factory=True,
        host=settings.ai_service_host,
        port=settings.port,
        log_level=settings.ai_log_level,
        access_log=False,
        server_header=False,
    )


if __name__ == "__main__":
    main()
