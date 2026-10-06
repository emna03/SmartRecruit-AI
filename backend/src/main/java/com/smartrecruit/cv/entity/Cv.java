package com.smartrecruit.cv.entity;

import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.common.entity.AuditableEntity;
import com.smartrecruit.skill.entity.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * CV PDF d'un candidat (un seul CV actif par candidat).
 * Le fichier est stocké sur disque (UPLOAD_DIR), seule sa référence est en base.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cvs")
public class Cv extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true)
    private CandidateProfile candidate;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "stored_file_name", nullable = false, unique = true)
    private String storedFileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
    private String extractedText;

    /** Réponse JSON validée du CV Parser (skills, education, experience, languages). */
    @Column(name = "extracted_data", columnDefinition = "LONGTEXT")
    private String extractedData;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "parsing_status", nullable = false, length = 20)
    private CvParsingStatus parsingStatus = CvParsingStatus.EN_COURS;

    @Column(name = "parsing_error", length = 500)
    private String parsingError;

    @Column(name = "parsed_at")
    private LocalDateTime parsedAt;

    /** Compétences extraites automatiquement par l'IA. */
    @ManyToMany
    @JoinTable(name = "cv_skills",
            joinColumns = @JoinColumn(name = "cv_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> extractedSkills = new HashSet<>();
}
