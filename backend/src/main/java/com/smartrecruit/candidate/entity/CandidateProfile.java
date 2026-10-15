package com.smartrecruit.candidate.entity;

import com.smartrecruit.application.entity.Application;
import com.smartrecruit.common.entity.AuditableEntity;
import com.smartrecruit.common.model.EducationLevel;
import com.smartrecruit.cv.entity.Cv;
import com.smartrecruit.skill.entity.Skill;
import com.smartrecruit.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "candidate_profiles")
public class CandidateProfile extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 150)
    private String headline;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String location;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "education_level", length = 30)
    private EducationLevel educationLevel;

    /** Cluster K-Means (interprétation non certaine) ; null si non attribué. */
    @Column(name = "cluster_id")
    private Integer clusterId;

    @Column(name = "cluster_description", length = 255)
    private String clusterDescription;

    @Column(name = "cluster_assigned_at")
    private java.time.LocalDateTime clusterAssignedAt;

    /** Compétences déclarées manuellement par le candidat. */
    @ManyToMany
    @JoinTable(name = "candidate_skills",
            joinColumns = @JoinColumn(name = "candidate_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skills = new HashSet<>();

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Education> educations = new ArrayList<>();

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Experience> experiences = new ArrayList<>();

    @OneToOne(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Cv cv;

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.REMOVE)
    private List<Application> applications = new ArrayList<>();

    public void addEducation(Education education) {
        educations.add(education);
        education.setCandidate(this);
    }

    public void removeEducation(Education education) {
        educations.remove(education);
        education.setCandidate(null);
    }

    public void addExperience(Experience experience) {
        experiences.add(experience);
        experience.setCandidate(this);
    }

    public void removeExperience(Experience experience) {
        experiences.remove(experience);
        experience.setCandidate(null);
    }

    public void attachCv(Cv newCv) {
        this.cv = newCv;
        if (newCv != null) {
            newCv.setCandidate(this);
        }
    }
}
