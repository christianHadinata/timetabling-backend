package com.timetablingapp.activity.constraint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityConstraintRequest {

    @NotNull(message = "activityId is required")
    private Integer activityId;

    @NotNull(message = "type is required (LECTURER, ROOM or ROOM_TYPE)")
    private ConstraintType type;

    @NotBlank(message = "value is required")
    private String value;
}
