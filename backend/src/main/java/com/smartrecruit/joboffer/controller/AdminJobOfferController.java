package com.smartrecruit.joboffer.controller;

import com.smartrecruit.joboffer.dto.JobOfferResponse;
import com.smartrecruit.joboffer.dto.UpdateJobOfferStatusRequest;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import com.smartrecruit.joboffer.service.JobOfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/jobs")
@RequiredArgsConstructor
public class AdminJobOfferController {

    private final JobOfferService jobOfferService;

    @GetMapping
    public List<JobOfferResponse> list(@RequestParam(required = false) JobOfferStatus status) {
        return jobOfferService.findAll(status);
    }

    @PatchMapping("/{id}/status")
    public JobOfferResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateJobOfferStatusRequest request) {
        return jobOfferService.adminUpdateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        jobOfferService.adminDelete(id);
    }
}
