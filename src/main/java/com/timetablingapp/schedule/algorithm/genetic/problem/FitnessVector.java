package com.timetablingapp.schedule.algorithm.genetic.problem;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Convention: smaller FitnessVector = fitter. Comparisons use natural ascending order. */
@Data @AllArgsConstructor
public class FitnessVector implements Comparable<FitnessVector> {
    private int hardViolations;   // lower = better
    private int softPenalty;      // tiebreak, lower = better

    @Override public int compareTo(FitnessVector o) {
        if (hardViolations != o.hardViolations) return Integer.compare(hardViolations, o.hardViolations);
        return Integer.compare(softPenalty, o.softPenalty);
    }
    public FitnessVector copy() { return new FitnessVector(hardViolations, softPenalty); }
}
