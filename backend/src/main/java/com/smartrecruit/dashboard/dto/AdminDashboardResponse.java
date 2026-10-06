package com.smartrecruit.dashboard.dto;

import com.smartrecruit.application.entity.ApplicationStatus;
import com.smartrecruit.cv.entity.CvParsingStatus;
import com.smartrecruit.joboffer.entity.ContractType;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import com.smartrecruit.user.entity.RoleName;

import java.util.List;
import java.util.Map;

/**
 * Statistiques globales de la plateforme (administrateur). Les prédictions de turnover ne sont pas enregistrées :
 * aucune statistique de prédiction n'existe.
 *
 * @param storedMatchingScores    scores de compatibilité enregistrés (consultations candidat et candidatures)
 * @param averageMatchingScore    moyenne de ces scores ; null s'il n'y en a aucun
 * @param publishedOffersByContract offres publiées par type de contrat
 * @param topRequestedSkills      compétences les plus demandées par les offres publiées (8 au plus)
 * @param activityByMonth         créations par mois sur les 6 derniers mois (UTC)
 */
public record AdminDashboardResponse(
        long totalUsers,
        Map<RoleName, Long> usersByRole,
        long disabledUsers,
        long companies,
        long recruitersWithoutCompany,
        long totalOffers,
        Map<JobOfferStatus, Long> offersByStatus,
        Map<ContractType, Long> publishedOffersByContract,
        long totalApplications,
        Map<ApplicationStatus, Long> applicationsByStatus,
        long candidatesWithCv,
        Map<CvParsingStatus, Long> cvsByParsingStatus,
        long storedMatchingScores,
        Double averageMatchingScore,
        List<SkillCount> topRequestedSkills,
        List<MonthlyActivity> activityByMonth) {

    public record SkillCount(String skill, long offers) {
    }

    public record MonthlyActivity(String month, long newUsers, long newOffers, long applications) {
    }
}
