package com.timetablingapp.schedule.algorithm.dto;

import java.util.Map;

import com.timetablingapp.activity.ActivityResponse;
import com.timetablingapp.room.RoomResponse;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Mirrors legacy TableController::getData() — activities+rooms keyed by id for the display grid. */
@Data
@AllArgsConstructor
public class TimetableDataResponse {
    private Map<Integer, ActivityResponse> activities;
    private Map<Integer, RoomResponse> rooms;
}
