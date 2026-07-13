package com.timetablingapp.schedule.algorithm.genetic.problem;

import java.util.List;

import org.springframework.stereotype.Service;

import com.timetablingapp.schedule.algorithm.constraint.Constraint;
import com.timetablingapp.schedule.algorithm.constraint.ConstraintResult;
import com.timetablingapp.schedule.algorithm.genetic.Chromosome;

@Service
public class FitnessFunction {
    private final List<Constraint> constraints;   // all Constraint beans injected

    public FitnessFunction(List<Constraint> constraints) {
        this.constraints = constraints;
    }

    public FitnessVector calculate(Chromosome chromosome) {
        int hard = 0, soft = 0;
        for (Constraint c : constraints) {
            ConstraintResult r = c.evaluate(chromosome);
            if (c.isHard()) hard += r.getHardViolations();
            else            soft += r.getSoftPenalty();
        }
        return new FitnessVector(hard, soft);
    }
}
