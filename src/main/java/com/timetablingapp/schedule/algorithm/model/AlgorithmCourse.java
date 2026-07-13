package com.timetablingapp.schedule.algorithm.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/** {@code type} holds "Wajib"/"Pilihan" (CourseType enum name). */
@Data
@AllArgsConstructor
public class AlgorithmCourse {
    private String code;
    private String type;
    private int tingkat;
    private String konsentrasi;
}
