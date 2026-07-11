package com.timetablingapp.room.type;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomTypeResponse {
    private Integer id;
    private String name;

    public static RoomTypeResponse fromEntity(RoomType rt) {
        return RoomTypeResponse.builder()
                .id(rt.getId())
                .name(rt.getName())
                .build();
    }
}
