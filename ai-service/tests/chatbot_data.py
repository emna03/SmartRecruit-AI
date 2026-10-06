"""Catalogue d'offres fictives (format envoyé par Spring Boot) partagé par les tests du chatbot."""

from app.schemas.chatbot import ChatbotOffer, ChatbotRequest

OFFERS_JSON = [
    {"id": 11, "title": "Développeur Java Spring Boot", "company": "Acme", "location": "Tunis", "contractType": "CDI",
     "minExperienceYears": 2, "educationLevel": "BAC_PLUS_5", "salaryMin": 2500, "salaryMax": 3500,
     "requiredSkills": ["Java", "Spring Boot", "MySQL", "Docker"]},
    {"id": 12, "title": "Développeur Front-end Angular", "company": "WebCo", "location": "Sousse", "contractType": "CDD",
     "minExperienceYears": 1, "educationLevel": "BAC_PLUS_3", "salaryMin": None, "salaryMax": None,
     "requiredSkills": ["Angular", "TypeScript", "HTML", "CSS"]},
    {"id": 13, "title": "Data Scientist", "company": "DataLab", "location": "Tunis", "contractType": "STAGE",
     "minExperienceYears": 0, "educationLevel": None, "salaryMin": None, "salaryMax": 1200,
     "requiredSkills": ["Python", "Machine Learning", "pandas"]},
    {"id": 14, "title": "Ingénieur Full Stack Java Angular", "company": "Acme", "location": "Tunis, Lac 2",
     "contractType": "CDI", "minExperienceYears": 4, "educationLevel": "BAC_PLUS_5", "salaryMin": None,
     "salaryMax": None, "requiredSkills": ["Java", "Angular", "Spring Boot", "Kubernetes"]},
]
OFFERS = [ChatbotOffer.model_validate(o) for o in OFFERS_JSON]


def ask(message: str, context: int | None = None, offers: list[ChatbotOffer] | None = None) -> ChatbotRequest:
    return ChatbotRequest(message=message, context_offer_id=context, offers=OFFERS if offers is None else offers)
