package com.smartrecruit.matching.dto;

import com.smartrecruit.application.dto.RecruiterApplicationResponse;

import java.util.List;

/** Recalcul des scores des candidats d'une offre : compteurs et candidatures classées par score. */
public record MatchingRefreshResponse(int computed, int withoutAnalyzedCv,
                                      List<RecruiterApplicationResponse> applications) {
}
