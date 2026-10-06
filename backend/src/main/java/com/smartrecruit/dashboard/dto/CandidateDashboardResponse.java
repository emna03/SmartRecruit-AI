package com.smartrecruit.dashboard.dto;

import com.smartrecruit.application.entity.ApplicationStatus;
import com.smartrecruit.cv.entity.CvParsingStatus;
import com.smartrecruit.joboffer.entity.JobOfferStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Statistiques du candidat connecté, calculées dans MySQL au moment de la requête.
 *
 * @param applicationsByStatus      tous les statuts, dans l'ordre de l'énumération (0 si aucune candidature)
 * @param publishedOffers           offres publiées sur la plateforme
 * @param publishedOffersNotApplied offres publiées auxquelles le candidat n'a pas encore postulé
 * @param scoredOffers              scores de compatibilité enregistrés et visibles (offre publiée ou candidature)
 * @param topMatches                5 meilleurs de ces scores
 */
public record CandidateDashboardResponse(
        long totalApplications,
        Map<ApplicationStatus, Long> applicationsByStatus,
        long publishedOffers,
        long publishedOffersNotApplied,
        CvSummary cv,
        long scoredOffers,
        List<TopMatch> topMatches) {

    /** ABSENT : aucun CV ; IMPORTE : CV enregistré mais pas analysé (analyse en cours ou en échec) ; ANALYSE. */
    public enum CvState { ABSENT, IMPORTE, ANALYSE }

    public record CvSummary(CvState state, CvParsingStatus parsingStatus, LocalDateTime updatedAt,
                            LocalDateTime parsedAt, long extractedSkills) {
    }

    /** {@code stale} : le CV a changé depuis le calcul du score. */
    public record TopMatch(Long jobOfferId, String title, String companyName, JobOfferStatus offerStatus,
                           double score, LocalDateTime computedAt, boolean stale, boolean applied) {
    }
}
