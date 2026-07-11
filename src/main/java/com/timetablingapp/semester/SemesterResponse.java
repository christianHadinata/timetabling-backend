package com.timetablingapp.semester;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemesterResponse {

    private Integer id;
    private String type;
    private String academicYear;
    private Boolean current;

    /**
     * Factory method to convert a Semester entity to SemesterResponse.
     */
    public static SemesterResponse fromEntity(Semester semester) {
        return SemesterResponse.builder()
                .id(semester.getId())
                .type(semester.getType())
                .academicYear(semester.getAcademicYear())
                .current(semester.getCurrent())
                .build();
    }
}
