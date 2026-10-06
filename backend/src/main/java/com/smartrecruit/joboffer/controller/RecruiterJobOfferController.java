package com.smartrecruit.joboffer.controller;

import com.smartrecruit.joboffer.dto.JobOfferRequest;
import com.smartrecruit.joboffer.dto.JobOfferResponse;
import com.smartrecruit.joboffer.dto.UpdateJobOfferStatusRequest;
import com.smartrecruit.joboffer.service.JobOfferService;
import com.smartrecruit.security.AppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Offres de l'entreprise du recruteur connecté. */
@RestController
@RequestMapping("/api/recruiter/jobs")
@RequiredArgsConstructor
public class RecruiterJobOfferController {

    private final JobOfferService jobOfferService;

    @GetMapping
    public List<JobOfferResponse> list(@AuthenticationPrincipal AppUserDetails user) {
        return jobOfferService.findMine(user.getId());
    }

    @GetMapping("/{id}")
    public JobOfferResponse get(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        return jobOfferService.getMine(user.getId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobOfferResponse create(@AuthenticationPrincipal AppUserDetails user,
                                   @Valid @RequestBody JobOfferRequest request) {
        return jobOfferService.create(user.getId(), request);
    }

    @PutMapping("/{id}")
    public JobOfferResponse update(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                                   @Valid @RequestBody JobOfferRequest request) {
        return jobOfferService.update(user.getId(), id, request);
    }

    @PatchMapping("/{id}/status")
    public JobOfferResponse updateStatus(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                                         @Valid @RequestBody UpdateJobOfferStatusRequest request) {
        return jobOfferService.updateStatus(user.getId(), id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        jobOfferService.delete(user.getId(), id);
    }
}
