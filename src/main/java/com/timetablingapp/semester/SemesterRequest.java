package com.timetablingapp.semester;

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
public class SemesterRequest {

    @NotBlank(message = "Semester type is required")
    private String type;

    @NotBlank(message = "Academic year is required")
    private String academicYear;

    @NotNull(message = "Current flag is required")
    private Boolean current;
}
