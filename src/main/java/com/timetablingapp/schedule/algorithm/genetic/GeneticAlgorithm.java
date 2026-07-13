package com.timetablingapp.schedule.algorithm.genetic;

import org.springframework.data.util.Pair;

import com.timetablingapp.config.GAConfig;
import com.timetablingapp.schedule.algorithm.genetic.operators.Crossover;
import com.timetablingapp.schedule.algorithm.genetic.operators.Mutation;
import com.timetablingapp.schedule.algorithm.genetic.operators.Selection;
import com.timetablingapp.schedule.algorithm.genetic.problem.FitnessFunction;
import com.timetablingapp.schedule.algorithm.genetic.problem.Problem;
import com.timetablingapp.schedule.algorithm.io.AlgorithmResult;

/** NOT a Spring bean — one instance per generate() run (holds mutable population/best). */
public class GeneticAlgorithm {
    private final Problem problem;
    private final FitnessFunction fitnessFunction;
    private final Crossover crossover;
    private final Mutation mutation;
    private final Selection selection;
    private final GAConfig cfg;
    private final GaProgressListener listener;

    private Population population;
    private Population newPopulation;
    public  Chromosome bestChromosome;

    public GeneticAlgorithm(Problem problem, FitnessFunction ff, Crossover c, Mutation m,
                            Selection s, GAConfig cfg, GaProgressListener listener) {
        this.problem = problem; this.fitnessFunction = ff; this.crossover = c;
        this.mutation = m; this.selection = s; this.cfg = cfg; this.listener = listener;
    }

    public AlgorithmResult run(int maxTrials) {
        int trial = 0;
        while (!problem.isSolved() && trial < maxTrials) {
            iteration(trial, maxTrials);
            trial++;
        }
        return problem.getResult();
    }

    private void iteration(int trial, int maxTrials) {
        bestChromosome = null;
        initialPopulationGeneration();
        for (int gen = 0; gen < cfg.getGenerations(); gen++) {
            newPopulation = new Population(cfg.getPopulationSize());
            doCrossover();
            doMutation(2);
            doSelection();
            population = newPopulation;

            if (listener != null && bestChromosome != null) {
                FitnessVectorSnapshot best = FitnessVectorSnapshot.of(bestChromosome);
                double progress = (trial * cfg.getGenerations() + gen + 1.0)
                                / (double) (maxTrials * cfg.getGenerations());
                listener.onProgress(trial, gen, best.hard(), best.soft(), progress);
            }
        }
        problem.setSchedule(bestChromosome);   // materialise best into the Result
    }

    private void initialPopulationGeneration() {
        population = new Population(cfg.getPopulationSize());
        for (int i = 0; i < cfg.getPopulationSize(); i++) {
            Chromosome c = problem.createValidChromosome();
            c.setFitness(fitnessFunction.calculate(c));
            compareChromosomeFitness(c);
            population.add(c);
        }
    }

    /** FIX: null-guard first best; keep the SMALLER (fitter) vector. */
    private void compareChromosomeFitness(Chromosome c) {
        if (bestChromosome == null
                || c.getFitness().compareTo(bestChromosome.getFitness()) < 0) {
            bestChromosome = c.copy();
        }
    }

    private void doCrossover() {
        int target = (int) (cfg.getCrossoverRate() * cfg.getPopulationSize());
        for (int i = 0; i < target; i += 2) {
            Pair<Chromosome, Chromosome> pair = crossover.onePointCrossOver(population);
            addToNewPopulation(pair.getFirst());
            addToNewPopulation(pair.getSecond());
        }
    }
    private void doMutation(int m) {
        int target = (int) (cfg.getMutationRate() * cfg.getPopulationSize());
        for (int i = 0; i < target; i++) addToNewPopulation(mutation.reselectSlotMutation(population, m));
    }
    private void doSelection() {
        selection.eliteSelection(population, newPopulation.getRemainingSlot())
                 .forEach(this::addToNewPopulation);
    }
    private void addToNewPopulation(Chromosome c) {
        try { newPopulation.add(c); compareChromosomeFitness(c); }
        catch (IllegalStateException full) { /* population full → skip (matches legacy) */ }
    }

    // tiny helper to pull hard/soft out of the best chromosome for the SSE event
    private record FitnessVectorSnapshot(int hard, int soft) {
        static FitnessVectorSnapshot of(Chromosome c) {
            return new FitnessVectorSnapshot(c.getFitness().getHardViolations(),
                                             c.getFitness().getSoftPenalty());
        }
    }
}
