package com.smartrecruit.application.repository;

import com.smartrecruit.application.entity.Application;
import com.smartrecruit.application.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByCandidateIdAndJobOfferId(Long candidateId, Long jobOfferId);

    List<Application> findByCandidateId(Long candidateId);

    List<Application> findByJobOfferId(Long jobOfferId);

    long countByCandidateIdAndStatus(Long candidateId, ApplicationStatus status);
}
