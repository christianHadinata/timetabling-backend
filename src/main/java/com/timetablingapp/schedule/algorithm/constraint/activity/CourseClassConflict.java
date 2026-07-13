package com.timetablingapp.schedule.algorithm.constraint.activity;

import org.springframework.stereotype.Component;

import com.timetablingapp.schedule.algorithm.model.AlgorithmActivity;
import com.timetablingapp.schedule.algorithm.model.GAContext;

/** Same course code + same class (different session) clashing at the same day+hour. */
@Component
public class CourseClassConflict implements ActivityPairConstraint {
    @Override public boolean isConflict(int a1, int a2, GAContext ctx) {
        AlgorithmActivity x = ctx.getActivityByIdx(a1), y = ctx.getActivityByIdx(a2);
        return x.getCourse().getCode().equals(y.getCourse().getCode())
            && x.getCourseClass().equals(y.getCourseClass());
    }
}
