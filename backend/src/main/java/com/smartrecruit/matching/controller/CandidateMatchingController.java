package com.smartrecruit.matching.controller;

import com.smartrecruit.matching.dto.CandidateMatchingResponse;
import com.smartrecruit.matching.service.MatchingService;
import com.smartrecruit.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/candidate/jobs")
@RequiredArgsConstructor
public class CandidateMatchingController {

    private final MatchingService matchingService;

    /** Compatibilité du candidat connecté avec l'offre, recalculée à partir de son CV analysé. */
    @GetMapping("/{jobId}/matching")
    public CandidateMatchingResponse get(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long jobId) {
        return matchingService.forCandidate(user.getId(), jobId);
    }
}
