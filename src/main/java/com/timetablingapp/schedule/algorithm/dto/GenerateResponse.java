package com.timetablingapp.schedule.algorithm.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Async job handle OR the synchronous result, depending on which factory method is used. */
@Getter @Builder @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenerateResponse {
    private String jobId;                  // async
    private List<ScheduleDto> inserted;    // sync
    private List<Integer> conflicts;
    private List<Integer> notInserted;

    public static GenerateResponse job(String id) {
        return GenerateResponse.builder().jobId(id).build();
    }
    public static GenerateResponse result(List<ScheduleDto> ins, List<Integer> c, List<Integer> n) {
        return GenerateResponse.builder().inserted(ins).conflicts(c).notInserted(n).build();
    }
}
