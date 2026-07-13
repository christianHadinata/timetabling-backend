package com.timetablingapp.schedule.algorithm.genetic;

@FunctionalInterface
public interface GaProgressListener {
    void onProgress(int trial, int generation, int hardViolations, int softPenalty, double progress);
}
