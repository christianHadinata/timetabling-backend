package com.timetablingapp.schedule.algorithm.genetic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.timetablingapp.schedule.algorithm.genetic.problem.FitnessVector;
import com.timetablingapp.schedule.algorithm.genetic.problem.Problem;
import com.timetablingapp.schedule.algorithm.model.AlgorithmActivity;
import com.timetablingapp.schedule.algorithm.model.SlotUsage;

import lombok.Data;

@Data
public class Chromosome implements Comparable<Chromosome> {
    private final List<Gene> gens;
    private final SlotUsage slotUsage;
    private final Problem problem;
    private FitnessVector fitness;

    public Chromosome(Problem problem) {
        this.gens = new ArrayList<>();
        this.slotUsage = new SlotUsage(problem);
        this.problem = problem;
        this.fitness = null;
    }

    public void addGens(Gene gene) {
        this.gens.add(gene);
        this.slotUsage.resolveSlotActivities(gene);
    }

    public Chromosome copy() {
        Chromosome c = new Chromosome(this.problem);
        this.gens.forEach(g -> c.addGens(g.copy()));
        c.fitness = (this.fitness != null) ? this.fitness.copy() : null;   // FIX: null-guard
        return c;
    }

    /** New child: genes 0..point from `first`, point..end from `second`. */
    public void crossOver(Chromosome first, Chromosome second, int point) {
        for (int i = 0; i < point; i++)                 this.addGens(first.gens.get(i).copy());
        for (int i = point; i < second.gens.size(); i++) this.addGens(second.gens.get(i).copy());
    }

    /** Re-roll one random gene's slots from its activity's still-free candidates. */
    public void changeRandomGeneSlot() {
        if (gens.isEmpty()) return;
        Gene gene = gens.get(new Random().nextInt(gens.size()));
        AlgorithmActivity act = problem.getContext().getActivityByIdx(gene.getActivityIdx());
        int[] free = act.getRandomFreeSlot();
        gene.setSlotIds(free != null ? free : new int[0]);
    }

    @Override public int compareTo(Chromosome o) { return this.fitness.compareTo(o.getFitness()); }
}
