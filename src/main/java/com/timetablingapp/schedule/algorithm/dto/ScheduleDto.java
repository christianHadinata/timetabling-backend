package com.timetablingapp.schedule.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One placed activity (int hours, matching Laravel). */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleDto {
    private Integer activityId;
    private Integer roomId;
    private Integer day;        // 1..7
    private Integer startTime;  // hour (7..23)
    private Integer endTime;    // hour
}
