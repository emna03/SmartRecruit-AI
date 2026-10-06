package com.smartrecruit.user.repository;

import com.smartrecruit.user.entity.RoleName;
import com.smartrecruit.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRoleName(RoleName roleName);
}
