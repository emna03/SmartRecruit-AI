import logging

from fastapi import APIRouter, HTTPException, Request

from app.schemas.common import ApiErrorResponse
from app.schemas.ml import TurnoverFactor, TurnoverModelInfo, TurnoverPredictionRequest, TurnoverPredictionResponse
from app.services.ml.model import TurnoverModel

router = APIRouter(prefix="/ml", tags=["ml"])
logger = logging.getLogger("smartrecruit_ai.ml")

MODEL_UNAVAILABLE_MESSAGE = "Modèle de prédiction indisponible : il doit être entraîné (python -m ml.train)."


def _model(request: Request) -> TurnoverModel:
    model = getattr(request.app.state, "turnover_model", None)
    if model is None:
        raise HTTPException(status_code=503, detail=MODEL_UNAVAILABLE_MESSAGE)
    return model


@router.post(
    "/turnover/predict",
    response_model=TurnoverPredictionResponse,
    summary="Estimation du risque de départ d'un employé (aide à la décision, jamais une décision automatique)",
    responses={400: {"model": ApiErrorResponse}, 503: {"model": ApiErrorResponse}},
)
def predict_turnover(payload: TurnoverPredictionRequest, request: Request) -> TurnoverPredictionResponse:
    model = _model(request)
    prediction = model.predict(payload.to_row())
    metadata = model.metadata
    metrics = metadata["testMetrics"]
    # Score et niveau uniquement : jamais les variables saisies (données RH d'une personne).
    logger.info("Prédiction turnover : score %.3f, niveau %s, modèle %s v%s", prediction.probability,
                prediction.risk_level, metadata["modelName"], metadata["modelVersion"])
    return TurnoverPredictionResponse(
        probability=prediction.probability,
        risk_level=prediction.risk_level,
        medium_threshold=metadata["mediumThreshold"],
        high_threshold=metadata["highThreshold"],
        factors=[TurnoverFactor(feature=f.feature, label=f.label, impact=f.impact, direction=f.direction,
                                reference_value=f.reference_value) for f in prediction.factors],
        model=TurnoverModelInfo(
            name=metadata["modelName"],
            version=metadata["modelVersion"],
            dataset=metadata["datasetName"],
            explanation_method=metadata["explanation"],
            test_accuracy=metrics["accuracy"],
            test_precision=metrics["precision"],
            test_recall=metrics["recall"],
            test_f1=metrics["f1"],
            test_roc_auc=metrics["rocAuc"],
        ),
    )
