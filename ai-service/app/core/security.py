"""Protection des routes internes par la clé partagée avec Spring Boot."""

import hmac
import logging

from fastapi import HTTPException, Request, Security, status
from fastapi.security import APIKeyHeader

API_KEY_HEADER = "X-Internal-Api-Key"
INVALID_KEY_MESSAGE = "Clé d'API interne manquante ou invalide"

logger = logging.getLogger("smartrecruit_ai.security")

_api_key_header = APIKeyHeader(name=API_KEY_HEADER, auto_error=False)


def require_internal_key(request: Request, api_key: str | None = Security(_api_key_header)) -> None:
    expected = request.app.state.settings.ai_internal_api_key.get_secret_value()
    # Comparaison en temps constant pour ne pas révéler la clé par mesure du temps de réponse.
    if not api_key or not hmac.compare_digest(api_key.encode(), expected.encode()):
        logger.warning(
            "Appel interne refusé : %s %s (clé %s, client %s)",
            request.method,
            request.url.path,
            "absente" if not api_key else "invalide",
            request.client.host if request.client else "inconnu",
        )
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=INVALID_KEY_MESSAGE)
