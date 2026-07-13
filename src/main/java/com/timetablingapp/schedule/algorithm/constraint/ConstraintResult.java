package com.timetablingapp.schedule.algorithm.constraint;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class ConstraintResult {
    private int hardViolations;
    private int softPenalty;
    private Set<Integer> conflictedActs;
}
