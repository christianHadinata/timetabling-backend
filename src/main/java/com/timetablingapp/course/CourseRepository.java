package com.timetablingapp.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Integer> {

    /**
     * Find a course by its unique code.
     * Mirrors Laravel: Course::where('code', $code)->first()
     */
    Optional<Course> findByCode(String code);

    /**
     * Check if a course code already exists.
     * Used for import duplicate detection.
     * Mirrors Laravel: CourseRepository.contains($code)
     */
    boolean existsByCode(String code);

    /**
     * Find all courses belonging to a specific jurusan.
     * Mirrors Laravel: Course::where("jurusan_id", $jurusanId)->get()
     */
    List<Course> findByJurusanId(Integer jurusanId);

    /**
     * Find all courses belonging to jurusans in the given list.
     * Mirrors Laravel: Course::whereIn("jurusan_id", Jurusan::jurusanIds())->get()
     */
    List<Course> findByJurusanIdIn(List<Integer> jurusanIds);
}
