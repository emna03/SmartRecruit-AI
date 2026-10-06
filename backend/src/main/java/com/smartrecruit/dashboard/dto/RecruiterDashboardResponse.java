package com.smartrecruit.dashboard.dto;

import com.smartrecruit.application.entity.ApplicationStatus;
import com.smartrecruit.dashboard.repository.EnumCounts;
import com.smartrecruit.joboffer.entity.JobOfferStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Statistiques des offres de l'entreprise du recruteur connecté (toutes les offres de l'entreprise, quel que soit
 * le recruteur qui les a créées). Les scores ne concernent que les candidats ayant postulé.
 *
 * @param averageMatchingScore moyenne des scores des candidatures ayant un score ; null s'il n'y en a aucun
 * @param offers               toutes les offres de l'entreprise, les plus récentes d'abord
 * @param topCandidates        5 candidatures au meilleur score (aide à la lecture, jamais une décision)
 */
public record RecruiterDashboardResponse(
        boolean hasCompany,
        String companyName,
        long totalOffers,
        Map<JobOfferStatus, Long> offersByStatus,
        long totalApplications,
        Map<ApplicationStatus, Long> applicationsByStatus,
        List<MonthlyCount> applicationsByMonth,
        long scoredApplications,
        Double averageMatchingScore,
        List<OfferStats> offers,
        List<TopCandidate> topCandidates) {

    public static RecruiterDashboardResponse withoutCompany() {
        return new RecruiterDashboardResponse(false, null, 0, EnumCounts.zeros(JobOfferStatus.class), 0,
                EnumCounts.zeros(ApplicationStatus.class), List.of(), 0, null, List.of(), List.of());
    }

    public record OfferStats(Long id, String title, JobOfferStatus status, LocalDateTime createdAt,
                             long applications, Map<ApplicationStatus, Long> applicationsByStatus,
                             long scoredApplications, Double averageScore, Double bestScore) {
    }

    public record TopCandidate(Long applicationId, String candidateName, Long jobOfferId, String jobTitle,
                               double score, ApplicationStatus status, boolean stale) {
    }
}
