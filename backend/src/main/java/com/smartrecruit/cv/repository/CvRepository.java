package com.smartrecruit.cv.repository;

import com.smartrecruit.cv.entity.Cv;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CvRepository extends JpaRepository<Cv, Long> {

    Optional<Cv> findByCandidateId(Long candidateId);
}
