package com.smartrecruit.candidate.dto;

import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.candidate.entity.Education;
import com.smartrecruit.candidate.entity.Experience;
import com.smartrecruit.common.model.EducationLevel;
import com.smartrecruit.skill.service.SkillService;
import com.smartrecruit.user.entity.User;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Profil complet d'un candidat (vu par lui-même, ou par un recruteur à qui il a postulé). */
public record CandidateProfileResponse(
        Long id,
        Long userId,
        String email,
        String firstName,
        String lastName,
        String headline,
        String summary,
        String phone,
        String location,
        Integer yearsOfExperience,
        EducationLevel educationLevel,
        List<String> skills,
        List<EducationResponse> educations,
        List<ExperienceResponse> experiences,
        boolean hasCv) {

    private static final Comparator<LocalDate> RECENT_FIRST = Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()).reversed();

    public static CandidateProfileResponse from(CandidateProfile p) {
        User u = p.getUser();
        return new CandidateProfileResponse(p.getId(), u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                p.getHeadline(), p.getSummary(), p.getPhone(), p.getLocation(), p.getYearsOfExperience(),
                p.getEducationLevel(), SkillService.names(p.getSkills()),
                p.getEducations().stream()
                        .sorted(Comparator.comparing(Education::getStartDate, RECENT_FIRST))
                        .map(EducationResponse::from).toList(),
                p.getExperiences().stream()
                        .sorted(Comparator.comparing(Experience::getStartDate, RECENT_FIRST))
                        .map(ExperienceResponse::from).toList(),
                p.getCv() != null);
    }
}
