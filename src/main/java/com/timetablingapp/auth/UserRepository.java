package com.timetablingapp.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by email (for login).
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if an email already exists (for uniqueness validation).
     */
    boolean existsByEmail(String email);
}
