package com.timetablingapp.schedule.algorithm.constraint;

import com.timetablingapp.schedule.algorithm.genetic.Chromosome;

public interface Constraint {
    boolean isHard();
    int getWeight();
    String getName();
    ConstraintResult evaluate(Chromosome chromosome);
}
