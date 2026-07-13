package com.timetablingapp.schedule.algorithm.genetic.operators;

import java.util.Random;

import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import com.timetablingapp.schedule.algorithm.genetic.Chromosome;
import com.timetablingapp.schedule.algorithm.genetic.Population;
import com.timetablingapp.schedule.algorithm.genetic.problem.FitnessFunction;

@Service
public class Crossover {
    private final FitnessFunction fitnessFunction;
    public Crossover(FitnessFunction fitnessFunction) { this.fitnessFunction = fitnessFunction; }

    public Pair<Chromosome, Chromosome> onePointCrossOver(Population population) {
        Pair<Chromosome, Chromosome> parents = population.selectTwo();
        Chromosome p1 = parents.getFirst(), p2 = parents.getSecond();
        int point = new Random().nextInt(Math.max(1, p1.getGens().size()));

        Chromosome c1 = new Chromosome(p1.getProblem()); c1.crossOver(p1, p2, point);
        c1.setFitness(fitnessFunction.calculate(c1));
        Chromosome c2 = new Chromosome(p1.getProblem()); c2.crossOver(p2, p1, point);
        c2.setFitness(fitnessFunction.calculate(c2));
        return Pair.of(c1, c2);
    }
}
