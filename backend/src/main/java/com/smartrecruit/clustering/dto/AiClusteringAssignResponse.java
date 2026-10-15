package com.smartrecruit.clustering.dto;

import java.util.List;

public record AiClusteringAssignResponse(
        int clusterId,
        String clusterDescription,
        List<String> dominantTerms,
        double distanceToCentroid,
        String modelVersion,
        int nClusters) {
}
