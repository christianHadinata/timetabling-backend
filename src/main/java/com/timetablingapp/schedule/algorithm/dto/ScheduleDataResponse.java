package com.timetablingapp.schedule.algorithm.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScheduleDataResponse {
    private List<ScheduleDto> inserted;
    private List<Integer> notInserted;
}
