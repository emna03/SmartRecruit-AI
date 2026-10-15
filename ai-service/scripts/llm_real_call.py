"""Appel Gemini réel contrôlé (données 100 % fictives). Ne journalise jamais la clé.

Usage (depuis ai-service, avec .env rempli) :
  .\\.venv\\Scripts\\python.exe -m scripts.llm_real_call
"""

from __future__ import annotations

import json
import sys

from app.core.config import get_settings
from app.schemas.llm import ProfessionalSummaryRequest
from app.services.llm.service import ProfessionalSummaryService


def main() -> int:
    settings = get_settings()
    if not settings.llm_configured:
        print("APPEL LLM RÉEL : ÉCHEC (LLM_* non configuré)")
        return 1
    # Masque : longueur uniquement.
    key_len = len(settings.llm_api_key.get_secret_value()) if settings.llm_api_key else 0
    print(f"provider={settings.llm_provider} model={settings.llm_model} key_len={key_len}")

    request = ProfessionalSummaryRequest.model_validate({
        "skills": ["API REST", "Scrum"],
        "technologies": ["Java", "Spring Boot", "Angular", "MySQL"],
        "educations": [{"degree": "Master informatique", "level": "BAC_PLUS_5"}],
        "experiences": [{"jobTitle": "Développeur logiciel", "durationMonths": 24}],
        "languages": [
            {"name": "Français", "level": "B2"},
            {"name": "Anglais", "level": "B2"},
        ],
        "totalExperienceMonths": 24,
        "highestEducationLevel": "BAC_PLUS_5",
    })
    result = ProfessionalSummaryService(settings).generate(request)
    print("APPEL LLM RÉEL : RÉUSSI")
    print(json.dumps(result.model_dump(by_alias=True), ensure_ascii=False, indent=2))
    invented = [k for k in result.keywords if k.lower() not in {
        "java", "spring boot", "angular", "mysql", "api rest", "scrum",
        "master informatique", "développeur logiciel", "français", "anglais", "b2", "bac_plus_5",
    } and k.lower() not in {"bac+5", "master"}]
    # Contrôle souple : les mots-clés doivent déjà avoir passé le filtre anti-hallucination.
    print(f"keywords_count={len(result.keywords)} summary_chars={len(result.summary)}")
    if invented:
        print("note: mots-clés non listés dans le vocabulaire strict (souvent des reformulations courtes) :", invented)
    return 0


if __name__ == "__main__":
    sys.exit(main())
