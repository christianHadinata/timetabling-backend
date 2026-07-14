package com.timetablingapp.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Integer> {

    /** Lookup an activity by its (course_code, class, session) key — used by result import. */
    Optional<Activity> findFirstByCourse_CodeAndCourseClassAndCourseSession(
            String courseCode, String courseClass, Integer courseSession);

    /**
     * Activities in a semester, restricted to the caller's faculty jurusans.
     * Mirrors ActivityController@index:
     *   Activity::whereHas('course', fn($q) => $q->whereIn('jurusan_id', Jurusan::jurusanIds()))
     *           ->where('semester_id', $sems)->get()
     */
    List<Activity> findBySemester_IdAndCourse_Jurusan_IdIn(Integer semesterId, List<Integer> jurusanIds);

    /**
     * Fetch-joined activities for GA scheduling — avoids N+1 when mapping
     * to AlgorithmActivity (course + activityType are touched for every row).
     */
    @Query("select distinct a from Activity a " +
           "join fetch a.course c join fetch a.activityType " +
           "where a.semester.id = :semesterId")
    List<Activity> findAllForScheduling(@Param("semesterId") Integer semesterId);

    /** All activities in a semester (used by revalidate / semester duplicate in later phases). */
    List<Activity> findBySemester_Id(Integer semesterId);

    /**
     * Paralel candidates: same tingkat + same jurusan + same semester.
     * Mirrors ActivityController::getParalelCandidates($tingkat, $jurusanId, $semester).
     */
    List<Activity> findBySemester_IdAndCourse_Jurusan_IdAndCourse_Tingkat(
            Integer semesterId, Integer jurusanId, Integer tingkat);

    /** Duplicate check — mirrors ActivityRepository::contains(). */
    boolean existsBySemester_IdAndCourse_CodeAndCourseClassAndCourseSession(
            Integer semesterId, String courseCode, String courseClass, Integer courseSession);

    /** Delete-guards. */
    boolean existsByCourse_Code(String courseCode);          // CourseService delete guard
    boolean existsByActivityType_Id(Integer activityTypeId); // ActivityTypeService delete guard
}
