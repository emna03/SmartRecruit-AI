package com.smartrecruit.application.entity;

import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.common.entity.AuditableEntity;
import com.smartrecruit.joboffer.entity.JobOffer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Candidature : un candidat ne peut postuler qu'une fois à une même offre. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_application_candidate_offer",
                columnNames = {"candidate_id", "job_offer_id"}))
public class Application extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private CandidateProfile candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_offer_id", nullable = false)
    private JobOffer jobOffer;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.EN_ATTENTE;

    @Column(name = "cover_letter", columnDefinition = "TEXT")
    private String coverLetter;
}
