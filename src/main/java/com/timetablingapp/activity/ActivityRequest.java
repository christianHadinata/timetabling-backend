package com.timetablingapp.activity;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityRequest {

    /** Optional — null means "current semester". */
    private Integer semesterId;

    @NotBlank(message = "courseCode is required")
    private String courseCode;

    @NotBlank(message = "courseClass is required")
    private String courseClass;

    @NotNull @Min(1)
    private Integer courseSession;

    @NotNull @Min(1)
    private Integer duration;

    @NotNull @Min(1)
    private Integer quota;

    @NotNull(message = "activityTypeId is required")
    private Integer activityTypeId;

    /** Constraint values. All optional; empty ⇒ "no restriction" (matches legacy). */
    private List<String> lecturerNiks;
    private List<Integer> roomIds;
    private List<Integer> roomTypeIds;

    /** Update-only extras. */
    private List<Integer> paralelActivityIds;
    private List<GapDto> gaps;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class GapDto {
        @NotNull private Integer activityId;   // the "with" activity
        @NotNull @Min(1) private Integer minGap;
    }
}
