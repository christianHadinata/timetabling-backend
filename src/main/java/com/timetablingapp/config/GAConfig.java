package com.timetablingapp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ga")
@Getter
@Setter
public class GAConfig {

    /**
     * Number of chromosomes per generation.
     * Default: 100
     */
    private int populationSize = 100;

    /**
     * Maximum number of generations to evolve.
     * Default: 500
     */
    private int generations = 500;

    /**
     * Probability of crossover between two chromosomes (0.0 - 1.0).
     * Default: 0.8
     */
    private double crossoverRate = 0.8;

    /**
     * Probability of mutation for each gene (0.0 - 1.0).
     * Default: 0.1
     */
    private double mutationRate = 0.1;
}
