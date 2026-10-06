package com.smartrecruit.application.dto;

import com.smartrecruit.application.entity.Application;
import com.smartrecruit.application.entity.ApplicationStatus;
import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.common.model.EducationLevel;
import com.smartrecruit.skill.service.SkillService;
import com.smartrecruit.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/** Candidature dans la liste du recruteur (résumé du candidat). */
public record RecruiterApplicationResponse(
        Long id,
        Long jobOfferId,
        String jobTitle,
        ApplicationStatus status,
        LocalDateTime appliedAt,
        ApplicantSummary candidate) {

    public record ApplicantSummary(Long candidateId, String firstName, String lastName, String email,
                                   String headline, String location, Integer yearsOfExperience,
                                   EducationLevel educationLevel, List<String> skills, boolean hasCv) {
    }

    public static RecruiterApplicationResponse from(Application a) {
        CandidateProfile p = a.getCandidate();
        User u = p.getUser();
        return new RecruiterApplicationResponse(a.getId(), a.getJobOffer().getId(), a.getJobOffer().getTitle(),
                a.getStatus(), a.getCreatedAt(),
                new ApplicantSummary(p.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), p.getHeadline(),
                        p.getLocation(), p.getYearsOfExperience(), p.getEducationLevel(),
                        SkillService.names(p.getSkills()), p.getCv() != null));
    }
}
