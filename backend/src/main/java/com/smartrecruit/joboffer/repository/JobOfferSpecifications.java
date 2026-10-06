package com.smartrecruit.joboffer.repository;

import com.smartrecruit.joboffer.dto.JobSearchCriteria;
import com.smartrecruit.joboffer.entity.JobOffer;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import com.smartrecruit.skill.entity.Skill;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JobOfferSpecifications {

    private JobOfferSpecifications() {
    }

    /** Offres PUBLIÉES correspondant à tous les filtres renseignés. */
    public static Specification<JobOffer> publishedMatching(JobSearchCriteria c) {
        List<Specification<JobOffer>> specs = new ArrayList<>();
        specs.add((root, query, cb) -> cb.equal(root.get("status"), JobOfferStatus.PUBLIEE));

        if (hasText(c.keyword())) {
            String pattern = like(c.keyword());
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)));
        }
        if (hasText(c.location())) {
            String pattern = like(c.location());
            specs.add((root, query, cb) -> cb.like(cb.lower(root.get("location")), pattern));
        }
        if (c.contractType() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("contractType"), c.contractType()));
        }
        if (c.educationLevel() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("educationLevel"), c.educationLevel()));
        }
        if (c.maxExperienceYears() != null) {
            specs.add((root, query, cb) -> cb.le(root.get("minExperienceYears"), c.maxExperienceYears()));
        }
        if (c.companyId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("company").get("id"), c.companyId()));
        }
        if (hasText(c.skill())) {
            String skill = c.skill().trim().toLowerCase(Locale.ROOT);
            specs.add((root, query, cb) -> {
                query.distinct(true);
                Join<JobOffer, Skill> skills = root.join("requiredSkills");
                return cb.equal(cb.lower(skills.get("name")), skill);
            });
        }
        return Specification.allOf(specs);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String like(String value) {
        String escaped = value.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "");
        return "%" + escaped + "%";
    }
}
