package com.timetablingapp.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImportResultResponse {

    private boolean success;
    private String message;
    private int importedCount;
    private List<String> errors;

    /**
     * Factory method for a successful import.
     */
    public static ImportResultResponse success(String message, int importedCount) {
        return ImportResultResponse.builder()
                .success(true)
                .message(message)
                .importedCount(importedCount)
                .build();
    }

    /**
     * Factory method for an import with errors.
     */
    public static ImportResultResponse withErrors(
            String message, int importedCount, List<String> errors) {
        return ImportResultResponse.builder()
                .success(errors.isEmpty())
                .message(message)
                .importedCount(importedCount)
                .errors(errors)
                .build();
    }
}
