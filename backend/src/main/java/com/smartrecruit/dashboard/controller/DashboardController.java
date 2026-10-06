package com.smartrecruit.dashboard.controller;

import com.smartrecruit.dashboard.dto.AdminDashboardResponse;
import com.smartrecruit.dashboard.dto.CandidateDashboardResponse;
import com.smartrecruit.dashboard.dto.RecruiterDashboardResponse;
import com.smartrecruit.dashboard.service.DashboardService;
import com.smartrecruit.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Un endpoint par rôle ; le préfixe du chemin impose le rôle (SecurityConfig), le périmètre vient du jeton. */
@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/api/candidate/dashboard")
    public CandidateDashboardResponse candidate(@AuthenticationPrincipal AppUserDetails user) {
        return dashboardService.candidate(user.getId());
    }

    @GetMapping("/api/recruiter/dashboard")
    public RecruiterDashboardResponse recruiter(@AuthenticationPrincipal AppUserDetails user) {
        return dashboardService.recruiter(user.getId());
    }

    @GetMapping("/api/admin/dashboard")
    public AdminDashboardResponse admin() {
        return dashboardService.admin();
    }
}
