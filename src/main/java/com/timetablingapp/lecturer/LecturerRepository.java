package com.timetablingapp.lecturer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LecturerRepository extends JpaRepository<Lecturer, Integer> {

    List<Lecturer> findAllByOrderByNikAsc();

    Optional<Lecturer> findByNik(String nik);

    boolean existsByNik(String nik);
}
