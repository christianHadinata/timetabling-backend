package com.timetablingapp.result;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResultRepository extends JpaRepository<Result, Integer> {

    /** Results for one semester (timetable view / export). */
    List<Result> findBySemester_Id(Integer semesterId);

    /**
     * Faculty-scoped export query: results in a semester with a given validity flag whose
     * activity's course belongs to one of the given jurusans. Mirrors ResultsExcel::download's
     * Result::where(semester,valid)->whereHas(activity.course, jurusan_id in Jurusan::jurusanIds()).
     */
    @Query("""
            select r from Result r
              join r.activity a
              join a.course c
             where r.semester.id = :sem and r.valid = :valid and c.jurusan.id in :jurusanIds
            """)
    List<Result> findForExport(@Param("sem") Integer semesterId,
                               @Param("valid") boolean valid,
                               @Param("jurusanIds") List<Integer> jurusanIds);

    /** Existing result(s) for an activity key — result import matches on (code, class, session). */
    @Query("""
            select r from Result r
              join r.activity a
              join a.course c
             where c.code = :code and a.courseClass = :cls and a.courseSession = :ses
            """)
    List<Result> findByActivityKey(@Param("code") String courseCode,
                                   @Param("cls") String courseClass,
                                   @Param("ses") Integer courseSession);

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
