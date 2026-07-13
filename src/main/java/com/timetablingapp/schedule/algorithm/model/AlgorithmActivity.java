package com.timetablingapp.schedule.algorithm.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class AlgorithmActivity {
    private Integer id;
    private Integer semesterId;
    private AlgorithmCourse course;
    private String courseClass;
    private Integer courseSession;
    private Integer activityTypeId;
    private Integer quota;
    private Integer duration;
    private List<SlotsWithPriority> slots;
    private Set<String> lecturerNiks;

    /** Drops candidates that fail the contiguous same-room/same-day run check (R1 guard). */
    public void resolveSlots(GAContext context) {
        slots.removeIf(slot -> !slot.resolveSlots(context, this.duration));
    }

    public boolean slotsIsEmpty() {
        return this.slots.isEmpty();
    }

    /** Random candidate among this activity's remaining free start-slots (or null if none left). */
    public int[] getRandomFreeSlot() {
        if (slots.isEmpty()) return null;
        SlotsWithPriority slot = slots.get(new Random().nextInt(slots.size()));
        return slot.getSlotIds();
    }

    /** Drops every candidate whose resolved slots overlap the given (now-occupied) slot ids. */
    public void removeSlotIfSlotExist(Set<Integer> slotSet) {
        List<SlotsWithPriority> removing = new ArrayList<>();
        for (SlotsWithPriority candidate : slots) {
            for (int id : candidate.getSlotIds()) {
                if (slotSet.contains(id)) {
                    removing.add(candidate);
                    break;
                }
            }
        }
        slots.removeAll(removing);
    }
}
