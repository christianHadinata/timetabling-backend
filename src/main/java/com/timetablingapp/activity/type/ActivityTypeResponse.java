package com.timetablingapp.activity.type;

import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ActivityTypeResponse {

    private Integer id;
    private String name;

    public static ActivityTypeResponse fromEntity(ActivityType t) {
        return ActivityTypeResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .build();
    }
}
