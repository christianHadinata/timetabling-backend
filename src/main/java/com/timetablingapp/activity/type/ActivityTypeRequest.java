package com.timetablingapp.activity.type;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityTypeRequest {

    @NotBlank(message = "Activity type name is required")
    private String name;
}
