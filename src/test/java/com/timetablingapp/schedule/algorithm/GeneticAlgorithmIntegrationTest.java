package com.timetablingapp.schedule.algorithm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.timetablingapp.config.GAConfig;
import com.timetablingapp.schedule.algorithm.constraint.ConflictedActivityConstraint;
import com.timetablingapp.schedule.algorithm.constraint.ConflictedSlotsConstraint;
import com.timetablingapp.schedule.algorithm.constraint.Constraint;
import com.timetablingapp.schedule.algorithm.constraint.LecturerMovingConstraint;
import com.timetablingapp.schedule.algorithm.constraint.RoomIdleConstraint;
import com.timetablingapp.schedule.algorithm.constraint.activity.ActivityPairConstraint;
import com.timetablingapp.schedule.algorithm.constraint.activity.CourseClassConflict;
import com.timetablingapp.schedule.algorithm.constraint.activity.CourseConflict;
import com.timetablingapp.schedule.algorithm.constraint.activity.LecturerConflict;
import com.timetablingapp.schedule.algorithm.genetic.GeneticAlgorithm;
import com.timetablingapp.schedule.algorithm.genetic.operators.Crossover;
import com.timetablingapp.schedule.algorithm.genetic.operators.Mutation;
import com.timetablingapp.schedule.algorithm.genetic.operators.Selection;
import com.timetablingapp.schedule.algorithm.genetic.problem.FitnessFunction;
import com.timetablingapp.schedule.algorithm.genetic.problem.Problem;
import com.timetablingapp.schedule.algorithm.io.AlgorithmResult;
import com.timetablingapp.schedule.algorithm.io.Schedule;
import com.timetablingapp.schedule.algorithm.model.AlgorithmActivity;
import com.timetablingapp.schedule.algorithm.model.AlgorithmCourse;
import com.timetablingapp.schedule.algorithm.model.AlgorithmRoom;
import com.timetablingapp.schedule.algorithm.model.AlgorithmSlot;
import com.timetablingapp.schedule.algorithm.model.AlgorithmTime;
import com.timetablingapp.schedule.algorithm.model.GAContext;
import com.timetablingapp.schedule.algorithm.model.SlotsWithPriority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the ported GA package end-to-end against §14's verification criteria, without
 * Spring or a database: build a small fixture by hand (3 rooms x 3 hours), run the real
 * GeneticAlgorithm/Problem/constraints, and assert the invariants phase8.md §14 requires.
 */
class GeneticAlgorithmIntegrationTest {

    // room-major, hour-contiguous slot ids (A1): room1={1,2,3} room2={4,5,6} room3={7,8,9}, hours 7,8,9
    private static final int ROOM1_H7 = 1, ROOM1_H8 = 2, ROOM1_H9 = 3;
    private static final int ROOM2_H7 = 4, ROOM2_H8 = 5, ROOM2_H9 = 6;
    private static final int ROOM3_H7 = 7, ROOM3_H8 = 8, ROOM3_H9 = 9;

    private GAContext buildContext() {
        AlgorithmTime t7 = new AlgorithmTime(1, 1, 7);
        AlgorithmTime t8 = new AlgorithmTime(2, 1, 8);
        AlgorithmTime t9 = new AlgorithmTime(3, 1, 9);

        List<AlgorithmRoom> rooms = List.of(
                new AlgorithmRoom(1, "R1", 1, 50, "B1", "1", -1),
                new AlgorithmRoom(2, "R2", 1, 50, "B1", "1", -1),
                new AlgorithmRoom(3, "R3", 1, 50, "B1", "1", -1));

        List<AlgorithmSlot> slots = List.of(
                new AlgorithmSlot(ROOM1_H7, 1, t7), new AlgorithmSlot(ROOM1_H8, 1, t8), new AlgorithmSlot(ROOM1_H9, 1, t9),
                new AlgorithmSlot(ROOM2_H7, 2, t7), new AlgorithmSlot(ROOM2_H8, 2, t8), new AlgorithmSlot(ROOM2_H9, 2, t9),
                new AlgorithmSlot(ROOM3_H7, 3, t7), new AlgorithmSlot(ROOM3_H8, 3, t8), new AlgorithmSlot(ROOM3_H9, 3, t9));

        // A1/A2 share lecturer N1 -> must land on different hours (any room).
        AlgorithmActivity a1 = new AlgorithmActivity(10, 1,
                new AlgorithmCourse("CS101", "Wajib", 1, null), "A", 1, 1, 10, 1,
                candidates(ROOM1_H7, ROOM1_H8, ROOM2_H7, ROOM2_H8, ROOM3_H7, ROOM3_H8), Set.of("N1"));
        AlgorithmActivity a2 = new AlgorithmActivity(11, 1,
                new AlgorithmCourse("CS102", "Wajib", 1, null), "B", 1, 1, 10, 1,
                candidates(ROOM1_H7, ROOM1_H8, ROOM2_H7, ROOM2_H8, ROOM3_H7, ROOM3_H8), Set.of("N1"));

        // A3 (Wajib) / A4 (Pilihan): same tingkat+konsentrasi -> curriculum "bentrok" (CourseConflict)
        // if scheduled at the same hour. Their only free options (room1@h9 is locked, see below)
        // are room2@h9 and room3@h9 -> BOTH always land on hour 9 -> an unavoidable conflict,
        // deliberately, to prove the final result surfaces it rather than silently double-placing.
        AlgorithmActivity a3 = new AlgorithmActivity(12, 1,
                new AlgorithmCourse("CS201", "Wajib", 2, null), "A", 1, 1, 10, 1,
                candidates(ROOM2_H9, ROOM3_H9), Set.of("N2"));
        AlgorithmActivity a4 = new AlgorithmActivity(13, 1,
                new AlgorithmCourse("CS301", "Pilihan", 2, null), "C", 1, 1, 10, 1,
                candidates(ROOM2_H9, ROOM3_H9), Set.of("N3"));

        List<AlgorithmActivity> activities = new ArrayList<>(List.of(a1, a2, a3, a4));

        // Locked/pre-existing placement: activity 99 already sits in room1@h9 (slot 3).
        Schedule locked = new Schedule();
        locked.setActivityId(99);
        locked.setRoomId(1);
        locked.setDay(1);
        locked.setStartTime(9);
        locked.setEndTime(10);

        GAContext ctx = new GAContext(activities, slots, rooms, List.of(locked));
        ctx.init();
        return ctx;
    }

