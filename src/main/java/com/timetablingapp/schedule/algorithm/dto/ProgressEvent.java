package com.timetablingapp.schedule.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder @AllArgsConstructor
public class ProgressEvent {
    private String status;            // "running" | "completed" | "error"
    private String jobId;
    private Integer trial;
    private Integer generation;
    private Integer hardViolations;
    private Integer softPenalty;
    private Double  progress;         // 0.0 .. 1.0
    private GenerateResponse result;  // set only on "completed"
    private String message;           // set only on "error"

    public static ProgressEvent running(String jobId, int trial, int gen, int hard, int soft, double p) {
        return ProgressEvent.builder().status("running").jobId(jobId)
                .trial(trial).generation(gen).hardViolations(hard).softPenalty(soft).progress(p).build();
    }
    public static ProgressEvent completed(String jobId, GenerateResponse r) {
        return ProgressEvent.builder().status("completed").jobId(jobId).progress(1.0).result(r).build();
    }
    public static ProgressEvent error(String jobId, String msg) {
        return ProgressEvent.builder().status("error").jobId(jobId).message(msg).build();
    }
}
