package com.timetablingapp.room;

import com.timetablingapp.room.available.RoomAvailableRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomRequest {

    @NotBlank(message = "Room code is required")
    private String roomCode;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Unit owner is required")
    private String unitOwner;

    @NotBlank(message = "Location is required")
    private String location;

    @NotBlank(message = "Building is required")
    private String building;

    @NotBlank(message = "Floor is required")
    private String floor;

    @NotNull(message = "Capacity is required")
    private Integer capacity;

    /** Nullable. Legacy sends 0 to mean "no parent" — normalized to null in the service. */
    private Integer parentRoomId;

    @NotNull(message = "Room type is required")
    private Integer roomTypeId;

    private String virtual;

    /** Availability windows created/replaced together with the room.
     *  Reuses RoomAvailableRequest.day/startTime/endTime (roomId ignored here). */
    @Valid
    private List<RoomAvailableRequest> availabilities = new ArrayList<>();
}
