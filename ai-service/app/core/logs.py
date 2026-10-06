"""Journalisation : une ligne par requête, corrélée avec Spring Boot par l'en-tête X-Request-Id.

Seuls la méthode, le chemin, le statut et la durée sont journalisés : jamais les en-têtes
(clé interne) ni le contenu des requêtes (données personnelles des CV).
"""

import logging
import re
import time
import uuid

from fastapi import FastAPI, Request

REQUEST_ID_HEADER = "X-Request-Id"
_VALID_REQUEST_ID = re.compile(r"^[A-Za-z0-9._-]{1,64}$")

logger = logging.getLogger("smartrecruit_ai.access")


def configure_logging(level: str) -> None:
    logging.basicConfig(level=level.upper(), format="%(asctime)s %(levelname)-7s %(name)s - %(message)s")
    logging.getLogger("smartrecruit_ai").setLevel(level.upper())


def register_request_logging(app: FastAPI) -> None:
    @app.middleware("http")
    async def log_request(request: Request, call_next):
        received = request.headers.get(REQUEST_ID_HEADER, "")
        request_id = received if _VALID_REQUEST_ID.match(received) else uuid.uuid4().hex
        start = time.perf_counter()
        response = await call_next(request)
        response.headers[REQUEST_ID_HEADER] = request_id
        logger.info("%s %s -> %d (%.0f ms) [%s]", request.method, request.url.path, response.status_code,
                    (time.perf_counter() - start) * 1000, request_id)
        return response
