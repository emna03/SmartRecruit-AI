"""Client Google Gemini (SDK officiel google-genai).

La clé API n'est jamais journalisée. Les appels sont mockables via ``generate_fn`` pour les tests.
"""

from __future__ import annotations

import logging
from collections.abc import Callable
from typing import Any

from google import genai
from google.genai import types
from google.genai.errors import APIError, ClientError, ServerError

from app.core.config import Settings
from app.services.llm.errors import (
    auth_rejected,
    empty_response,
    not_configured,
    quota_exceeded,
    timeout,
    unavailable,
)
from app.services.llm.prompts import (
    MAX_OUTPUT_TOKENS,
    SYSTEM_PROMPT,
    TEMPERATURE,
    build_user_message,
    response_json_schema,
)

logger = logging.getLogger("smartrecruit_ai.llm")

GenerateFn = Callable[[str, str, dict[str, Any]], str]


class GeminiClient:
    def __init__(self, settings: Settings, generate_fn: GenerateFn | None = None):
        self._settings = settings
        self._generate_fn = generate_fn
        self._client: genai.Client | None = None

    @property
    def model_name(self) -> str:
        return self._settings.llm_model

    def generate_json(self, sanitized_payload: dict) -> str:
        if not self._settings.llm_configured:
            raise not_configured()
        user_message = build_user_message(sanitized_payload)
        config = {
            "temperature": TEMPERATURE,
            "max_output_tokens": MAX_OUTPUT_TOKENS,
            "response_mime_type": "application/json",
            "response_json_schema": response_json_schema(),
            "system_instruction": SYSTEM_PROMPT,
            "timeout_seconds": self._settings.llm_timeout_seconds,
        }
        # Jamais le prompt ni les données candidat dans les journaux.
        logger.info(
            "Appel Gemini : modèle=%s, température=%s, timeout=%ss",
            self.model_name,
            TEMPERATURE,
            self._settings.llm_timeout_seconds,
        )
        if self._generate_fn is not None:
            return self._generate_fn(self.model_name, user_message, config)
        return self._call_sdk(user_message, config)

    def _call_sdk(self, user_message: str, config: dict[str, Any]) -> str:
        client = self._sdk_client()
        try:
            response = client.models.generate_content(
                model=self.model_name,
                contents=user_message,
                config=types.GenerateContentConfig(
                    temperature=config["temperature"],
                    max_output_tokens=config["max_output_tokens"],
                    response_mime_type=config["response_mime_type"],
                    response_json_schema=config["response_json_schema"],
                    system_instruction=config["system_instruction"],
                    http_options=types.HttpOptions(timeout=config["timeout_seconds"] * 1000),
                ),
            )
        except ClientError as exc:
            raise _translate_client_error(exc) from exc
        except ServerError as exc:
            raise unavailable() from exc
        except APIError as exc:
            raise _translate_api_error(exc) from exc
        except TimeoutError as exc:
            raise timeout() from exc
        except Exception as exc:
            message = str(exc).lower()
            if "timeout" in message or "timed out" in message:
                raise timeout() from exc
            if "429" in message or "quota" in message or "resource_exhausted" in message:
                raise quota_exceeded() from exc
            if "401" in message or "403" in message or "api key" in message or "permission" in message:
                raise auth_rejected() from exc
            logger.warning("Erreur Gemini non classée : %s", type(exc).__name__)
            raise unavailable() from exc

        text = (getattr(response, "text", None) or "").strip()
        if not text:
            raise empty_response()
        return text

    def _sdk_client(self) -> genai.Client:
        if self._client is None:
            key = self._settings.llm_api_key.get_secret_value() if self._settings.llm_api_key else ""
            self._client = genai.Client(api_key=key)
        return self._client


def _translate_client_error(exc: ClientError):
    code = getattr(exc, "code", None) or getattr(exc, "status_code", None)
    message = str(exc).lower()
    if code in (401, 403) or "api key" in message or "permission" in message:
        return auth_rejected()
    if code == 429 or "quota" in message or "resource_exhausted" in message:
        return quota_exceeded()
    if code == 408 or "timeout" in message:
        return timeout()
    return unavailable()


def _translate_api_error(exc: APIError):
    code = getattr(exc, "code", None) or getattr(exc, "status_code", None)
    message = str(exc).lower()
    if code in (401, 403):
        return auth_rejected()
    if code == 429:
        return quota_exceeded()
    if code in (408, 504) or "timeout" in message:
        return timeout()
    return unavailable()
