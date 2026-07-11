package com.timetablingapp.course.constraint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseConstraintRepository extends JpaRepository<CourseConstraint, Integer> {

    /**
     * Find all constraints where this course is the primary course.
     * Mirrors Laravel: CourseConstraint::where('course_id', $courseId)->get()
     */
    List<CourseConstraint> findByCourseId(Integer courseId);

    /**
     * Find all constraints where this course is the "with" course.
     * Mirrors Laravel: CourseConstraint::where('with_course_id', $courseId)->get()
     */
    List<CourseConstraint> findByWithCourseId(Integer courseId);

    /**
     * Find course constraints (not semester constraints) for a course.
     * Mirrors Laravel: CourseConstraint::where('course_id', $courseId)->whereNull('with_semester')->get()
     */
    List<CourseConstraint> findByCourseIdAndWithSemesterIsNull(Integer courseId);

    /**
     * Find constraints where this course is the "with_course_id" (reverse lookup).
     * Mirrors Laravel: CourseConstraint::where('with_course_id', $courseId)->whereNull('with_semester')->get()
     */
    List<CourseConstraint> findByWithCourseIdAndWithSemesterIsNull(Integer withCourseId);

    /**
     * Find semester constraints for a course.
     * Mirrors Laravel: CourseConstraint::where('course_id', $courseId)->whereNull('with_course_id')->get()
     */
    List<CourseConstraint> findByCourseIdAndWithCourseIsNull(Integer courseId);

    /**
     * Delete all constraints where the course is the primary course.
     * Mirrors Laravel: CourseConstraint::where('course_id', $thisCourseId)->delete()
     */
    void deleteByCourseId(Integer courseId);

    /**
     * Delete all constraints where the course is the "with" course.
     * Mirrors Laravel: CourseConstraint::where('with_course_id', $thisCourseId)->delete()
     */
    void deleteByWithCourseId(Integer courseId);
}
