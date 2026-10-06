package com.smartrecruit.joboffer.repository;

import com.smartrecruit.joboffer.entity.JobOffer;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long>, JpaSpecificationExecutor<JobOffer> {

    @Override
    @EntityGraph(attributePaths = "company")
    Page<JobOffer> findAll(Specification<JobOffer> spec, Pageable pageable);

    List<JobOffer> findByStatus(JobOfferStatus status);

    List<JobOffer> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<JobOffer> findByRecruiterId(Long recruiterId);

    /** Identifiants des offres d'un statut, les plus récentes d'abord (même ordre que la liste publique). */
    @Query("select o.id from JobOffer o where o.status = :status order by o.createdAt desc, o.id desc")
    List<Long> findIdsByStatus(@Param("status") JobOfferStatus status, Pageable pageable);

    /** Offres avec leur entreprise et leurs compétences, chargées en une requête. */
    @EntityGraph(attributePaths = {"company", "requiredSkills"})
    List<JobOffer> findWithDetailsByIdIn(Collection<Long> ids);
}
