package com.smartrecruit.turnover.model;

import java.util.EnumSet;
import java.util.Set;

/** Département (variable Department du dataset IBM) et postes qui y existent dans l'entreprise fictive. */
public enum Department {
    SALES(EnumSet.of(JobRole.SALES_EXECUTIVE, JobRole.SALES_REPRESENTATIVE, JobRole.MANAGER)),
    RESEARCH_DEVELOPMENT(EnumSet.of(JobRole.RESEARCH_SCIENTIST, JobRole.LABORATORY_TECHNICIAN,
            JobRole.MANUFACTURING_DIRECTOR, JobRole.HEALTHCARE_REPRESENTATIVE, JobRole.RESEARCH_DIRECTOR,
            JobRole.MANAGER)),
    HUMAN_RESOURCES(EnumSet.of(JobRole.HUMAN_RESOURCES, JobRole.MANAGER));

    private final Set<JobRole> jobRoles;

    Department(Set<JobRole> jobRoles) {
        this.jobRoles = jobRoles;
    }

    public boolean hasJobRole(JobRole role) {
        return jobRoles.contains(role);
    }
}
