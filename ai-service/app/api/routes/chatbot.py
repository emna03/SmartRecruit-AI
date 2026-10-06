import logging

from fastapi import APIRouter

from app.schemas.chatbot import ChatbotRequest, ChatbotResponse
from app.schemas.common import ApiErrorResponse
from app.services.chatbot.engine import answer_question

router = APIRouter(prefix="/chatbot", tags=["chatbot"])
logger = logging.getLogger("smartrecruit_ai.chatbot")


@router.post(
    "/ask",
    response_model=ChatbotResponse,
    summary="Réponse du chatbot RH, construite à partir des offres publiées reçues et de la base de connaissances",
    responses={400: {"model": ApiErrorResponse}},
)
def ask(request: ChatbotRequest) -> ChatbotResponse:
    response = answer_question(request)
    # Intention et compteurs uniquement : jamais le texte de la question (il peut contenir des données personnelles).
    logger.info("Chatbot : intention %s (confiance %.2f), %d offres reçues, %d citées, version %s",
                response.intent, response.confidence, len(request.offers), len(response.offer_ids),
                response.chatbot_version)
    return response
