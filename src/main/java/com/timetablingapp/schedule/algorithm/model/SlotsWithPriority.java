package com.timetablingapp.schedule.algorithm.model;

import java.util.Objects;

import lombok.Getter;
import lombok.Setter;

/** One candidate *start* slot for an activity, expanded to its `duration` consecutive slot ids. */
@Getter @Setter
public class SlotsWithPriority {
    private int[] slots;          // resolved consecutive slot ids (empty until resolveSlots is called)
    private final int slotStartId;
    private final int priority;

    public SlotsWithPriority(int slotStartId, int priority) {
        this.slotStartId = slotStartId;
        this.priority = priority;
    }

    /**
     * Resolves {@code slotStartId..slotStartId+duration-1} and returns whether that run is
     * actually a contiguous same-room, same-day, consecutive-hour block (R1 guard). The GA's
     * arithmetic id-walk (A1) assumes seeded room-major/hour-contiguous ids; if the DB's slots
     * aren't laid out that way, a "consecutive id" can land in a different room or day. When
     * that happens this candidate is invalid and must be dropped rather than silently used.
     */
    public boolean resolveSlots(GAContext context, int duration) {
        int[] ids = context.getSlotStartToDuration(slotStartId, duration);
        AlgorithmSlot first = context.findSlotById(ids[0]);
        if (first == null) return false;
        for (int i = 0; i < ids.length; i++) {
            AlgorithmSlot s = context.findSlotById(ids[i]);
            if (s == null
                    || !Objects.equals(s.getRoomId(), first.getRoomId())
                    || s.getTime().getDay() != first.getTime().getDay()
                    || s.getTime().getHour() != first.getTime().getHour() + i) {
                return false;
            }
        }
        this.slots = ids;
        return true;
    }

    public int[] getSlotIds() {
        return slots;
    }
}
