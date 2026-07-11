package com.timetablingapp.room.available;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomAvailableRepository extends JpaRepository<RoomAvailable, Integer> {

    List<RoomAvailable> findByRoom_IdOrderByDayAsc(Integer roomId);

    Optional<RoomAvailable> findByRoom_IdAndDay(Integer roomId, Integer day);

    void deleteByRoom_Id(Integer roomId);  // soft-delete via @SQLDelete on each row
}
