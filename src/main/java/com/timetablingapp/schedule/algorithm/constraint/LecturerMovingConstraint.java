package com.timetablingapp.schedule.algorithm.constraint;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.timetablingapp.schedule.algorithm.genetic.Chromosome;

/**
 * Soft, no-op (TODO Phase 8+): the legacy rewrite threw UnsupportedOperationException here,
 * which would blow up bean registration. Never enforced in the original Laravel algorithm.
 */
@Component
public class LecturerMovingConstraint implements Constraint {
    @Override public boolean isHard() { return false; }
    @Override public int getWeight() { return 0; }
    @Override public String getName() { return "lecturer moving (TODO Phase 8+)"; }
    @Override public ConstraintResult evaluate(Chromosome c) { return new ConstraintResult(0, 0, Set.of()); }
}
