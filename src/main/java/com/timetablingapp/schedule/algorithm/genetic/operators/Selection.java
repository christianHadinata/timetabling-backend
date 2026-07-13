package com.timetablingapp.schedule.algorithm.genetic.operators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

import com.timetablingapp.schedule.algorithm.genetic.Chromosome;
import com.timetablingapp.schedule.algorithm.genetic.Population;

@Service
public class Selection {
    /** Elite = the `count` fittest (smallest FitnessVector). Returns deep copies. */
    public List<Chromosome> eliteSelection(Population population, int count) {
        List<Chromosome> sorted = new ArrayList<>(population.getList());
        Collections.sort(sorted);                       // ascending = fittest first
        List<Chromosome> elites = new ArrayList<>();
        for (int i = 0; i < Math.min(count, sorted.size()); i++) {
            elites.add(sorted.get(i).copy());
        }
        return elites;
    }
}
