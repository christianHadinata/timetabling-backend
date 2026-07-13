package com.timetablingapp.schedule.algorithm.genetic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.data.util.Pair;

public class Population {
    private final List<Chromosome> population = new ArrayList<>();
    private final int popSize;
    private final Random r = new Random();

    public Population(int popSize) { this.popSize = popSize; }

    public void add(Chromosome c) {
        if (population.size() < popSize) population.add(c);
        else throw new IllegalStateException("Population is full.");
    }
    public Chromosome selectOne() { return population.get(r.nextInt(population.size())).copy(); }
    public Pair<Chromosome, Chromosome> selectTwo() {
        return Pair.of(population.get(r.nextInt(population.size())).copy(),
                       population.get(r.nextInt(population.size())).copy());
    }
    public int getRemainingSlot() { return popSize - population.size(); }
    public List<Chromosome> getList() { return population; }
}
