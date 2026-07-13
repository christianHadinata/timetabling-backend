package com.timetablingapp.schedule.algorithm.genetic;

import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Gene {
    private int activityIdx;   // index into GAContext.activities
    private int[] slotIds;     // chosen consecutive slot ids (may be empty)

    public Gene copy() {
        return new Gene(activityIdx, Arrays.copyOf(slotIds, slotIds.length));
    }

    @Override public String toString() {
        return "Activity " + activityIdx + " -> Slots " + Arrays.toString(slotIds);
    }
}
