package com.timetablingapp.schedule.slot.time;

import jakarta.persistence.*;
import lombok.*;

/**
 * Lookup table `times` (day 1..7 × hour 7..23). No timestamps, no soft-delete.
 * Seeded via Laravel SlotTableSeeder; treated as read-only master data.
 */
@Entity
@Table(name = "times")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Time {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private Integer day;    // 1..7 (1 = Monday)

    @Column(nullable = false)
    private Integer hour;   // 7..23
}
