package com.timetablingapp.setting.constraint;

import com.timetablingapp.setting.SettingableType;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SettingConstraintDto {
    private SettingableType type;
    private String value;

    public static SettingConstraintDto fromEntity(SettingConstraint c) {
        return SettingConstraintDto.builder()
                .type(c.getType())
                .value(c.getValue())
                .build();
    }
}
