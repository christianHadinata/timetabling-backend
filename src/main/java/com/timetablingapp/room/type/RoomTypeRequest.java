package com.timetablingapp.room.type;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomTypeRequest {
    @NotBlank(message = "Room type name is required")
    private String name;
}
