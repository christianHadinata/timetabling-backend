package com.timetablingapp.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CourseRequest {

    @NotBlank(message = "Course code is required")
    private String code;

    @NotBlank(message = "Course name is required")
    private String name;

    @NotNull(message = "Course type is required")
    private CourseType type;

    private Integer tingkat;

    private String konsentrasi;

    @NotNull(message = "Jurusan ID is required")
    private Integer jurusanId;
}
