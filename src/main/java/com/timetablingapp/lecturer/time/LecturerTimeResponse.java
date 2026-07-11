package com.timetablingapp.lecturer.time;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LecturerTimeResponse {

    private Integer id;
    private Integer lecturerId;
    private Integer day;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private LecturerTimeType type;

    public static LecturerTimeResponse fromEntity(LecturerTimeNA e) {
        return LecturerTimeResponse.builder()
                .id(e.getId())
                .lecturerId(e.getLecturer() != null ? e.getLecturer().getId() : null)
                .day(e.getDay())
                .startTime(e.getStartTime())
                .endTime(e.getEndTime())
                .type(e.getType())
                .build();
    }
}
