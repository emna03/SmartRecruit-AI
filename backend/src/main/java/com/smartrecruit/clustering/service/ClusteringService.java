package com.smartrecruit.clustering.service;

import com.smartrecruit.ai.AiServiceClient;
import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.candidate.repository.CandidateProfileRepository;
import com.smartrecruit.clustering.dto.AiClusteringAssignRequest;
import com.smartrecruit.clustering.dto.AiClusteringAssignResponse;
import com.smartrecruit.clustering.dto.AiClusteringSummaryResponse;
import com.smartrecruit.clustering.dto.AiClusteringTrainRequest;
import com.smartrecruit.clustering.dto.AiClusteringTrainResponse;
import com.smartrecruit.clustering.dto.ClusterAssignmentResponse;
import com.smartrecruit.common.exception.BadRequestException;
import com.smartrecruit.common.exception.ResourceNotFoundException;
import com.smartrecruit.cv.dto.CvParseResult;
import com.smartrecruit.cv.service.CvService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Clustering K-Means des profils candidats (données professionnelles anonymisées uniquement).
 * Le candidat ne peut pas consulter les clusters des autres.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusteringService {

    static final String TRAIN_PATH = "/api/ml/clustering/train";
    static final String ASSIGN_PATH = "/api/ml/clustering/assign";
    static final String SUMMARY_PATH = "/api/ml/clustering/summary";

    private final CandidateProfileRepository candidateProfileRepository;
    private final CvService cvService;
    private final AiServiceClient aiClient;

    @Transactional(readOnly = true)
    public AiClusteringSummaryResponse summary() {
        return aiClient.get(SUMMARY_PATH, AiClusteringSummaryResponse.class);
    }

    /**
     * Entraîne K-Means sur tous les CV analysés (profils anonymisés), puis réattribue chaque candidat.
     */
    @Transactional
    public AiClusteringTrainResponse trainAndAssignAll() {
        List<CandidateProfile> candidates = candidateProfileRepository.findAll();
        List<AiClusteringTrainRequest.Profile> profiles = new ArrayList<>();
        List<CandidateProfile> withAnalysis = new ArrayList<>();
        for (CandidateProfile profile : candidates) {
            cvService.analysisOf(profile.getCv()).ifPresent(analysis -> {
                profiles.add(toProfile(analysis));
                withAnalysis.add(profile);
            });
        }
        if (profiles.size() < 6) {
            throw new BadRequestException(
                    "Dataset insuffisant pour un clustering crédible : au moins 6 CV analysés sont requis (actuellement "
                            + profiles.size() + ").");
        }
        AiClusteringTrainResponse trained = aiClient.post(TRAIN_PATH, new AiClusteringTrainRequest(profiles),
                AiClusteringTrainResponse.class);
        for (CandidateProfile profile : withAnalysis) {
            assignInternal(profile);
        }
        log.info("K-Means entraîné : K={}, profils={}", trained.nClusters(), trained.nProfiles());
        return trained;
    }

    @Transactional
    public ClusterAssignmentResponse assignCandidate(Long candidateId) {
        CandidateProfile profile = candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidat introuvable"));
        return assignInternal(profile);
    }

    private ClusterAssignmentResponse assignInternal(CandidateProfile profile) {
        CvParseResult analysis = cvService.analysisOf(profile.getCv())
                .orElseThrow(() -> new BadRequestException("CV non analysé : clustering impossible"));
        AiClusteringAssignResponse ai = aiClient.post(ASSIGN_PATH,
                new AiClusteringAssignRequest(toProfile(analysis)), AiClusteringAssignResponse.class);
        profile.setClusterId(ai.clusterId());
        profile.setClusterDescription(ai.clusterDescription());
        profile.setClusterAssignedAt(LocalDateTime.now(ZoneOffset.UTC));
        candidateProfileRepository.save(profile);
        return new ClusterAssignmentResponse(
                profile.getId(), ai.clusterId(), ai.clusterDescription(), ai.dominantTerms(),
                ai.distanceToCentroid(), ai.modelVersion(), ai.nClusters(), profile.getClusterAssignedAt());
    }

    static AiClusteringTrainRequest.Profile toProfile(CvParseResult analysis) {
        return new AiClusteringTrainRequest.Profile(
                analysis.skills().stream().map(CvParseResult.DetectedSkill::name).distinct().toList(),
                analysis.technologies().stream().map(CvParseResult.DetectedSkill::name).distinct().toList(),
                analysis.educations().stream()
                        .map(e -> new AiClusteringTrainRequest.Education(
                                e.degree(), e.fieldOfStudy(), e.institution(),
                                e.level() == null ? null : e.level().name()))
                        .toList(),
                analysis.experiences().stream()
                        .map(e -> new AiClusteringTrainRequest.Experience(
                                e.jobTitle(), e.company(), e.durationMonths(), e.description()))
                        .toList(),
                analysis.languages().stream()
                        .map(l -> new AiClusteringTrainRequest.Language(l.name(), l.level(), l.cefr()))
                        .toList(),
                analysis.totalExperienceMonths(),
                analysis.highestEducationLevel() == null ? null : analysis.highestEducationLevel().name());
    }
}
