package com.timetablingapp.setting;

import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SettingResponse {

    private Integer id;
    private String  name;
    private Integer semesterId;
    /** "{type} {academic_year}" — mirrors SettingController@index typeAndSemester. */
    private String  typeAndSemester;

    public static SettingResponse fromEntity(Setting s) {
        var sem = s.getSemester();
        String label = (sem != null) ? (sem.getType() + " " + sem.getAcademicYear()) : null;
        return SettingResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .semesterId(sem != null ? sem.getId() : null)
                .typeAndSemester(label)
                .build();
    }
}
