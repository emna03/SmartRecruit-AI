package com.smartrecruit.company.repository;

import com.smartrecruit.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Company> findByNameIgnoreCase(String name);
}
