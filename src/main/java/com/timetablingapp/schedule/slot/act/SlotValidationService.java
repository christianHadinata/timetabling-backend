package com.timetablingapp.schedule.slot.act;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.activity.ActivityRepository;
import com.timetablingapp.activity.constraint.ActivityConstraint;
import com.timetablingapp.activity.constraint.ActivityConstraintRepository;
import com.timetablingapp.course.Course;
import com.timetablingapp.course.CourseType;
import com.timetablingapp.lecturer.time.LecturerTimeNA;
import com.timetablingapp.lecturer.time.LecturerTimeNARepository;
import com.timetablingapp.lecturer.time.LecturerTimeType;
import com.timetablingapp.result.Result;
import com.timetablingapp.result.ResultRepository;
import com.timetablingapp.room.Room;
import com.timetablingapp.room.RoomRepository;
import com.timetablingapp.room.available.RoomAvailable;
import com.timetablingapp.room.available.RoomAvailableRepository;
import com.timetablingapp.schedule.slot.Slot;
import com.timetablingapp.schedule.slot.SlotRepository;
import com.timetablingapp.schedule.slot.time.Time;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Port of Activity::validateSlots() (the ismodel=false branch) plus isValidCreate()
 * and getPriority(). For every UNscheduled activity of a semester, computes the set of
 * valid (activity, slot) pairs and their priority, and materialises them into slot_acts.
 *
 * See implementation-specs/phase7.md §6 for the full algorithm mapping.
 */
@Service
@RequiredArgsConstructor
public class SlotValidationService {

    private static final int MIN_HOUR = 7;
    private static final int MAX_HOUR = 24;   // exclusive
    private static final int MAX_DAY  = 7;

    private final SlotRepository slotRepository;
    private final SlotActivityRepository slotActivityRepository;
    private final ActivityRepository activityRepository;
    private final ActivityConstraintRepository activityConstraintRepository;
    private final RoomRepository roomRepository;
    private final RoomAvailableRepository roomAvailableRepository;
    private final LecturerTimeNARepository lecturerTimeRepository;
    private final ResultRepository resultRepository;

    /** Preloaded activity constraints grouped by type. */
    private record ConstraintBundle(Set<String> lecturerNiks,
                                    Set<Integer> roomIds,
                                    Set<Integer> roomTypeIds) {
        static ConstraintBundle empty() {
            return new ConstraintBundle(new HashSet<>(), new HashSet<>(), new HashSet<>());
        }
    }

    /** Per-day conflict matrices (index by hour 7..23). */
    private static final class DayMatrix {
        final Map<Integer, boolean[]> roomBlocked = new HashMap<>();   // roomId -> [hour]
        final List<Set<String>> lecturerNA       = newHourSets();
        final List<Set<String>> lecturerPriority = newHourSets();
        final List<Set<String>> courseTaken      = newHourSets();
        final List<Set<String>> bentrokWajib     = newHourSets();
        final List<Set<String>> bentrokPilihan   = newHourSets();

        private static List<Set<String>> newHourSets() {
            List<Set<String>> l = new ArrayList<>(MAX_HOUR);
            for (int i = 0; i < MAX_HOUR; i++) l.add(new HashSet<>());
            return l;
        }
    }

    /**
     * Recompute slot_acts for every unscheduled activity of the given semester.
     * Port of Activity::validateSlots($acts) — the ismodel=false branch.
     */
    @Transactional
    public int revalidate(Integer semesterId) {
        // ---- load master data ----------------------------------------------
        List<Activity> activities = activityRepository.findBySemester_Id(semesterId);
        Map<Integer, Activity> activityById = new HashMap<>();
        for (Activity a : activities) activityById.put(a.getId(), a);

        List<Slot> slots = slotRepository.findAllBy();
        List<Room> rooms = roomRepository.findAll();
        Map<Integer, ConstraintBundle> constraintsByActivity = loadConstraints();
        Map<Integer, List<Room>> childrenByParent = indexChildren(rooms);

        // day -> matrix
        Map<Integer, DayMatrix> data = initMatrices(rooms);
        populateLecturerUnavailability(data);
        Set<Integer> scheduled = populateFromResults(
                semesterId, data, activityById, constraintsByActivity, childrenByParent);

        // ---- per-activity rebuild ------------------------------------------
        int written = 0;
        for (Activity activity : activities) {
            if (scheduled.contains(activity.getId())) {
                continue;   // already placed → leave its slot_acts untouched
            }
            slotActivityRepository.deleteByActivityId(activity.getId());

            ConstraintBundle cb = constraintsByActivity
                    .getOrDefault(activity.getId(), ConstraintBundle.empty());
            List<SlotActivity> batch = new ArrayList<>();
            for (Slot slot : slots) {
                if (isValidCreate(slot, activity, cb, data)) {
                    int priority = computePriority(slot, activity, cb, data);
                    batch.add(new SlotActivity(activity, slot, priority));
                }
            }
            slotActivityRepository.saveAll(batch);
            written += batch.size();
        }
        return written;
    }

