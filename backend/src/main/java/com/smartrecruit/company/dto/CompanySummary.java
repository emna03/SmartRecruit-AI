package com.smartrecruit.company.dto;

import com.smartrecruit.company.entity.Company;

public record CompanySummary(Long id, String name, String sector, String location, String website) {

    public static CompanySummary from(Company c) {
        return c == null ? null : new CompanySummary(c.getId(), c.getName(), c.getSector(), c.getLocation(), c.getWebsite());
    }
}
