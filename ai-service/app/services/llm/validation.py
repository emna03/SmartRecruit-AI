"""Validation Pydantic + filtre anti-hallucination des mots-clés."""

from __future__ import annotations

import json
import re

from pydantic import ValidationError

from app.schemas.llm import ProfessionalSummaryResponse
from app.services.cv_parser.text import fold
from app.services.llm import PROMPT_VERSION, PROVIDER_NAME
from app.services.llm.errors import hallucination, invalid_json, invalid_schema
from app.services.llm.sanitation import allowed_keyword_tokens

# Un mot-clé « libre » ne peut pas être extrait d'une phrase trop longue (anti prompt-injection).
_MAX_SOURCE_WORDS = 6
_WORD = re.compile(r"[\w+#./-]{2,}", re.UNICODE)


def parse_and_validate(raw_text: str, sanitized_payload: dict, model_name: str) -> ProfessionalSummaryResponse:
    """Parse le JSON Gemini, valide le schéma, filtre les mots-clés inventés."""
    try:
        data = json.loads(raw_text)
    except json.JSONDecodeError as exc:
        raise invalid_json() from exc
    if not isinstance(data, dict):
        raise invalid_schema("l'objet racine doit être un JSON object")

    keywords = data.get("keywords")
    if not isinstance(keywords, list):
        raise invalid_schema("keywords doit être une liste")

    allowed = allowed_keyword_tokens(sanitized_payload)
    accepted, rejected = filter_keywords(keywords, allowed)
    if not accepted:
        detail = ", ".join(rejected[:5]) if rejected else "aucun mot-clé cohérent"
        raise hallucination(detail)

    # Si une majorité stricte de mots-clés est inventée, on refuse toute la réponse.
    if rejected and len(rejected) > len(accepted):
        raise hallucination(", ".join(rejected[:5]))

    candidate = {
        "summary": data.get("summary"),
        "keywords": accepted,
        "model": model_name,
        "provider": PROVIDER_NAME,
        "promptVersion": PROMPT_VERSION,
        "source": "llm",
    }
    try:
        return ProfessionalSummaryResponse.model_validate(candidate)
    except ValidationError as exc:
        raise invalid_schema(exc.errors()[0]["msg"]) from exc


def filter_keywords(keywords: list, allowed: set[str]) -> tuple[list[str], list[str]]:
    accepted: list[str] = []
    rejected: list[str] = []
    seen: set[str] = set()
    for item in keywords:
        if not isinstance(item, str):
            continue
        text = " ".join(item.split()).strip()
        if not text:
            continue
        key = fold(text)
        if key in seen:
            continue
        seen.add(key)
        if _is_allowed(text, allowed):
            accepted.append(text)
        else:
            rejected.append(text)
    return accepted, rejected


def _is_allowed(keyword: str, allowed: set[str]) -> bool:
    folded = fold(keyword)
    if folded in allowed:
        return True
    # Correspondance exacte élargie : le mot-clé est un alias court d'un terme autorisé
    # (« Spring » ↔ « Spring Boot ») sans ouvrir la porte aux phrases d'injection.
    for token in allowed:
        if _short_alias(folded, token):
            return True
    return False


def _short_alias(keyword: str, token: str) -> bool:
    if not keyword or not token:
        return False
    if keyword == token:
        return True
    # Uniquement si les deux sont des libellés courts (pas une phrase d'injection).
    if len(keyword.split()) > _MAX_SOURCE_WORDS or len(token.split()) > _MAX_SOURCE_WORDS:
        return False
    if keyword in token.split() or token.startswith(keyword + " ") or keyword.startswith(token + " "):
        return True
    return False
