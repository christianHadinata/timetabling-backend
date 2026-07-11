package com.timetablingapp.activity.constraint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityConstraintRepository extends JpaRepository<ActivityConstraint, Integer> {

    /** All constraints for one activity (both list + response mapping). */
    List<ActivityConstraint> findByActivity_Id(Integer activityId);

    /** Constraints of one kind for one activity. */
    List<ActivityConstraint> findByActivity_IdAndType(Integer activityId, ConstraintType type);

    /**
     * Soft-delete every constraint of an activity (Spring Data derived delete loads
     * each row and calls delete(), so @SQLDelete is honored — matches Laravel's
     * ActivityConstraint::where('activity_id',$id)->delete()).
     */
    void deleteByActivity_Id(Integer activityId);

    /**
     * Delete-guard for LecturerService: is any activity assigned this lecturer NIK?
     * Mirrors Laravel LecturerController@destroy check.
     */
    boolean existsByTypeAndValue(ConstraintType type, String value);
}
