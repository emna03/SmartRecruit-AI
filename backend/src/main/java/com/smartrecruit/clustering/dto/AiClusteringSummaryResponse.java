package com.smartrecruit.clustering.dto;

import java.util.List;

public record AiClusteringSummaryResponse(
        boolean available,
        String algorithm,
        String modelVersion,
        String embeddingModel,
        Integer nClusters,
        Integer nProfiles,
        Double silhouette,
        Integer chosenK,
        String trainedAt,
        List<AiClusteringTrainResponse.ClusterItem> clusters,
        String note) {
}
