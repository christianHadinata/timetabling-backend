package com.timetablingapp.schedule.algorithm.io;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.timetablingapp.schedule.algorithm.model.AlgorithmActivity;
import com.timetablingapp.schedule.algorithm.model.GAContext;

import lombok.Data;

@Data
public class AlgorithmResult {
    private List<Schedule> schedule;
    private Set<Integer> conflictedActIds;
    private Set<Integer> notInsertedActIds;

    public AlgorithmResult() {
        this.schedule = new ArrayList<>();
        this.conflictedActIds = new HashSet<>();
        this.notInsertedActIds = new HashSet<>();
    }

    /** Assumes the caller already scrubbed the corresponding free-slot candidates. */
    public void initSchedule(GAContext context) {
        this.schedule.addAll(context.getInitialSchedules());
    }

    public boolean isScheduled(AlgorithmActivity activity) {
        for (Schedule sch : this.schedule) {
            if (sch.getActivityId().equals(activity.getId())) return true;
        }
        return false;
    }

    public void addSchedule(Integer activityId, List<Integer> slotIds) {
        this.schedule.add(new Schedule(activityId, slotIds));
    }

    public boolean isSlotOccupied(Integer slotId) {
        for (Schedule sch : this.schedule) {
            if (sch.getSlotIds().contains(slotId)) return true;
        }
        return false;
    }

    public void addConflictedActivities(Set<Integer> stillConflictedActivityIds) {
        this.conflictedActIds.addAll(stillConflictedActivityIds);
    }

    public void addNotInsertedActivities(AlgorithmActivity activity) {
        this.notInsertedActIds.add(activity.getId());
    }
}
