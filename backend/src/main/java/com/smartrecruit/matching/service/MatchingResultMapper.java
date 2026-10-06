package com.smartrecruit.matching.service;

import com.smartrecruit.cv.entity.Cv;
import com.smartrecruit.cv.entity.CvParsingStatus;
import com.smartrecruit.matching.dto.AiMatchingResult;
import com.smartrecruit.matching.dto.MatchedSkill;
import com.smartrecruit.matching.dto.MatchingResultResponse;
import com.smartrecruit.matching.entity.MatchingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Conversion entre le résultat du service IA, l'entité MatchingResult (MySQL) et la réponse de l'API. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingResultMapper {

    private static final TypeReference<List<MatchedSkill>> MATCHED = new TypeReference<>() { };
    private static final TypeReference<List<String>> NAMES = new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public void apply(MatchingResult entity, AiMatchingResult result) {
        entity.setScore(decimal(result.score()));
        entity.setSkillsScore(decimal(result.skillsScore()));
        entity.setExperienceScore(decimal(result.experienceScore()));
        entity.setEducationScore(decimal(result.educationScore()));
        entity.setMatchedSkills(objectMapper.writeValueAsString(result.matchedSkills()));
        entity.setMissingSkills(objectMapper.writeValueAsString(result.missingSkills()));
        entity.setExplanation(result.explanation());
        entity.setAlgorithmVersion(result.algorithmVersion());
        entity.setComputedAt(LocalDateTime.now(ZoneOffset.UTC));
    }

    /** {@code cv} : CV actuel du candidat, pour signaler un score calculé avant sa dernière analyse. */
    public MatchingResultResponse toResponse(MatchingResult entity, Cv cv) {
        if (entity == null) {
            return null;
        }
        return new MatchingResultResponse(entity.getScore().doubleValue(), number(entity.getSkillsScore()),
                number(entity.getExperienceScore()), number(entity.getEducationScore()),
                read(entity, entity.getMatchedSkills(), MATCHED), read(entity, entity.getMissingSkills(), NAMES),
                entity.getExplanation(), entity.getAlgorithmVersion(), entity.getComputedAt(), isStale(entity, cv));
    }

    static boolean isStale(MatchingResult entity, Cv cv) {
        return cv == null || isStale(entity.getComputedAt(), cv.getParsingStatus(), cv.getParsedAt());
    }

    /** Score calculé avant la dernière analyse du CV, ou CV qui n'est plus analysé ({@code cvStatus} null : pas de CV). */
    public static boolean isStale(LocalDateTime computedAt, CvParsingStatus cvStatus, LocalDateTime cvParsedAt) {
        return cvStatus != CvParsingStatus.TERMINE || cvParsedAt == null || computedAt.isBefore(cvParsedAt);
    }

    private <T> List<T> read(MatchingResult entity, String json, TypeReference<List<T>> type) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            log.error("Résultat de matching {} illisible : {}", entity.getId(), e.getClass().getSimpleName());
            return List.of();
        }
    }

    private static BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static Double number(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
