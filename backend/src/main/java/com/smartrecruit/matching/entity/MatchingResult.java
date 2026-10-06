package com.smartrecruit.matching.entity;

import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.common.entity.BaseEntity;
import com.smartrecruit.joboffer.entity.JobOffer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dernier score de compatibilité calculé entre un candidat et une offre.
 * Les sous-scores et listes de compétences rendent le score explicable.
 * Il existe indépendamment d'une candidature (le candidat peut consulter son score avant de postuler).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "matching_results",
        uniqueConstraints = @UniqueConstraint(name = "uk_matching_candidate_offer",
                columnNames = {"candidate_id", "job_offer_id"}))
public class MatchingResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private CandidateProfile candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_offer_id", nullable = false)
    private JobOffer jobOffer;

    /** Score global de 0 à 100. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "skills_score", precision = 5, scale = 2)
    private BigDecimal skillsScore;

    @Column(name = "experience_score", precision = 5, scale = 2)
    private BigDecimal experienceScore;

    @Column(name = "education_score", precision = 5, scale = 2)
    private BigDecimal educationScore;

    /** Tableau JSON des compétences trouvées, ex. ["Java","Angular"]. */
    @Column(name = "matched_skills", columnDefinition = "TEXT")
    private String matchedSkills;

    /** Tableau JSON des compétences manquantes. */
    @Column(name = "missing_skills", columnDefinition = "TEXT")
    private String missingSkills;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "algorithm_version", nullable = false, length = 20)
    private String algorithmVersion;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;
}
