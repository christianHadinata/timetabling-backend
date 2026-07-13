package com.timetablingapp.schedule.algorithm.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AlgorithmTime {
    private Integer id;
    private int day;
    private int hour;
}
