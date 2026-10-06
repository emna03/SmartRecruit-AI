package com.smartrecruit.application.controller;

import com.smartrecruit.application.dto.ApplyRequest;
import com.smartrecruit.application.dto.CandidateApplicationResponse;
import com.smartrecruit.application.service.ApplicationService;
import com.smartrecruit.security.AppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/candidate/applications")
@RequiredArgsConstructor
public class CandidateApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateApplicationResponse apply(@AuthenticationPrincipal AppUserDetails user,
                                              @Valid @RequestBody ApplyRequest request) {
        return applicationService.apply(user.getId(), request);
    }

    @GetMapping
    public List<CandidateApplicationResponse> list(@AuthenticationPrincipal AppUserDetails user) {
        return applicationService.findMine(user.getId());
    }

    @GetMapping("/{id}")
    public CandidateApplicationResponse get(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        return applicationService.getMine(user.getId(), id);
    }
}
