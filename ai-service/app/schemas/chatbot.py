"""Entrée et sortie du chatbot RH (JSON en camelCase).

Spring Boot envoie la question et le catalogue des offres publiées (données publiques uniquement : aucune
information sur le candidat). La réponse ne cite que des offres de ce catalogue, désignées par leur identifiant.
"""

from decimal import Decimal
from typing import Annotated, Literal

from pydantic import Field, StringConstraints, model_validator

from app.schemas.cv import CamelModel, EducationLevel
from app.schemas.matching import StrictCamelModel

MAX_MESSAGE_LENGTH = 500
MAX_OFFERS = 300
MAX_RETURNED_OFFERS = 10

ContractType = Literal["CDI", "CDD", "STAGE", "ALTERNANCE", "FREELANCE"]
Intent = Literal[
    "GREETING", "THANKS", "HELP", "SEARCH_OFFERS", "OFFER_SKILLS", "OFFER_EXPERIENCE", "OFFER_EDUCATION",
    "OFFER_DETAILS", "OFFER_SALARY", "FAQ_APPLY", "FAQ_CV", "FAQ_MATCHING", "FAQ_APPLICATIONS", "FAQ_PROFILE",
    "FAQ_PRIVACY", "FAQ_RECOMMENDATION", "UNKNOWN",
]
# Liens vers les pages de l'application : Angular associe chaque action à une route connue.
Action = Literal["OPEN_JOBS", "OPEN_CV", "OPEN_APPLICATIONS", "OPEN_PROFILE"]

Message = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=MAX_MESSAGE_LENGTH)]
ShortName = Annotated[str, Field(min_length=1, max_length=150)]
SkillName = Annotated[str, Field(min_length=1, max_length=100)]
Amount = Annotated[Decimal, Field(ge=0, le=10_000_000)]


class ChatbotOffer(StrictCamelModel):
    id: Annotated[int, Field(ge=1)]
    title: ShortName
    company: ShortName
    location: ShortName | None = None
    contract_type: ContractType
    min_experience_years: Annotated[int, Field(ge=0, le=50)] = 0
    education_level: EducationLevel | None = None
    salary_min: Amount | None = None
    salary_max: Amount | None = None
    required_skills: Annotated[list[SkillName], Field(max_length=50)] = []


class ChatbotRequest(StrictCamelModel):
    message: Message
    context_offer_id: Annotated[int, Field(ge=1)] | None = None
    offers: Annotated[list[ChatbotOffer], Field(max_length=MAX_OFFERS)] = []

    @model_validator(mode="after")
    def _consistent_offers(self) -> "ChatbotRequest":
        ids = [offer.id for offer in self.offers]
        if len(ids) != len(set(ids)):
            raise ValueError("Chaque offre du catalogue doit avoir un identifiant unique")
        if self.context_offer_id is not None and self.context_offer_id not in ids:
            raise ValueError("L'offre de contexte doit faire partie du catalogue envoyé")
        return self


class ChatbotEntities(CamelModel):
    """Informations extraites de la question (affichées pour expliquer la réponse)."""

    skills: Annotated[list[SkillName], Field(max_length=20)] = []
    experience_years: Annotated[int, Field(ge=0, le=50)] | None = None
    education_level: EducationLevel | None = None
    contract_type: ContractType | None = None
    location: ShortName | None = None
    keywords: Annotated[list[Annotated[str, Field(min_length=1, max_length=40)]], Field(max_length=5)] = []


class ChatbotResponse(CamelModel):
    chatbot_version: Annotated[str, Field(min_length=1, max_length=20)]
    intent: Intent
    confidence: Annotated[float, Field(ge=0, le=1)]
    answer: Annotated[str, Field(min_length=1, max_length=2000)]
    offer_ids: Annotated[list[Annotated[int, Field(ge=1)]], Field(max_length=MAX_RETURNED_OFFERS)] = []
    total_matches: Annotated[int, Field(ge=0)] = 0
    context_offer_id: Annotated[int, Field(ge=1)] | None = None
    entities: ChatbotEntities = ChatbotEntities()
    suggestions: Annotated[list[Annotated[str, Field(min_length=1, max_length=120)]], Field(max_length=4)] = []
    actions: Annotated[list[Action], Field(max_length=3)] = []
