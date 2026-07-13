package com.timetablingapp.schedule.algorithm.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/** {@code parentId == -1} means "no parent room". */
@Data
@AllArgsConstructor
public class AlgorithmRoom {
    private Integer id;
    private String roomCode;
    private Integer roomTypeId;
    private int capacity;
    private String building;
    private String floor;
    private Integer parentId;
}
