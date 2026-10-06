package com.smartrecruit.turnover.service;

import com.smartrecruit.ai.AiServiceClient;
import com.smartrecruit.turnover.dto.TurnoverPrediction;
import com.smartrecruit.turnover.dto.TurnoverPredictionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Risque de turnover : variables saisies (Angular) -> Spring Boot (validation) -> FastAPI (modèle ML entraîné sur
 * le dataset IBM) -> prédiction validée -> Angular.
 *
 * <p>Démonstration d'aide à la décision : rien n'est enregistré, aucune décision n'est prise, et seuls le niveau
 * de risque et le modèle sont journalisés (jamais les variables saisies).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurnoverPredictionService {

    static final String PREDICT_PATH = "/api/ml/turnover/predict";

    private final AiServiceClient aiClient;

    public TurnoverPrediction predict(TurnoverPredictionRequest request) {
        TurnoverPrediction prediction = aiClient.post(PREDICT_PATH, request, TurnoverPrediction.class);
        log.info("Prédiction de turnover : niveau {}, modèle {} v{}", prediction.riskLevel(),
                prediction.model().name(), prediction.model().version());
        return prediction;
    }
}
