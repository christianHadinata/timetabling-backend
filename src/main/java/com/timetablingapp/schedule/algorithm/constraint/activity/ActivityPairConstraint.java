package com.timetablingapp.schedule.algorithm.constraint.activity;

import com.timetablingapp.schedule.algorithm.model.GAContext;

public interface ActivityPairConstraint {
    boolean isConflict(int a1, int a2, GAContext ctx);
}
