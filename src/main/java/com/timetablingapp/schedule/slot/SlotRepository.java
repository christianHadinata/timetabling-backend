package com.timetablingapp.schedule.slot;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Integer> {

    /**
     * All slots with room + time pre-fetched — validation iterates over every slot,
     * so avoid N+1. Mirrors Laravel: Slot::with(['time'])->get() (room is also needed).
     */
    @EntityGraph(attributePaths = {"room", "room.roomType", "room.parentRoom", "time"})
    List<Slot> findAllBy();

    /**
     * Hard-delete all slots of a room (no soft-delete on slots).
     * Callers must first clear dependent slot_acts. Mirrors RoomController@destroy.
     */
    @Modifying
    @Query("DELETE FROM Slot s WHERE s.room.id = :roomId")
    int deleteByRoomId(@Param("roomId") Integer roomId);
}
