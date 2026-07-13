package com.timetablingapp.result;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResultRepository extends JpaRepository<Result, Integer> {

    /** Results for one semester (timetable view / export). */
    List<Result> findBySemester_Id(Integer semesterId);

    /**
     * Bulk soft-delete every result of a semester.
     * Mirrors ResultRepository::deleteBasedOnSemesterId($semId).
     * Spring Data derived delete loads rows then delete()s each, honoring @SQLDelete.
     */
    void deleteBySemester_Id(Integer semesterId);

    /** Cascade support for ActivityService.delete(). */
    List<Result> findByActivity_Id(Integer activityId);

    void deleteByActivity_Id(Integer activityId);

    /**
     * Delete-guard for ActivityService: is this activity actually scheduled
     * (assigned to a room)? Mirrors ActivityController@destroy's check.
     */
    boolean existsByActivity_IdAndRoomIsNotNull(Integer activityId);

    /**
     * Delete-guard for RoomService: is any result using this room?
     * Mirrors Laravel RoomController@destroy: Result::where('room_id', $id)->get().
     */
    boolean existsByRoom_Id(Integer roomId);
}
