"""Erreurs métier du module LLM, traduites en HTTP par la route."""


class LlmError(Exception):
    def __init__(self, code: str, message: str, status_code: int):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code


def not_configured() -> LlmError:
    return LlmError(
        "LLM_NOT_CONFIGURED",
        "La génération IA n'est pas configurée (LLM_PROVIDER / LLM_API_KEY / LLM_MODEL).",
        503,
    )


def auth_rejected() -> LlmError:
    return LlmError(
        "LLM_AUTH_REJECTED",
        "La clé API du fournisseur LLM a été refusée. Vérifiez LLM_API_KEY.",
        503,
    )


def timeout() -> LlmError:
    return LlmError(
        "LLM_TIMEOUT",
        "Le fournisseur LLM n'a pas répondu à temps. Réessayez dans quelques instants.",
        503,
    )


def quota_exceeded() -> LlmError:
    return LlmError(
        "LLM_QUOTA",
        "Le quota du fournisseur LLM est temporairement dépassé. Réessayez plus tard.",
        503,
    )


def unavailable() -> LlmError:
    return LlmError(
        "LLM_UNAVAILABLE",
        "Le fournisseur LLM est momentanément indisponible. Réessayez plus tard.",
        503,
    )


def empty_response() -> LlmError:
    return LlmError(
        "LLM_EMPTY_RESPONSE",
        "Le fournisseur LLM a renvoyé une réponse vide.",
        502,
    )


def invalid_json() -> LlmError:
    return LlmError(
        "LLM_INVALID_JSON",
        "La réponse du fournisseur LLM n'est pas un JSON valide.",
        502,
    )


def invalid_schema(detail: str) -> LlmError:
    return LlmError(
        "LLM_INVALID_SCHEMA",
        f"La réponse du fournisseur LLM ne respecte pas le schéma attendu : {detail}",
        502,
    )


def hallucination(detail: str) -> LlmError:
    return LlmError(
        "LLM_HALLUCINATION",
        f"La réponse IA a été rejetée car elle contient des éléments absents du CV : {detail}",
        422,
    )


def empty_profile() -> LlmError:
    return LlmError(
        "LLM_EMPTY_PROFILE",
        "Aucune information professionnelle exploitable n'a été fournie pour générer un résumé.",
        400,
    )
