package com.smartrecruit.application.controller;

import com.smartrecruit.application.dto.ApplicationDetailResponse;
import com.smartrecruit.application.dto.RecruiterApplicationResponse;
import com.smartrecruit.application.dto.UpdateApplicationStatusRequest;
import com.smartrecruit.application.service.ApplicationService;
import com.smartrecruit.security.AppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recruiter")
@RequiredArgsConstructor
public class RecruiterApplicationController {

    private final ApplicationService applicationService;

    @GetMapping("/jobs/{jobId}/applications")
    public List<RecruiterApplicationResponse> listForOffer(@AuthenticationPrincipal AppUserDetails user,
                                                           @PathVariable Long jobId) {
        return applicationService.findForOffer(user.getId(), jobId);
    }

    @GetMapping("/applications/{id}")
    public ApplicationDetailResponse get(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        return applicationService.getForRecruiter(user.getId(), id);
    }

    @PatchMapping("/applications/{id}/status")
    public ApplicationDetailResponse updateStatus(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                                                  @Valid @RequestBody UpdateApplicationStatusRequest request) {
        return applicationService.updateStatus(user.getId(), id, request.status());
    }
}
