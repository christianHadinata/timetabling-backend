package com.timetablingapp.schedule.algorithm.constraint.activity;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.timetablingapp.schedule.algorithm.model.GAContext;

/** Same lecturer teaching two activities at the same day+hour. */
@Component
public class LecturerConflict implements ActivityPairConstraint {
    @Override public boolean isConflict(int a1, int a2, GAContext ctx) {
        Set<String> l1 = ctx.getActivityByIdx(a1).getLecturerNiks();
        Set<String> l2 = ctx.getActivityByIdx(a2).getLecturerNiks();
        for (String nik : l1) if (l2.contains(nik)) return true;
        return false;
    }
}