    // ---- matrix construction ----------------------------------------------------

    private Map<Integer, DayMatrix> initMatrices(List<Room> rooms) {
        // roomId -> day -> availability window (last wins, mirrors Laravel avails keyBy day)
        Map<Integer, Map<Integer, RoomAvailable>> availByRoomDay = new HashMap<>();
        for (RoomAvailable av : roomAvailableRepository.findAll()) {
            availByRoomDay
                .computeIfAbsent(av.getRoom().getId(), k -> new HashMap<>())
                .put(av.getDay(), av);
        }

        Map<Integer, DayMatrix> data = new HashMap<>();
        for (int day = 1; day <= MAX_DAY; day++) {
            DayMatrix m = new DayMatrix();
            for (Room room : rooms) {
                boolean[] blocked = new boolean[MAX_HOUR];
                RoomAvailable av = availByRoomDay
                        .getOrDefault(room.getId(), Map.of()).get(day);
                for (int h = MIN_HOUR; h < MAX_HOUR; h++) {
                    if (av != null) {
                        int start = av.getStartTime().getHour();
                        int end   = av.getEndTime().getHour();
                        blocked[h] = !(h >= start && h < end);
                    } else {
                        blocked[h] = true;   // no window that day → fully blocked
                    }
                }
                m.roomBlocked.put(room.getId(), blocked);
            }
            data.put(day, m);
        }
        return data;
    }

    private void populateLecturerUnavailability(Map<Integer, DayMatrix> data) {
        for (LecturerTimeNA na : lecturerTimeRepository.findAll()) {
            Integer day = na.getDay();
            DayMatrix m = data.get(day);
            if (m == null) continue;
            String nik = na.getLecturer().getNik();
            int s = na.getStartTime().getHour();
            int e = na.getEndTime().getHour();
            for (int h = s; h < e && h < MAX_HOUR; h++) {
                if (h < MIN_HOUR) continue;
                if (na.getType() == LecturerTimeType.PRIORITY) {
                    m.lecturerPriority.get(h).add(nik);
                } else {
                    m.lecturerNA.get(h).add(nik);
                }
            }
        }
    }

    /** Block slots already occupied by valid results; return scheduled activity ids. */
    private Set<Integer> populateFromResults(
            Integer semesterId,
            Map<Integer, DayMatrix> data,
            Map<Integer, Activity> activityById,
            Map<Integer, ConstraintBundle> constraintsByActivity,
            Map<Integer, List<Room>> childrenByParent) {

        Set<Integer> scheduled = new HashSet<>();
        for (Result res : resultRepository.findBySemester_Id(semesterId)) {
            if (Boolean.FALSE.equals(res.getValid())) continue;
            if (res.getDay() == null || res.getRoom() == null
                    || res.getStartTime() == null || res.getEndTime() == null) continue;

            Activity activity = activityById.get(res.getActivity().getId());
            if (activity == null) continue;               // result for another jurusan/semester
            ConstraintBundle cb = constraintsByActivity
                    .getOrDefault(activity.getId(), ConstraintBundle.empty());

            int day   = Integer.parseInt(res.getDay());
            int start = res.getStartTime().getHour();
            int end   = res.getEndTime().getHour();
            DayMatrix m = data.get(day);
            if (m == null) continue;

            boolean wajib = isWajib(activity);
            String bedaKey = bedaKey(activity.getCourse());
            String samaKey = bedaKey + "," + activity.getCourseClass();
            String courseKey = activity.getCourse().getCode() + activity.getCourseClass();

            for (int h = start; h < end && h < MAX_HOUR; h++) {
                if (h < MIN_HOUR) continue;
                if (wajib) {
                    m.bentrokWajib.get(h).add(samaKey);
                    m.bentrokWajib.get(h).add(bedaKey);
                } else {
                    m.bentrokPilihan.get(h).add(bedaKey);
                }
                m.courseTaken.get(h).add(courseKey);
                for (String nik : cb.lecturerNiks()) m.lecturerNA.get(h).add(nik);

                Room room = res.getRoom();
                blockRoom(m, room.getId(), h);
                if (room.getParentRoom() != null) blockRoom(m, room.getParentRoom().getId(), h);
                for (Room child : childrenByParent.getOrDefault(room.getId(), List.of())) {
                    blockRoom(m, child.getId(), h);
                }
            }
            scheduled.add(activity.getId());
        }
        return scheduled;
    }

