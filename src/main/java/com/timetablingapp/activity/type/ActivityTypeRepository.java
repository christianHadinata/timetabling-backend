package com.timetablingapp.activity.type;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ActivityTypeRepository extends JpaRepository<ActivityType, Integer> {

    /** Resolve an activity type by its display name (Excel import). */
    Optional<ActivityType> findByName(String name);
}
