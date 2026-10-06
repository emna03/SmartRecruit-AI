package com.smartrecruit.application.dto;

import com.smartrecruit.application.entity.Application;
import com.smartrecruit.application.entity.ApplicationStatus;
import com.smartrecruit.joboffer.entity.ContractType;
import com.smartrecruit.joboffer.entity.JobOffer;
import com.smartrecruit.joboffer.entity.JobOfferStatus;

import java.time.LocalDateTime;

/** Candidature vue par le candidat. */
public record CandidateApplicationResponse(
        Long id,
        ApplicationStatus status,
        String coverLetter,
        LocalDateTime appliedAt,
        LocalDateTime updatedAt,
        JobInfo job) {

    public record JobInfo(Long id, String title, String companyName, String location,
                          ContractType contractType, JobOfferStatus status) {
    }

    public static CandidateApplicationResponse from(Application a) {
        JobOffer o = a.getJobOffer();
        return new CandidateApplicationResponse(a.getId(), a.getStatus(), a.getCoverLetter(), a.getCreatedAt(),
                a.getUpdatedAt(),
                new JobInfo(o.getId(), o.getTitle(), o.getCompany().getName(), o.getLocation(),
                        o.getContractType(), o.getStatus()));
    }
}
