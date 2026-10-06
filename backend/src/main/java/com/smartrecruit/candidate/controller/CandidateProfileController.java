package com.smartrecruit.candidate.controller;

import com.smartrecruit.candidate.dto.CandidateProfileResponse;
import com.smartrecruit.candidate.dto.EducationRequest;
import com.smartrecruit.candidate.dto.ExperienceRequest;
import com.smartrecruit.candidate.dto.UpdateCandidateProfileRequest;
import com.smartrecruit.candidate.service.CandidateProfileService;
import com.smartrecruit.security.AppUserDetails;
import com.smartrecruit.skill.dto.SkillNamesRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Profil du candidat connecté. Chaque opération renvoie le profil complet mis à jour. */
@RestController
@RequestMapping("/api/candidate/profile")
@RequiredArgsConstructor
public class CandidateProfileController {

    private final CandidateProfileService profileService;

    @GetMapping
    public CandidateProfileResponse get(@AuthenticationPrincipal AppUserDetails user) {
        return profileService.getProfile(user.getId());
    }

    @PutMapping
    public CandidateProfileResponse update(@AuthenticationPrincipal AppUserDetails user,
                                           @Valid @RequestBody UpdateCandidateProfileRequest request) {
        return profileService.updateProfile(user.getId(), request);
    }

    @PutMapping("/skills")
    public CandidateProfileResponse replaceSkills(@AuthenticationPrincipal AppUserDetails user,
                                                  @Valid @RequestBody SkillNamesRequest request) {
        return profileService.replaceSkills(user.getId(), request.skills());
    }

    @PostMapping("/educations")
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateProfileResponse addEducation(@AuthenticationPrincipal AppUserDetails user,
                                                 @Valid @RequestBody EducationRequest request) {
        return profileService.addEducation(user.getId(), request);
    }

    @PutMapping("/educations/{id}")
    public CandidateProfileResponse updateEducation(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                                                    @Valid @RequestBody EducationRequest request) {
        return profileService.updateEducation(user.getId(), id, request);
    }

    @DeleteMapping("/educations/{id}")
    public CandidateProfileResponse deleteEducation(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        return profileService.deleteEducation(user.getId(), id);
    }

    @PostMapping("/experiences")
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateProfileResponse addExperience(@AuthenticationPrincipal AppUserDetails user,
                                                  @Valid @RequestBody ExperienceRequest request) {
        return profileService.addExperience(user.getId(), request);
    }

    @PutMapping("/experiences/{id}")
    public CandidateProfileResponse updateExperience(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                                                     @Valid @RequestBody ExperienceRequest request) {
        return profileService.updateExperience(user.getId(), id, request);
    }

    @DeleteMapping("/experiences/{id}")
    public CandidateProfileResponse deleteExperience(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        return profileService.deleteExperience(user.getId(), id);
    }
}
