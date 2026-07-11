package com.timetablingapp.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivityResponse {

    private Integer id;
    private Integer semesterId;
    private String courseCode;
    private String courseName;
    private String courseClass;
    private Integer courseSession;
    private Integer duration;
    private Integer quota;
    private Integer activityTypeId;
    private String activityTypeName;
    private String name;                 // computed display name
    private String color;                // from course → jurusan → color + tingkat

    private List<String> lecturerNiks;
    private List<Integer> roomIds;
    private List<Integer> roomTypeIds;

    // Detail-only (null on list responses)
    private List<ParalelDto> paralels;
    private List<GapDto> gaps;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ParalelDto {
        private Integer id;              // paralel activity id
        private String courseCode;
        private String courseName;
        private String courseClass;
        private Integer courseSession;
        private String activityType;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GapDto {
        private Integer id;              // the "with" activity id
        private String courseCode;
        private String courseName;
        private String courseClass;
        private Integer courseSession;
        private String activityType;
        private Integer minGap;
    }
}
