package com.timetablingapp.schedule.algorithm.genetic.operators;

import org.springframework.stereotype.Service;

import com.timetablingapp.schedule.algorithm.genetic.Chromosome;
import com.timetablingapp.schedule.algorithm.genetic.Population;
import com.timetablingapp.schedule.algorithm.genetic.problem.FitnessFunction;

@Service
public class Mutation {
    private final FitnessFunction fitnessFunction;
    public Mutation(FitnessFunction fitnessFunction) { this.fitnessFunction = fitnessFunction; }

    public Chromosome reselectSlotMutation(Population population, int mutationCount) {
        Chromosome c = population.selectOne();
        for (int i = 0; i < mutationCount; i++) c.changeRandomGeneSlot();
        c.setFitness(fitnessFunction.calculate(c));
        return c;
    }
}
