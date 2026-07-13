package com.timetablingapp.schedule.slot.act;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.schedule.slot.Slot;
import jakarta.persistence.*;
import lombok.*;

/**
 * Table `slot_acts` — a valid (activity, slot) pair with a computed priority.
 * HARD delete (no deleted_at column), no timestamps.
 */
@Entity
@Table(name = "slot_acts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SlotActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    @Column(nullable = false)
    private Integer priority = 0;

    public SlotActivity(Activity activity, Slot slot, int priority) {
        this.activity = activity;
        this.slot = slot;
        this.priority = priority;
    }
}
