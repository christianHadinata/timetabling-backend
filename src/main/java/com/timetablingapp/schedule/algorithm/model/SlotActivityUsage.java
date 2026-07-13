package com.timetablingapp.schedule.algorithm.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One (slot, activity) usage fact recorded while resolving a chromosome's genes. */
@Data
@AllArgsConstructor
public class SlotActivityUsage {
    private int slotId;
    private int roomId;
    private int day;
    private int hour;
    private int activityIdx;
}
