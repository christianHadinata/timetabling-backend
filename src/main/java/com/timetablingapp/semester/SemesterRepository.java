package com.timetablingapp.semester;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, Integer> {

    /**
     * Find the current semester (where current = true).
     */
    Optional<Semester> findByCurrentTrue();

    /**
     * Find the latest semester by ID (highest ID = most recent).
     * Mirrors Laravel: Semester::latest('id')->first()
     */
    Optional<Semester> findFirstByOrderByIdDesc();

    /**
     * Find all semesters ordered by ID descending.
     * Mirrors Laravel: Semester::orderByDesc('id')->get()
     */
    List<Semester> findAllByOrderByIdDesc();

    /**
     * Set all semesters to current = false.
     * Mirrors Laravel: $this->model->where('current', true)->update(['current' => false])
     */
    @Modifying
    @Query("UPDATE Semester s SET s.current = false WHERE s.current = true")
    void unsetAllCurrent();

    /**
     * Set a specific semester as current.
     * Mirrors Laravel: $this->model->where('id', $id)->update(['current' => true])
     */
    @Modifying
    @Query("UPDATE Semester s SET s.current = true WHERE s.id = :id")
    void setCurrentById(@Param("id") Integer id);
}
