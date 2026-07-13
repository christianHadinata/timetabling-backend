package com.timetablingapp.result;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ResultRequest {

    /** Optional — null means "current semester". */
    private Integer semesterId;

    @NotNull(message = "activityId is required")
    private Integer activityId;

    /** Nullable — result may be created before room assignment. */
    private Integer roomId;

    private String day;
    private LocalTime startTime;   // "HH:mm[:ss]"
    private LocalTime endTime;

    /** Defaults to true (matches DB default). */
    private Boolean valid = true;
}
