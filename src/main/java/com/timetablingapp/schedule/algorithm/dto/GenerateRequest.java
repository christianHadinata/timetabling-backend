package com.timetablingapp.schedule.algorithm.dto;

import java.util.List;

import lombok.Data;

@Data
public class GenerateRequest {
    private Integer settingId;                 // nullable → schedule everything
    private List<ScheduleDto> lockedSchedules; // already-placed activities the GA must respect
}
