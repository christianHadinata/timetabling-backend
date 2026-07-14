package com.timetablingapp.lecturer.time;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;

@Repository
public interface LecturerTimeNARepository extends JpaRepository<LecturerTimeNA, Integer> {

    List<LecturerTimeNA> findByLecturer_IdOrderByDayAsc(Integer lecturerId);

    List<LecturerTimeNA> findByLecturer_IdAndType(Integer lecturerId, LecturerTimeType type);

    void deleteByLecturer_Id(Integer lecturerId);

    boolean existsByLecturer_Id(Integer lecturerId);

    /** Dedup guard for the time import — mirrors LecturerController@uploadExcelTime existence check. */
    boolean existsByLecturer_IdAndDayAndTypeAndStartTimeAndEndTime(
            Integer lecturerId, Integer day, LecturerTimeType type, LocalTime startTime, LocalTime endTime);
}
