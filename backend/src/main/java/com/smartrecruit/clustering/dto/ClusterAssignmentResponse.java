package com.smartrecruit.clustering.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Attribution d'un candidat à un cluster (sans données personnelles des autres). */
public record ClusterAssignmentResponse(
        Long candidateId,
        Integer clusterId,
        String clusterDescription,
        List<String> dominantTerms,
        Double distanceToCentroid,
        String modelVersion,
        Integer nClusters,
        LocalDateTime assignedAt) {
}
