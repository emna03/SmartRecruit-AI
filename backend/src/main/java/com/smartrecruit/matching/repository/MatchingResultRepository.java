package com.smartrecruit.matching.repository;

import com.smartrecruit.matching.entity.MatchingResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchingResultRepository extends JpaRepository<MatchingResult, Long> {

    Optional<MatchingResult> findByCandidateIdAndJobOfferId(Long candidateId, Long jobOfferId);

    /** Classement des candidats d'une offre, du meilleur score au moins bon. */
    List<MatchingResult> findByJobOfferIdOrderByScoreDesc(Long jobOfferId);
}
