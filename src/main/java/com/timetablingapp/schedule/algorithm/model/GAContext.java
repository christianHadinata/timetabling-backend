package com.timetablingapp.schedule.algorithm.model;

import java.util.*;
import lombok.Getter;

@Getter
public class GAContext {
    private final List<AlgorithmActivity> activities;
    private final List<AlgorithmSlot> slots;
    private final List<AlgorithmRoom> rooms;
    private final List<com.timetablingapp.schedule.algorithm.io.Schedule> initialSchedules;

    private final Map<Integer, Integer> activityIndexById = new HashMap<>();   // FIX: init all three
    private final Map<Integer, Integer> slotIndexById     = new HashMap<>();
    private final Map<Integer, Integer> roomIndexById     = new HashMap<>();

    public GAContext(List<AlgorithmActivity> activities, List<AlgorithmSlot> slots,
                     List<AlgorithmRoom> rooms,
                     List<com.timetablingapp.schedule.algorithm.io.Schedule> initialSchedules) {
        this.activities = activities; this.slots = slots; this.rooms = rooms;
        this.initialSchedules = (initialSchedules != null) ? initialSchedules : List.of();
    }

    public void init() {
        for (int i = 0; i < rooms.size(); i++)    roomIndexById.put(rooms.get(i).getId(), i);
        for (int i = 0; i < slots.size(); i++)    slotIndexById.put(slots.get(i).getId(), i);
        for (int i = 0; i < activities.size(); i++) {
            activityIndexById.put(activities.get(i).getId(), i);
            activities.get(i).resolveSlots(this);
        }
        slots.forEach(s -> { s.resolveRoom(this); });
        slots.forEach(s -> s.resolveParentChild(this));   // second pass: parent/child need all rooms resolved
        initialSchedules.forEach(sch -> sch.init(this));
    }

    public int getActivityIndexById(Integer id) { return activityIndexById.get(id); }
    public AlgorithmActivity getActivityByIdx(int i) { return activities.get(i); }
    public AlgorithmSlot getSlotById(int id) { return slots.get(slotIndexById.get(id)); }
    public AlgorithmRoom getRoomById(Integer id) { return rooms.get(roomIndexById.get(id)); }

    /** Safe lookup for validation (unlike {@link #getSlotById}, never throws on a missing id). */
    public AlgorithmSlot findSlotById(int id) {
        Integer idx = slotIndexById.get(id);
        return idx != null ? slots.get(idx) : null;
    }

    /** Safe lookup (unlike {@link #getActivityIndexById}, never throws on a missing/unmodeled id). */
    public Integer findActivityIndexById(Integer id) {
        return activityIndexById.get(id);
    }

    /** Consecutive slots [startId .. startId+duration-1] — relies on seeded contiguous ids (A1). */
    public int[] getSlotStartToDuration(int startId, int duration) {
        int[] ids = new int[duration];
        for (int i = 0; i < duration; i++) ids[i] = startId + i;
        return ids;
    }

    public List<Integer> getSlotIdsByDayTime(Integer roomId, int day, int startH, int endH) {
        List<Integer> out = new ArrayList<>();
        for (AlgorithmSlot s : slots) {
            AlgorithmTime t = s.getTime();
            if (Objects.equals(s.getRoomId(), roomId) && t.getDay() == day
                    && t.getHour() >= startH && t.getHour() < endH) out.add(s.getId());
        }
        return out;
    }
}
