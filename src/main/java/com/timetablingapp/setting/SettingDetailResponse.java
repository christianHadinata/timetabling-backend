package com.timetablingapp.setting;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SettingDetailResponse {

    private Integer id;
    private String  name;
    private Integer semesterId;
    private String  typeAndSemester;

    /**
     * Constraints grouped by settingable_type (dbValue key) -> list of values.
     * A type with NO stored rows is expanded to the full default set
     * (all ids / all hours / all days) — mirrors Setting::getByType().
     */
    private Map<String, List<String>> constraints;
}
