package com.timetablingapp.schedule.algorithm.constraint;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.timetablingapp.schedule.algorithm.constraint.activity.ActivityPairConstraint;
import com.timetablingapp.schedule.algorithm.genetic.Chromosome;
import com.timetablingapp.schedule.algorithm.model.GAContext;

/**
 * Hard: two activities placed at the same day+hour that conflict per one of the
 * DI'd {@link ActivityPairConstraint} beans (lecturer / course-class / curriculum bentrok).
 */
@Component
public class ConflictedActivityConstraint implements Constraint {

    private final List<ActivityPairConstraint> constraints;

    public ConflictedActivityConstraint(List<ActivityPairConstraint> constraints) {
        this.constraints = constraints;
    }

    @Override
    public ConstraintResult evaluate(Chromosome chromosome) {
        Set<Integer> conflicted = getConflictedLectureTimes(chromosome);
        return new ConstraintResult(conflicted.size() * getWeight(), 0, conflicted);
    }

    private Set<Integer> getConflictedLectureTimes(Chromosome chromosome) {
        Map<String, List<Integer>> grouped = chromosome.getSlotUsage().getDayHourActs();
        Set<Integer> conflictedActs = new HashSet<>();
        GAContext context = chromosome.getProblem().getContext();

        grouped.forEach((dayHour, activityIdxs) -> {
            int size = activityIdxs.size();
            for (int i = 0; i < size; i++) {
                for (int j = i + 1; j < size; j++) {
                    int a1 = activityIdxs.get(i);
                    int a2 = activityIdxs.get(j);
                    for (ActivityPairConstraint c : constraints) {
                        if (c.isConflict(a1, a2, context)) {
                            conflictedActs.add(a1);
                            conflictedActs.add(a2);
                        }
                    }
                }
            }
        });
        return conflictedActs;
    }

    @Override public boolean isHard() { return true; }
    @Override public int getWeight() { return 1000; }
    @Override public String getName() { return "conflicted activity"; }
}
