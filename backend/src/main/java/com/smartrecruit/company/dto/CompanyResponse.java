package com.smartrecruit.company.dto;

import com.smartrecruit.company.entity.Company;

import java.time.LocalDateTime;

public record CompanyResponse(
        Long id,
        String name,
        String description,
        String sector,
        String website,
        String location,
        int jobOfferCount,
        int recruiterCount,
        LocalDateTime createdAt) {

    public static CompanyResponse from(Company c) {
        return new CompanyResponse(c.getId(), c.getName(), c.getDescription(), c.getSector(), c.getWebsite(),
                c.getLocation(), c.getJobOffers().size(), c.getRecruiters().size(), c.getCreatedAt());
    }
}
