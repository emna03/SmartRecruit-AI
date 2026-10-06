package com.smartrecruit.recruiter.controller;

import com.smartrecruit.recruiter.dto.RecruiterProfileResponse;
import com.smartrecruit.recruiter.dto.UpdateRecruiterProfileRequest;
import com.smartrecruit.recruiter.service.RecruiterProfileService;
import com.smartrecruit.security.AppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recruiter/profile")
@RequiredArgsConstructor
public class RecruiterProfileController {

    private final RecruiterProfileService profileService;

    @GetMapping
    public RecruiterProfileResponse get(@AuthenticationPrincipal AppUserDetails user) {
        return profileService.getProfile(user.getId());
    }

    @PutMapping
    public RecruiterProfileResponse update(@AuthenticationPrincipal AppUserDetails user,
                                           @Valid @RequestBody UpdateRecruiterProfileRequest request) {
        return profileService.updateProfile(user.getId(), request);
    }
}
