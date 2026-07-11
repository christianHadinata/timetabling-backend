package com.timetablingapp.activity.paralel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityParalelRepository extends JpaRepository<ActivityParalel, Integer> {

    /** Mirrors ActivityParalelRepository::getParalelCoursesWith($actId). */
    List<ActivityParalel> findByActivityIdOrWithActivityId(Integer activityId, Integer withActivityId);

    /** Mirrors ActivityParalelRepository::deleteAll($actId). */
    @Modifying
    @Query("DELETE FROM ActivityParalel p WHERE p.activityId = :id OR p.withActivityId = :id")
    void deleteAllForActivity(@Param("id") Integer activityId);
}
