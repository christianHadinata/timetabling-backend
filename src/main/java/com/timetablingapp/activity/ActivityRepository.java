package com.timetablingapp.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Integer> {

    /**
     * Activities in a semester, restricted to the caller's faculty jurusans.
     * Mirrors ActivityController@index:
     *   Activity::whereHas('course', fn($q) => $q->whereIn('jurusan_id', Jurusan::jurusanIds()))
     *           ->where('semester_id', $sems)->get()
     */
    List<Activity> findBySemester_IdAndCourse_Jurusan_IdIn(Integer semesterId, List<Integer> jurusanIds);

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
