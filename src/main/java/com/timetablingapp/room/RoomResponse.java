package com.timetablingapp.room;

import com.timetablingapp.room.available.RoomAvailableResponse;
import com.timetablingapp.room.type.RoomTypeResponse;
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
public class RoomResponse {

    private Integer id;
    private String roomCode;
    private String name;
    private String unitOwner;
    private String location;
    private String building;
    private String floor;
    private Integer capacity;
    private String virtual;

    private Integer parentRoomId;        // mirrors Laravel custom JSON "parent_id"
    private List<Integer> childIds;      // mirrors Laravel custom JSON "child_ids"

    private Integer roomTypeId;
    private RoomTypeResponse roomType;   // nested

    private List<RoomAvailableResponse> availabilities;

    public static RoomResponse fromEntity(Room room,
                                          List<Room> children,
                                          List<RoomAvailableResponse> availabilities) {
        RoomResponse.RoomResponseBuilder b = RoomResponse.builder()
                .id(room.getId())
                .roomCode(room.getRoomCode())
                .name(room.getName())
                .unitOwner(room.getUnitOwner())
                .location(room.getLocation())
                .building(room.getBuilding())
                .floor(room.getFloor())
                .capacity(room.getCapacity())
                .virtual(room.getVirtual())
                .availabilities(availabilities)
                .childIds(children == null ? List.of()
                        : children.stream().map(Room::getId).toList());

        if (room.getParentRoom() != null) {
            b.parentRoomId(room.getParentRoom().getId());
        }
        if (room.getRoomType() != null) {
            b.roomTypeId(room.getRoomType().getId())
             .roomType(RoomTypeResponse.fromEntity(room.getRoomType()));
        }
        return b.build();
    }
}
