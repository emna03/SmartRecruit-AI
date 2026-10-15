package com.smartrecruit.clustering.controller;

import com.smartrecruit.clustering.dto.AiClusteringSummaryResponse;
import com.smartrecruit.clustering.dto.AiClusteringTrainResponse;
import com.smartrecruit.clustering.dto.ClusterAssignmentResponse;
import com.smartrecruit.clustering.service.ClusteringService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Clustering K-Means : admin (entraînement) et recruteur/admin (résumé / attribution).
 * Aucune donnée personnelle des autres candidats n'est exposée au candidat lui-même.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ClusteringController {

    private final ClusteringService clusteringService;

    @GetMapping({"/admin/clustering/summary", "/recruiter/clustering/summary"})
    @PreAuthorize("hasAnyRole('ADMIN','RECRUTEUR')")
    public AiClusteringSummaryResponse summary() {
        return clusteringService.summary();
    }

    @PostMapping("/admin/clustering/train")
    @PreAuthorize("hasRole('ADMIN')")
    public AiClusteringTrainResponse train() {
        return clusteringService.trainAndAssignAll();
    }

    @PostMapping({"/admin/clustering/candidates/{candidateId}/assign",
            "/recruiter/clustering/candidates/{candidateId}/assign"})
    @PreAuthorize("hasAnyRole('ADMIN','RECRUTEUR')")
    public ClusterAssignmentResponse assign(@PathVariable Long candidateId) {
        return clusteringService.assignCandidate(candidateId);
    }
}
