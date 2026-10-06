package com.smartrecruit.joboffer.repository;

import com.smartrecruit.joboffer.entity.JobOffer;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long>, JpaSpecificationExecutor<JobOffer> {

    @Override
    @EntityGraph(attributePaths = "company")
    Page<JobOffer> findAll(Specification<JobOffer> spec, Pageable pageable);

    List<JobOffer> findByStatus(JobOfferStatus status);

    List<JobOffer> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<JobOffer> findByRecruiterId(Long recruiterId);
}
