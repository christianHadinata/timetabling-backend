package com.timetablingapp.schedule.slot.act;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SlotActivityRepository extends JpaRepository<SlotActivity, Integer> {

    /**
     * Hard-delete all slot_acts belonging to activities of one semester.
     * slot_acts has no deleted_at, so a bulk DELETE is correct here.
     * Mirrors SlotActivityController@resetAll:
     *   SlotActivity::whereHas('activity', fn($q)=>$q->where('semester_id',$id))->delete()
     */
    @Modifying
    @Query("DELETE FROM SlotActivity sa WHERE sa.activity.id IN " +
           "(SELECT a.id FROM Activity a WHERE a.semester.id = :semesterId)")
    int deleteBySemesterId(@Param("semesterId") Integer semesterId);

    /** Clear one activity's slot_acts (used during per-activity rebuild). */
    @Modifying
    @Query("DELETE FROM SlotActivity sa WHERE sa.activity.id = :activityId")
    int deleteByActivityId(@Param("activityId") Integer activityId);

    /**
     * Clear all slot_acts referencing any slot of a room — required before deleting
     * the room's slots (FK). Mirrors RoomController@destroy: $slot->slotActs()->delete().
     */
    @Modifying
    @Query("DELETE FROM SlotActivity sa WHERE sa.slot.room.id = :roomId")
    int deleteBySlotRoomId(@Param("roomId") Integer roomId);
}
