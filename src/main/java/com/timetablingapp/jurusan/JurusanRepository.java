package com.timetablingapp.jurusan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JurusanRepository extends JpaRepository<Jurusan, Integer> {

    /**
     * Find all jurusans belonging to a specific faculty.
     * Used for faculty-based filtering (non-admin users).
     * Mirrors Laravel: Jurusan::where(['faculty' => $faculty])->get()
     */
    List<Jurusan> findByFaculty(String faculty);

    /**
     * Find all jurusan IDs for a specific faculty.
     * Mirrors Laravel: Jurusan::jurusanIds()
     */
    List<Jurusan> findAllByFaculty(String faculty);
}
