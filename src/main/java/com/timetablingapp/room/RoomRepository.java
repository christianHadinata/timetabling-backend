package com.timetablingapp.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Integer> {

    List<Room> findAllByOrderByRoomCodeAsc();

    Optional<Room> findByRoomCode(String roomCode);

    boolean existsByRoomCode(String roomCode);

    /** Children of a given room (used by the delete guard). */
    List<Room> findByParentRoom_Id(Integer parentRoomId);

    boolean existsByParentRoom_Id(Integer parentRoomId);

    /** Used by RoomTypeService delete guard. */
    boolean existsByRoomType_Id(Integer roomTypeId);
}