    private List<SlotsWithPriority> candidates(int... startSlotIds) {
        List<SlotsWithPriority> out = new ArrayList<>();
        for (int id : startSlotIds) out.add(new SlotsWithPriority(id, 0));
        return out;
    }

    private GeneticAlgorithm buildGa(Problem problem) {
        List<ActivityPairConstraint> pairConstraints = List.of(
                new LecturerConflict(), new CourseClassConflict(), new CourseConflict());
        List<Constraint> constraints = List.of(
                new ConflictedSlotsConstraint(),
                new ConflictedActivityConstraint(pairConstraints),
                new LecturerMovingConstraint(),
                new RoomIdleConstraint());
        FitnessFunction fitnessFunction = new FitnessFunction(constraints);
        Crossover crossover = new Crossover(fitnessFunction);
        Mutation mutation = new Mutation(fitnessFunction);
        Selection selection = new Selection();

        GAConfig cfg = new GAConfig();
        cfg.setPopulationSize(30);
        cfg.setGenerations(60);
        cfg.setCrossoverRate(0.8);
        cfg.setMutationRate(0.2);

        return new GeneticAlgorithm(problem, fitnessFunction, crossover, mutation, selection, cfg, null);
    }

    @Test
    void locksAreNeverReassigned() {
        GAContext ctx = buildContext();
        Problem problem = new Problem(ctx);
        problem.init();
        AlgorithmResult result = buildGa(problem).run(5);

        Schedule lockedEntry = result.getSchedule().stream()
                .filter(s -> s.getActivityId().equals(99)).findFirst().orElseThrow();
        assertEquals(List.of(ROOM1_H9), lockedEntry.getSlotIds(), "locked activity's slot must be untouched");
    }

    @Test
    void noLecturerConflictAndNoRoomDoubleBooking() {
        GAContext ctx = buildContext();
        Problem problem = new Problem(ctx);
        problem.init();
        AlgorithmResult result = buildGa(problem).run(5);

        // room double-booking: no physical slot id used by two different Schedule entries
        Set<Integer> seenSlots = new HashSet<>();
        for (Schedule s : result.getSchedule()) {
            for (Integer slotId : s.getSlotIds()) {
                assertTrue(seenSlots.add(slotId),
                        "slot " + slotId + " double-booked across schedule entries");
            }
        }

        // lecturer conflict: A1 (id 10) and A2 (id 11) share NIK N1 -> must differ in hour
        Integer hourA1 = hourOf(result, ctx, 10);
        Integer hourA2 = hourOf(result, ctx, 11);
        if (hourA1 != null && hourA2 != null) {
            assertTrue(!hourA1.equals(hourA2),
                    "A1/A2 share a lecturer but were scheduled at the same hour: " + hourA1);
        }
    }

    /**
     * A3 (Wajib) and A4 (Pilihan) can *only* ever be scheduled at hour 9 (their sole free
     * room1@h9 option is occupied by the lock), so they always collide under CourseConflict.
     * `Problem.setSchedule` alone only guards against literal physical-slot double-booking
     * (see genetic/problem/Problem.java `setSchedule`) — it does NOT re-check pair-constraints,
     * so the raw GA output can legitimately contain both "successfully" placed on different
     * physical slots at the same hour. {@link PairwiseConflictResolver} (wired into
     * {@code TimetableService.mapResult}) is the layer responsible for catching that before it
     * reaches a caller — this proves it actually does.
     */
    @Test
    void curriculumOverlapIsCaughtByPairwiseConflictResolver() {
        GAContext ctx = buildContext();
        Problem problem = new Problem(ctx);
        problem.init();
        AlgorithmResult result = buildGa(problem).run(5);

        PairwiseConflictResolver resolver = new PairwiseConflictResolver(
                List.of(new LecturerConflict(), new CourseClassConflict(), new CourseConflict()));
        Set<Integer> conflicted = resolver.findConflicts(result.getSchedule(), ctx, Set.of(99));

        boolean a3Placed = result.getSchedule().stream().anyMatch(s -> s.getActivityId().equals(12));
        boolean a4Placed = result.getSchedule().stream().anyMatch(s -> s.getActivityId().equals(13));
        if (a3Placed && a4Placed) {
            assertTrue(conflicted.contains(12) || conflicted.contains(13),
                    "A3/A4 both landed in the raw schedule at the same hour, but the resolver did "
                            + "not flag either one as conflicted");
        }
        assertTrue(!conflicted.contains(99), "the locked activity must never be flagged/reassigned");
    }

    private Integer hourOf(AlgorithmResult result, GAContext ctx, int activityId) {
        return result.getSchedule().stream()
                .filter(s -> s.getActivityId().equals(activityId))
                .findFirst()
                .map(s -> ctx.getSlotById(s.getSlotIds().get(0)).getTime().getHour())
                .orElse(null);
    }
}
