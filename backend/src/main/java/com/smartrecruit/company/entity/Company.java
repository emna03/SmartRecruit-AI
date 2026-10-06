package com.smartrecruit.company.entity;

import com.smartrecruit.common.entity.AuditableEntity;
import com.smartrecruit.joboffer.entity.JobOffer;
import com.smartrecruit.recruiter.entity.RecruiterProfile;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "companies")
public class Company extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String sector;

    @Column(length = 255)
    private String website;

    @Column(length = 150)
    private String location;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobOffer> jobOffers = new ArrayList<>();

    @OneToMany(mappedBy = "company")
    private List<RecruiterProfile> recruiters = new ArrayList<>();

    public void addJobOffer(JobOffer offer) {
        jobOffers.add(offer);
        offer.setCompany(this);
    }
}
