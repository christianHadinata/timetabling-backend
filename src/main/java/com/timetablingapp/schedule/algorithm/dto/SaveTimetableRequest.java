package com.timetablingapp.schedule.algorithm.dto;

import java.util.List;

import lombok.Data;

@Data
public class SaveTimetableRequest {
    private List<ScheduleDto> schedules;   // placed (valid=1)
    private List<Integer> notInserted;     // unplaced (valid=0)
}
