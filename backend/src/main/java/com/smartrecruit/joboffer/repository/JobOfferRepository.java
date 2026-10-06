package com.smartrecruit.joboffer.repository;

import com.smartrecruit.joboffer.entity.JobOffer;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long>, JpaSpecificationExecutor<JobOffer> {

    List<JobOffer> findByStatus(JobOfferStatus status);

    List<JobOffer> findByCompanyId(Long companyId);

    List<JobOffer> findByRecruiterId(Long recruiterId);
}
