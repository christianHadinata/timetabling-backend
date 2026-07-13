package com.timetablingapp.schedule.algorithm.genetic.problem;

import java.util.List;

import org.springframework.stereotype.Service;

import com.timetablingapp.schedule.algorithm.constraint.Constraint;

/** Kept for parity with the legacy rewrite; useful if per-run constraint sets are needed later. */
@Service
public class FitnessFunctionFactory {

    private final List<Constraint> constraints;

    public FitnessFunctionFactory(List<Constraint> constraints) {
        this.constraints = constraints;
    }

    public FitnessFunction create() {
        return new FitnessFunction(constraints);
    }
}
