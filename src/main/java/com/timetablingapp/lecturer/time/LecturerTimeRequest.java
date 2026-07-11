package com.timetablingapp.lecturer.time;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LecturerTimeRequest {

    @NotNull(message = "lecturerId is required")
    private Integer lecturerId;

    @NotNull
    @Min(1)
    @Max(6)
    private Integer day;

    @NotNull
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    @NotNull(message = "type is required (NOT_AVAILABLE or PRIORITY)")
    private LecturerTimeType type;
}
