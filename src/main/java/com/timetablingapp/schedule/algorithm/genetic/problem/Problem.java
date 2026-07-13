package com.timetablingapp.schedule.algorithm.genetic.problem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.timetablingapp.schedule.algorithm.genetic.Chromosome;
import com.timetablingapp.schedule.algorithm.genetic.Gene;
import com.timetablingapp.schedule.algorithm.io.AlgorithmResult;
import com.timetablingapp.schedule.algorithm.model.AlgorithmActivity;
import com.timetablingapp.schedule.algorithm.model.AlgorithmSlot;
import com.timetablingapp.schedule.algorithm.model.GAContext;

/** NOT a Spring bean — one instance per generate() run (holds the mutable not-yet-scheduled list). */
public class Problem {
    private final GAContext context;
    private final List<AlgorithmActivity> nysAct;   // not-yet-scheduled activities
    private final AlgorithmResult result;

    public Problem(GAContext context) {
        this.context = context;
        this.nysAct = new ArrayList<>();
        this.result = new AlgorithmResult();
    }

    /** Call once, after GAContext.init(), before the GA runs. */
    public void init() {
        this.result.initSchedule(this.context);
        this.initNys();
    }

    public boolean isSolved() {
        return this.nysAct.isEmpty();
    }

    private void initNys() {
        this.context.getActivities().forEach(activity -> {
            if (!activity.slotsIsEmpty()) {
                this.nysAct.add(activity);
            } else if (!this.result.isScheduled(activity)) {
                this.result.addNotInsertedActivities(activity);
            }
        });
    }

    public Chromosome createValidChromosome() {
        Chromosome c = new Chromosome(this);
        for (AlgorithmActivity act : this.nysAct) {
            int[] freeSlotIds = act.getRandomFreeSlot();
            if (freeSlotIds == null) freeSlotIds = new int[0];
            int activityIndex = context.getActivityIndexById(act.getId());
            c.addGens(new Gene(activityIndex, freeSlotIds));
        }
        return c;
    }

    public void setSchedule(Chromosome bestChromosome) {
        Map<Integer, List<Integer>> slotActs = bestChromosome.getSlotUsage().getSlotActs();
        Map<Integer, List<Integer>> actSlots = bestChromosome.getSlotUsage().getActSlots();

        Set<Integer> conflictedActivities = new HashSet<>();   // idx
        slotActs.forEach((slotId, activityIdxs) -> {
            if (activityIdxs.size() > 1) conflictedActivities.addAll(activityIdxs);
        });

        // schedule the activities that have no slot conflict
        actSlots.forEach((activityIdx, slotIds) -> {
            if (!conflictedActivities.contains(activityIdx)) {
                addToScheduledAndRemoveNys(activityIdx, slotIds);
            }
        });

        // among the conflicted ones, salvage any whose slots are actually still free
        Set<Integer> stillConflictedActivityIds = new HashSet<>();
        actSlots.forEach((activityIdx, slotIds) -> {
            if (conflictedActivities.contains(activityIdx)) {
                boolean free = true;
                for (Integer slotId : slotIds) {
                    if (result.isSlotOccupied(slotId)) {
                        free = false;
                        Integer actId = this.context.getActivityByIdx(activityIdx).getId();
                        stillConflictedActivityIds.add(actId);
                        break;
                    }
                }
                if (free) addToScheduledAndRemoveNys(activityIdx, slotIds);
            }
        });

        this.result.addConflictedActivities(stillConflictedActivityIds);
    }

    private void addToScheduledAndRemoveNys(int activityIdx, List<Integer> slotIds) {
        AlgorithmActivity activity = context.getActivityByIdx(activityIdx);
        result.addSchedule(activity.getId(), slotIds);   // assumes slotIds sorted
        this.nysAct.remove(activity);
        this.removeFreeSlot(activity, slotIds);
        this.removeFreeSlotByActivity(activity, slotIds);
    }

    /** Also drops candidates overlapping this slot's parent/child rooms (they share physical space). */
    private void removeFreeSlot(AlgorithmActivity activity, List<Integer> slotIds) {
        Set<Integer> slotSet = new HashSet<>();
        slotIds.forEach(slotId -> {
            AlgorithmSlot slot = this.context.getSlotById(slotId);
            slotSet.add(slotId);
            if (slot.getParent() != null) slotSet.add(slot.getParent().getId());
            for (AlgorithmSlot child : slot.getChilds()) slotSet.add(child.getId());
        });
        activity.removeSlotIfSlotExist(slotSet);
    }

    /**
     * Cross-activity pruning (drop OTHER activities' candidates that would now conflict via
     * lecturer/course-class/curriculum bentrok) — a no-op in the legacy source and left so here.
     * ConflictedActivityConstraint catches these conflicts at fitness-evaluation time instead, so
     * setSchedule's own conflict handling above still keeps the final result correct.
     */
    // TODO Phase 8+
    private void removeFreeSlotByActivity(AlgorithmActivity activity, List<Integer> slotIds) {
    }

    public GAContext getContext() {
        return this.context;
    }

    public AlgorithmResult getResult() {
        return this.result;
    }
}
