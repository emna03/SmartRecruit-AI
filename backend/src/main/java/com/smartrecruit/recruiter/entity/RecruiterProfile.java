package com.smartrecruit.recruiter.entity;

import com.smartrecruit.common.entity.AuditableEntity;
import com.smartrecruit.company.entity.Company;
import com.smartrecruit.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "recruiter_profiles")
public class RecruiterProfile extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Null tant que le recruteur n'a pas encore créé / rejoint son entreprise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(length = 30)
    private String phone;
}
