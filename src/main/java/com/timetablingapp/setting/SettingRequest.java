package com.timetablingapp.setting;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SettingRequest {

    @NotBlank(message = "name is required")
    private String name;

    /** Optional — null means "current semester" (create only; ignored on update). */
    private Integer semesterId;

    /**
     * type key (SettingableType.dbValue, e.g. "roomType") -> selected values.
     * Values are stringified ids / hours / days, matching setting_constraints.settingable_value.
     */
    private Map<String, List<String>> constraints;

    /**
     * Types where the user selected "all" — no rows are persisted for these
     * (absence of a constraint row is interpreted as "everything allowed").
     * Mirrors the Laravel `semua` array.
     */
    private List<String> selectAll;
}
