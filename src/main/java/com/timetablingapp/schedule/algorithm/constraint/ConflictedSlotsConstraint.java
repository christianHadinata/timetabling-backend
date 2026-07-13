package com.timetablingapp.schedule.algorithm.constraint;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.timetablingapp.schedule.algorithm.genetic.Chromosome;

/** Hard: two activities double-booking the same physical slot. */
@Component
public class ConflictedSlotsConstraint implements Constraint {

    @Override
    public ConstraintResult evaluate(Chromosome chromosome) {
        Set<Integer> conflicted = getConflictedSlots(chromosome);
        return new ConstraintResult(conflicted.size() * getWeight(), 0, conflicted);
    }

    private Set<Integer> getConflictedSlots(Chromosome chromosome) {
        Map<Integer, List<Integer>> grouped = chromosome.getSlotUsage().getSlotActs();
        Set<Integer> conflictedActs = new HashSet<>();
        grouped.forEach((slotId, activityIdxs) -> {
            if (activityIdxs.size() > 1) conflictedActs.addAll(activityIdxs);
        });
        return conflictedActs;
    }

    @Override public boolean isHard() { return true; }
    @Override public int getWeight() { return 1000; }
    @Override public String getName() { return "conflicted slots"; }
}
