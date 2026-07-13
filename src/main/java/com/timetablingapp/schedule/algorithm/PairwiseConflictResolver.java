package com.timetablingapp.schedule.algorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.timetablingapp.schedule.algorithm.constraint.activity.ActivityPairConstraint;
import com.timetablingapp.schedule.algorithm.io.Schedule;
import com.timetablingapp.schedule.algorithm.model.AlgorithmSlot;
import com.timetablingapp.schedule.algorithm.model.GAContext;

/**
 * Final safety net run after the GA finishes. {@code Problem.setSchedule} (the ported GA's own
 * materialisation step, see genetic/problem/Problem.java) only guards against literal
 * physical-slot double-booking — the lecturer/course-class/curriculum pair-constraints
 * ({@link ActivityPairConstraint}) only influence the search via the fitness score during
 * evolution and are never re-checked once the GA stops. When a conflict between two activities
 * is genuinely unavoidable given their candidate slots, the GA's best chromosome can still carry
 * it, and setSchedule happily schedules both since they land on different physical slots.
 * Re-checking the pair-constraints here over the materialised result is what makes phase8.md
 * §14's "no lecturer conflict / no curriculum overlap" invariants actually hold on what's
 * returned to the caller, not just on what the GA's fitness function penalised during search.
 */
@Component
public class PairwiseConflictResolver {

    private final List<ActivityPairConstraint> constraints;

    public PairwiseConflictResolver(List<ActivityPairConstraint> constraints) {
        this.constraints = constraints;
    }

    /**
     * @return activityIds involved in an unresolved pairwise conflict, excluding any id in
     *         {@code lockedIds} — a locked activity is never reassigned or hidden by this check;
     *         if it conflicts with a freshly-placed one, only the non-locked side is flagged.
     */
    public Set<Integer> findConflicts(Collection<Schedule> scheduled, GAContext ctx, Set<Integer> lockedIds) {
        Map<String, List<Integer>> byDayHour = new HashMap<>();
        for (Schedule sch : scheduled) {
            for (Integer slotId : sch.getSlotIds()) {
                AlgorithmSlot slot = ctx.getSlotById(slotId);
                String key = slot.getTime().getDay() + "_" + slot.getTime().getHour();
                byDayHour.computeIfAbsent(key, k -> new ArrayList<>()).add(sch.getActivityId());
            }
        }

        Set<Integer> conflicted = new HashSet<>();
        for (List<Integer> activityIds : byDayHour.values()) {
            for (int i = 0; i < activityIds.size(); i++) {
                Integer a1 = activityIds.get(i);
                Integer idx1 = ctx.findActivityIndexById(a1);
                if (idx1 == null) continue;
                for (int j = i + 1; j < activityIds.size(); j++) {
                    Integer a2 = activityIds.get(j);
                    if (a1.equals(a2)) continue;   // same activity spanning multiple hours
                    Integer idx2 = ctx.findActivityIndexById(a2);
                    if (idx2 == null) continue;
                    for (ActivityPairConstraint c : constraints) {
                        if (c.isConflict(idx1, idx2, ctx)) {
                            if (!lockedIds.contains(a1)) conflicted.add(a1);
                            if (!lockedIds.contains(a2)) conflicted.add(a2);
                        }
                    }
                }
            }
        }
        return conflicted;
    }
}
