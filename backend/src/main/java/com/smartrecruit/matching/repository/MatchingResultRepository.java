package com.smartrecruit.matching.repository;

import com.smartrecruit.matching.entity.MatchingResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchingResultRepository extends JpaRepository<MatchingResult, Long> {

    Optional<MatchingResult> findByCandidateIdAndJobOfferId(Long candidateId, Long jobOfferId);

    /** Classement des candidats d'une offre, du meilleur score au moins bon. */
    List<MatchingResult> findByJobOfferIdOrderByScoreDesc(Long jobOfferId);

    /** Scores calculés à partir d'un CV qui n'existe plus. */
    @Modifying(flushAutomatically = true)
    @Query("delete from MatchingResult m where m.candidate.id = :candidateId")
    int deleteByCandidateId(@Param("candidateId") Long candidateId);

    /** Scores calculés avec des critères d'offre qui ont changé. */
    @Modifying(flushAutomatically = true)
    @Query("delete from MatchingResult m where m.jobOffer.id = :jobOfferId")
    int deleteByJobOfferId(@Param("jobOfferId") Long jobOfferId);
}
