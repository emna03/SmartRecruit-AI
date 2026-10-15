package com.smartrecruit.clustering.dto;

import java.util.List;

public record AiClusteringTrainResponse(
        String algorithm,
        String modelVersion,
        String embeddingModel,
        int nClusters,
        int nProfiles,
        double silhouette,
        int chosenK,
        String trainedAt,
        List<ClusterItem> clusters,
        String note) {

    public record ClusterItem(int clusterId, int size, List<String> dominantTerms, String clusterDescription) {
    }
}
