package com.timetablingapp.schedule.validate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ValidateLockRepository extends JpaRepository<ValidateLock, Long> {

    /** Latest row by created_at — mirrors ->latest('created_at')->first(). */
    Optional<ValidateLock> findFirstByOrderByCreatedAtDesc();
}
