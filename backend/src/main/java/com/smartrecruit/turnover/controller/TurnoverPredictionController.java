package com.smartrecruit.turnover.controller;

import com.smartrecruit.turnover.dto.TurnoverPrediction;
import com.smartrecruit.turnover.dto.TurnoverPredictionRequest;
import com.smartrecruit.turnover.service.TurnoverPredictionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Estimation du risque de turnover (ADMIN et RECRUTEUR) : une aide à la décision, jamais une décision. */
@RestController
@RequestMapping("/api/hr/turnover")
@RequiredArgsConstructor
public class TurnoverPredictionController {

    private final TurnoverPredictionService service;

    @PostMapping("/predictions")
    public TurnoverPrediction predict(@Valid @RequestBody TurnoverPredictionRequest request) {
        return service.predict(request);
    }
}
