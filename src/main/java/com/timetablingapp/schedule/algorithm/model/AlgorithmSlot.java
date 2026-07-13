package com.timetablingapp.schedule.algorithm.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AlgorithmSlot {
    private final Integer id;
    private final Integer roomId;
    private final AlgorithmTime time;

    private AlgorithmRoom room;          // resolved via roomId
    private AlgorithmSlot parent;        // resolved: same-time slot in the parent room, if any
    private final List<AlgorithmSlot> childs = new ArrayList<>();

    public AlgorithmSlot(Integer id, Integer roomId, AlgorithmTime time) {
        this.id = id;
        this.roomId = roomId;
        this.time = time;
    }

    public void resolveRoom(GAContext context) {
        this.room = context.getRoomById(roomId);
    }

    /**
     * Links this slot to the slot at the same time_id in the parent room (if the room
     * has a parent) — booking either one must block both. Fixes the legacy bug that
     * compared a Slot id to a Room's parentId (different id spaces).
     */
    public void resolveParentChild(GAContext context) {
        if (room.getParentId() == -1) return;
        for (AlgorithmSlot other : context.getSlots()) {
            if (Objects.equals(other.getRoomId(), room.getParentId())
                    && Objects.equals(other.getTime().getId(), this.time.getId())) {
                this.parent = other;
                other.getChilds().add(this);
                break;
            }
        }
    }
}
