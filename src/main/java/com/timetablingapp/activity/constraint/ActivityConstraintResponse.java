package com.timetablingapp.activity.constraint;

import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ActivityConstraintResponse {

    private Integer id;
    private Integer activityId;
    private ConstraintType type;
    private String value;

    public static ActivityConstraintResponse fromEntity(ActivityConstraint c) {
        return ActivityConstraintResponse.builder()
                .id(c.getId())
                .activityId(c.getActivity() != null ? c.getActivity().getId() : null)
                .type(c.getType())
                .value(c.getValue())
                .build();
    }
}
