package com.smartrecruit.candidate.service;

import com.smartrecruit.candidate.dto.CandidateProfileResponse;
import com.smartrecruit.candidate.dto.EducationRequest;
import com.smartrecruit.candidate.dto.ExperienceRequest;
import com.smartrecruit.candidate.dto.UpdateCandidateProfileRequest;
import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.candidate.entity.Education;
import com.smartrecruit.candidate.entity.Experience;
import com.smartrecruit.candidate.repository.CandidateProfileRepository;
import com.smartrecruit.common.exception.BadRequestException;
import com.smartrecruit.common.exception.ResourceNotFoundException;
import static com.smartrecruit.common.util.Strings.blankToNull;
import com.smartrecruit.skill.service.SkillService;
import com.smartrecruit.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CandidateProfileService {

    private final CandidateProfileRepository candidateProfileRepository;
    private final SkillService skillService;

    /** Profil du candidat connecté (créé à l'inscription). */
    @Transactional(readOnly = true)
    public CandidateProfile getByUserId(Long userId) {
        return candidateProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil candidat introuvable"));
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse getProfile(Long userId) {
        return CandidateProfileResponse.from(getByUserId(userId));
    }

    @Transactional
    public CandidateProfileResponse updateProfile(Long userId, UpdateCandidateProfileRequest request) {
        CandidateProfile profile = getByUserId(userId);
        User user = profile.getUser();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        profile.setHeadline(blankToNull(request.headline()));
        profile.setSummary(blankToNull(request.summary()));
        profile.setPhone(blankToNull(request.phone()));
        profile.setLocation(blankToNull(request.location()));
        profile.setYearsOfExperience(request.yearsOfExperience());
        profile.setEducationLevel(request.educationLevel());
        return CandidateProfileResponse.from(profile);
    }

    /** Remplace l'ensemble des compétences déclarées. */
    @Transactional
    public CandidateProfileResponse replaceSkills(Long userId, List<String> skillNames) {
        CandidateProfile profile = getByUserId(userId);
        profile.getSkills().clear();
        profile.getSkills().addAll(skillService.resolve(skillNames));
        return CandidateProfileResponse.from(profile);
    }

    @Transactional
    public CandidateProfileResponse addEducation(Long userId, EducationRequest request) {
        CandidateProfile profile = getByUserId(userId);
        Education education = new Education();
        apply(education, request);
        profile.addEducation(education);
        candidateProfileRepository.flush();
        return CandidateProfileResponse.from(profile);
    }

    @Transactional
    public CandidateProfileResponse updateEducation(Long userId, Long educationId, EducationRequest request) {
        CandidateProfile profile = getByUserId(userId);
        apply(findEducation(profile, educationId), request);
        return CandidateProfileResponse.from(profile);
    }

    @Transactional
    public CandidateProfileResponse deleteEducation(Long userId, Long educationId) {
        CandidateProfile profile = getByUserId(userId);
        profile.removeEducation(findEducation(profile, educationId));
        return CandidateProfileResponse.from(profile);
    }

    @Transactional
    public CandidateProfileResponse addExperience(Long userId, ExperienceRequest request) {
        CandidateProfile profile = getByUserId(userId);
        Experience experience = new Experience();
        apply(experience, request);
        profile.addExperience(experience);
        candidateProfileRepository.flush();
        return CandidateProfileResponse.from(profile);
    }

    @Transactional
    public CandidateProfileResponse updateExperience(Long userId, Long experienceId, ExperienceRequest request) {
        CandidateProfile profile = getByUserId(userId);
        apply(findExperience(profile, experienceId), request);
        return CandidateProfileResponse.from(profile);
    }

    @Transactional
    public CandidateProfileResponse deleteExperience(Long userId, Long experienceId) {
        CandidateProfile profile = getByUserId(userId);
        profile.removeExperience(findExperience(profile, experienceId));
        return CandidateProfileResponse.from(profile);
    }

    // Une formation / expérience d'un autre candidat est traitée comme introuvable (pas de fuite d'information).
    private static Education findEducation(CandidateProfile profile, Long id) {
        return profile.getEducations().stream().filter(e -> e.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Formation introuvable"));
    }

    private static Experience findExperience(CandidateProfile profile, Long id) {
        return profile.getExperiences().stream().filter(e -> e.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Expérience introuvable"));
    }

    private static void apply(Education education, EducationRequest r) {
        checkDates(r.startDate(), r.endDate());
        education.setDegree(r.degree().trim());
        education.setFieldOfStudy(blankToNull(r.fieldOfStudy()));
        education.setInstitution(r.institution().trim());
        education.setStartDate(r.startDate());
        education.setEndDate(r.endDate());
        education.setDescription(blankToNull(r.description()));
    }

    private static void apply(Experience experience, ExperienceRequest r) {
        checkDates(r.startDate(), r.endDate());
        experience.setJobTitle(r.jobTitle().trim());
        experience.setCompanyName(r.companyName().trim());
        experience.setLocation(blankToNull(r.location()));
        experience.setStartDate(r.startDate());
        experience.setEndDate(r.endDate());
        experience.setDescription(blankToNull(r.description()));
    }

    private static void checkDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BadRequestException("La date de fin doit être postérieure à la date de début");
        }
    }
}
