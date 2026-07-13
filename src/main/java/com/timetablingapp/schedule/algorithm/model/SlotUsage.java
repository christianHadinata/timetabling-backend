package com.timetablingapp.schedule.algorithm.model;

import java.util.*;
import java.util.stream.Collectors;

import com.timetablingapp.schedule.algorithm.genetic.Gene;
import com.timetablingapp.schedule.algorithm.genetic.problem.Problem;

public class SlotUsage {
    private final List<SlotActivityUsage> slotActivities = new ArrayList<>();   // FIX: init
    private final Problem problem;

    public SlotUsage(Problem problem) { this.problem = problem; }

    public void resolveSlotActivities(Gene gene) {
        int activityIdx = gene.getActivityIdx();
        int[] slotIds = gene.getSlotIds();                                       // FIX: int[]
        for (int i = 0; i < slotIds.length; i++) {                               // FIX: < not <=
            AlgorithmSlot s = problem.getContext().getSlotById(slotIds[i]);
            addSlotActivity(s.getId(), activityIdx);
            if (s.getParent() != null) addSlotActivity(s.getParent().getId(), activityIdx);
            for (AlgorithmSlot child : s.getChilds()) addSlotActivity(child.getId(), activityIdx);
        }
    }

    private void addSlotActivity(int slotId, int activityIdx) {
        AlgorithmSlot slot = problem.getContext().getSlotById(slotId);
        slotActivities.add(new SlotActivityUsage(
                slotId, slot.getRoomId(), slot.getTime().getDay(), slot.getTime().getHour(), activityIdx));
    }

    public Map<Integer, List<Integer>> getSlotActs() {   // slotId -> [activityIdx]
        return slotActivities.stream().collect(Collectors.groupingBy(
                SlotActivityUsage::getSlotId,
                Collectors.mapping(SlotActivityUsage::getActivityIdx, Collectors.toList())));
    }
    public Map<Integer, List<Integer>> getActSlots() {   // activityIdx -> [slotId]
        return slotActivities.stream().collect(Collectors.groupingBy(
                SlotActivityUsage::getActivityIdx,
                Collectors.mapping(SlotActivityUsage::getSlotId, Collectors.toList())));
    }
    public Map<String, List<Integer>> getDayHourActs() { // "day_hour" -> [activityIdx]
        return slotActivities.stream().collect(Collectors.groupingBy(
                sa -> sa.getDay() + "_" + sa.getHour(),
                Collectors.mapping(SlotActivityUsage::getActivityIdx, Collectors.toList())));
    }
}
