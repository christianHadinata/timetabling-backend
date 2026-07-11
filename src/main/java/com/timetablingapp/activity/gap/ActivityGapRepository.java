package com.timetablingapp.activity.gap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityGapRepository extends JpaRepository<ActivityGap, Integer> {

    /** Mirrors ActivityGapRepository::getActivityGaps($actId). */
    List<ActivityGap> findByActivityIdOrWithActivityId(Integer activityId, Integer withActivityId);

    /** Mirrors ActivityGapRepository::deleteAll($actId). */
    @Modifying
    @Query("DELETE FROM ActivityGap g WHERE g.activityId = :id OR g.withActivityId = :id")
    void deleteAllForActivity(@Param("id") Integer activityId);
}
