"""Orchestration : sanitation → Gemini → validation anti-hallucination."""

from __future__ import annotations

import logging

from app.core.config import Settings
from app.schemas.llm import ProfessionalSummaryRequest, ProfessionalSummaryResponse
from app.services.llm.errors import empty_profile
from app.services.llm.gemini_client import GeminiClient
from app.services.llm.sanitation import has_usable_content, sanitize
from app.services.llm.validation import parse_and_validate

logger = logging.getLogger("smartrecruit_ai.llm")


class ProfessionalSummaryService:
    def __init__(self, settings: Settings, client: GeminiClient | None = None):
        self._settings = settings
        self._client = client or GeminiClient(settings)

    def generate(self, request: ProfessionalSummaryRequest) -> ProfessionalSummaryResponse:
        payload = sanitize(request)
        if not has_usable_content(payload):
            raise empty_profile()
        raw = self._client.generate_json(payload)
        result = parse_and_validate(raw, payload, self._client.model_name)
        # Compteurs uniquement : jamais le résumé ni les données candidat.
        logger.info(
            "Résumé LLM généré : modèle=%s, résumé=%d car., mots-clés=%d, prompt=%s",
            result.model,
            len(result.summary),
            len(result.keywords),
            result.prompt_version,
        )
        return result