    private void blockRoom(DayMatrix m, Integer roomId, int hour) {
        boolean[] arr = m.roomBlocked.get(roomId);
        if (arr != null) arr[hour] = true;
    }

    // ---- validity check (port of isValidCreate) ---------------------------------

    private boolean isValidCreate(Slot slot, Activity activity,
                                  ConstraintBundle cb, Map<Integer, DayMatrix> data) {
        Room room = slot.getRoom();
        Time time = slot.getTime();
        int day = time.getDay();
        int hour = time.getHour();
        DayMatrix m = data.get(day);
        if (m == null) return false;

        // 1. time boundary
        if (hour + activity.getDuration() >= MAX_HOUR) return false;

        // 2. room free for the whole duration
        boolean[] blocked = m.roomBlocked.get(room.getId());
        if (blocked == null) return false;
        for (int i = 0; i < activity.getDuration(); i++) {
            if (blocked[hour + i]) return false;
        }

        // 3. activity room constraint
        if (!cb.roomIds().isEmpty() && !cb.roomIds().contains(room.getId())) return false;

        // 4. activity room-type constraint
        Integer roomTypeId = room.getRoomType() != null ? room.getRoomType().getId() : null;
        if (!cb.roomTypeIds().isEmpty() && !cb.roomTypeIds().contains(roomTypeId)) return false;

        // 5. lecturer not-available / already teaching
        for (String nik : cb.lecturerNiks()) {
            if (m.lecturerNA.get(hour).contains(nik)) return false;
        }

        // 6. room capacity
        if (room.getCapacity() < activity.getQuota()) return false;

        // 7. same course+class, different session already placed this hour
        String courseKey = activity.getCourse().getCode() + activity.getCourseClass();
        if (m.courseTaken.get(hour).contains(courseKey)) return false;

        // 8. curriculum overlap (bentrok)
        String bedaKey = bedaKey(activity.getCourse());
        if (isWajib(activity)) {
            if (m.bentrokWajib.get(hour).contains(bedaKey + "," + activity.getCourseClass())) {
                return false;                                               // wajib vs wajib (samaKey)
            }
            if (m.bentrokPilihan.get(hour).contains(bedaKey)) return false; // wajib vs pilihan
        } else {
            if (m.bentrokWajib.get(hour).contains(bedaKey)) return false;   // pilihan vs wajib
        }
        return true;
    }

    // ---- priority (port of getPriority) -----------------------------------------

    private int computePriority(Slot slot, Activity activity,
                                ConstraintBundle cb, Map<Integer, DayMatrix> data) {
        Time time = slot.getTime();
        int day = time.getDay();
        int hour = time.getHour();
        DayMatrix m = data.get(day);
        int priority = 0;
        for (String nik : cb.lecturerNiks()) {
            if (m.lecturerPriority.get(hour).contains(nik)) priority++;
        }
        Course course = activity.getCourse();
        String jenjang = (course.getJurusan() != null && course.getJurusan().getJenjang() != null)
                ? course.getJurusan().getJenjang().name()   // "S1", "D3", ...
                : "";
        priority += course.getPriorityValue(day, hour, jenjang);
        return priority;
    }

    // ---- helpers ----------------------------------------------------------------

    private Map<Integer, ConstraintBundle> loadConstraints() {
        Map<Integer, ConstraintBundle> map = new HashMap<>();
        for (ActivityConstraint c : activityConstraintRepository.findAll()) {
            ConstraintBundle b = map.computeIfAbsent(
                    c.getActivity().getId(), k -> ConstraintBundle.empty());
            switch (c.getType()) {
                case LECTURER  -> b.lecturerNiks().add(c.getValue());
                case ROOM      -> b.roomIds().add(Integer.valueOf(c.getValue()));
                case ROOM_TYPE -> b.roomTypeIds().add(Integer.valueOf(c.getValue()));
            }
        }
        return map;
    }

    private Map<Integer, List<Room>> indexChildren(List<Room> rooms) {
        Map<Integer, List<Room>> byParent = new HashMap<>();
        for (Room r : rooms) {
            if (r.getParentRoom() != null) {
                byParent.computeIfAbsent(r.getParentRoom().getId(), k -> new ArrayList<>()).add(r);
            }
        }
        return byParent;
    }

    private boolean isWajib(Activity a) {
        return a.getCourse() != null && a.getCourse().getType() == CourseType.Wajib;
    }

    private String bedaKey(Course course) {
        return course.getTingkat() + "," + course.getKonsentrasi();
    }
}
