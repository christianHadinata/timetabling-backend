package com.timetablingapp.room.available;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomAvailableResponse {

    private Integer id;
    private Integer roomId;
    private Integer day;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    public static RoomAvailableResponse fromEntity(RoomAvailable ra) {
        return RoomAvailableResponse.builder()
                .id(ra.getId())
                .roomId(ra.getRoom() != null ? ra.getRoom().getId() : null)
                .day(ra.getDay())
                .startTime(ra.getStartTime())
                .endTime(ra.getEndTime())
                .build();
    }
}
