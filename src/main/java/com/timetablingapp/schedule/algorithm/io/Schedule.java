package com.timetablingapp.schedule.algorithm.io;

import java.util.List;

import com.timetablingapp.schedule.algorithm.model.GAContext;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Schedule {
    private Integer activityId;
    private Integer roomId;
    private Integer day;
    private Integer startTime;   // hour
    private Integer endTime;     // hour
    private List<Integer> slotIds;

    public Schedule(Integer activityId, List<Integer> slotIds) {
        this.activityId = activityId;
        this.slotIds = slotIds;
    }

    /** Resolves slotIds from (roomId, day, startTime, endTime) — used for locked/initial schedules. */
    public void init(GAContext context) {
        this.slotIds = context.getSlotIdsByDayTime(this.roomId, this.day, this.startTime, this.endTime);
    }
}
