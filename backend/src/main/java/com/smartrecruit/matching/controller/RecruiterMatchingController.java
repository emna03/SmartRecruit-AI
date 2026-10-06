package com.smartrecruit.matching.controller;

import com.smartrecruit.matching.dto.MatchingRefreshResponse;
import com.smartrecruit.matching.service.MatchingService;
import com.smartrecruit.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recruiter/jobs")
@RequiredArgsConstructor
public class RecruiterMatchingController {

    private final MatchingService matchingService;

    /** Recalcule les scores des candidats de l'offre et renvoie les candidatures classées par score. */
    @PostMapping("/{jobId}/matching")
    public MatchingRefreshResponse refresh(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long jobId) {
        return matchingService.refreshForOffer(user.getId(), jobId);
    }
}
