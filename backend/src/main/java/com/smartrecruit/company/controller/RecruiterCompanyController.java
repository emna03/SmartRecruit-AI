package com.smartrecruit.company.controller;

import com.smartrecruit.company.dto.CompanyRequest;
import com.smartrecruit.company.dto.CompanyResponse;
import com.smartrecruit.company.service.CompanyService;
import com.smartrecruit.security.AppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Entreprise du recruteur connecté. */
@RestController
@RequestMapping("/api/recruiter/company")
@RequiredArgsConstructor
public class RecruiterCompanyController {

    private final CompanyService companyService;

    @GetMapping
    public CompanyResponse get(@AuthenticationPrincipal AppUserDetails user) {
        return companyService.getMyCompany(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@AuthenticationPrincipal AppUserDetails user,
                                  @Valid @RequestBody CompanyRequest request) {
        return companyService.createMyCompany(user.getId(), request);
    }

    @PutMapping
    public CompanyResponse update(@AuthenticationPrincipal AppUserDetails user,
                                  @Valid @RequestBody CompanyRequest request) {
        return companyService.updateMyCompany(user.getId(), request);
    }
}
