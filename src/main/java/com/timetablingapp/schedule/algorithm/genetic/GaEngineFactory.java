package com.timetablingapp.schedule.algorithm.genetic;

import org.springframework.stereotype.Component;
import com.timetablingapp.config.GAConfig;
import com.timetablingapp.schedule.algorithm.genetic.operators.Crossover;
import com.timetablingapp.schedule.algorithm.genetic.operators.Mutation;
import com.timetablingapp.schedule.algorithm.genetic.operators.Selection;
import com.timetablingapp.schedule.algorithm.genetic.problem.FitnessFunction;
import com.timetablingapp.schedule.algorithm.genetic.problem.Problem;

/** Assembles a per-run GeneticAlgorithm from singleton collaborators. */
@Component
public class GaEngineFactory {
    private final FitnessFunction fitnessFunction;
    private final Crossover crossover;
    private final Mutation mutation;
    private final Selection selection;
    private final GAConfig cfg;

    public GaEngineFactory(FitnessFunction ff, Crossover c, Mutation m, Selection s, GAConfig cfg) {
        this.fitnessFunction = ff; this.crossover = c; this.mutation = m; this.selection = s; this.cfg = cfg;
    }

    public GeneticAlgorithm create(Problem problem, GaProgressListener listener) {
        return new GeneticAlgorithm(problem, fitnessFunction, crossover, mutation, selection, cfg, listener);
    }
}
