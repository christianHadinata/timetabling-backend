package com.timetablingapp.schedule.slot;

import com.timetablingapp.room.Room;
import com.timetablingapp.schedule.slot.time.Time;
import jakarta.persistence.*;
import lombok.*;

/**
 * Table `slots` — one row per (room × time). No timestamps, no soft-delete.
 */
@Entity
@Table(name = "slots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.EAGER)   // time is always needed alongside a slot
    @JoinColumn(name = "time_id", nullable = false)
    private Time time;
}
