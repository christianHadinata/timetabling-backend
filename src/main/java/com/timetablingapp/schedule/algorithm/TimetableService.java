package com.timetablingapp.schedule.algorithm;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.activity.ActivityRepository;
import com.timetablingapp.activity.ActivityResponse;
import com.timetablingapp.activity.ActivityService;
import com.timetablingapp.activity.constraint.ActivityConstraint;
import com.timetablingapp.activity.constraint.ActivityConstraintRepository;
import com.timetablingapp.activity.constraint.ConstraintType;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.result.Result;
import com.timetablingapp.result.ResultRepository;
import com.timetablingapp.room.Room;
import com.timetablingapp.room.RoomRepository;
import com.timetablingapp.room.RoomResponse;
import com.timetablingapp.room.RoomService;
import com.timetablingapp.schedule.algorithm.dto.*;
import com.timetablingapp.schedule.algorithm.genetic.*;
import com.timetablingapp.schedule.algorithm.genetic.problem.Problem;
import com.timetablingapp.schedule.algorithm.io.AlgorithmResult;
import com.timetablingapp.schedule.algorithm.io.Schedule;
import com.timetablingapp.schedule.algorithm.model.*;
import com.timetablingapp.schedule.slot.Slot;
import com.timetablingapp.schedule.slot.SlotRepository;
import com.timetablingapp.schedule.slot.act.SlotActivity;
import com.timetablingapp.schedule.slot.act.SlotActivityRepository;
import com.timetablingapp.schedule.slot.act.SlotActivityService;
import com.timetablingapp.semester.Semester;
import com.timetablingapp.semester.SemesterRepository;
import com.timetablingapp.setting.SettingDetailResponse;
import com.timetablingapp.setting.SettingService;

import lombok.RequiredArgsConstructor;

/**
 * Entity <-> GA model mapping, GA orchestration (async), and GA Result -> frontend response mapping.
 * Port of TableController's getAlgorithm / saveData / getData / prepareScheduleData flows.
 */
@Service
@RequiredArgsConstructor
public class TimetableService {

    private static final int MAX_TRIALS = 10;   // matches legacy ScheduleService.run(10)

    private final ActivityRepository activityRepository;
    private final ActivityConstraintRepository activityConstraintRepository;
    private final SlotRepository slotRepository;
    private final SlotActivityRepository slotActivityRepository;
    private final RoomRepository roomRepository;
    private final ResultRepository resultRepository;
    private final SemesterRepository semesterRepository;
    private final SettingService settingService;
    private final GaEngineFactory gaEngineFactory;
    private final SlotActivityService slotActivityService;   // to revalidate on save
    private final SseService sseService;
    private final ActivityService activityService;           // reused for getData()
    private final RoomService roomService;                   // reused for getData()
    private final PairwiseConflictResolver pairwiseConflictResolver;

    /**
     * Self-reference through the Spring proxy: calling {@code this.runGenerate(...)} or
     * {@code this.generateSync(...)} directly from within the same bean bypasses the AOP
     * proxy, silently dropping {@code @Async}/{@code @Transactional}. Routing through
     * {@code self} keeps both annotations effective.
     */
    @Lazy
    private final TimetableService self;

    private final AtomicLong jobSeq = new AtomicLong();

    // ---- generate -----------------------------------------------------------

    /** Async entry: returns jobId immediately; progress + result stream over SSE. */
    public String startGenerate(GenerateRequest req) {
        String jobId = "gen-" + jobSeq.incrementAndGet();
        self.runGenerate(jobId, req);          // @Async — returns instantly
        return jobId;
    }

    @Async("gaExecutor")
    public void runGenerate(String jobId, GenerateRequest req) {
        try {
            GenerateResponse resp = self.generateSync(req, (trial, gen, hard, soft, progress) ->
                    sseService.broadcast(jobId, ProgressEvent.running(jobId, trial, gen, hard, soft, progress)));
            sseService.broadcast(jobId, ProgressEvent.completed(jobId, resp));
        } catch (Exception e) {
            sseService.broadcast(jobId, ProgressEvent.error(jobId, e.getMessage()));
        } finally {
            sseService.complete(jobId);
        }
    }

    /** Synchronous core — also usable directly via ?wait=true and by tests. */
    @Transactional(readOnly = true)
    public GenerateResponse generateSync(GenerateRequest req, GaProgressListener listener) {
        Integer semesterId = currentSemester().getId();
        SettingDetailResponse setting = (req.getSettingId() != null)
                ? settingService.findDetail(req.getSettingId()) : null;

        List<AlgorithmActivity> activities = loadActivities(semesterId, setting);
        List<AlgorithmSlot> slots = slotRepository.findAllBy().stream().map(this::toSlot).toList();
        List<AlgorithmRoom> rooms = roomRepository.findAll().stream().map(this::toRoom).toList();
        List<Schedule> locked = mapLocked(req.getLockedSchedules());

        GAContext ctx = new GAContext(activities, slots, rooms, locked);
        ctx.init();
        Problem problem = new Problem(ctx);
        problem.init();

        GeneticAlgorithm ga = gaEngineFactory.create(problem, listener);
        AlgorithmResult result = ga.run(MAX_TRIALS);
        Set<Integer> lockedIds = locked.stream().map(Schedule::getActivityId).collect(Collectors.toSet());
        return mapResult(result, ctx, lockedIds);
    }

    // ---- mapping: entities → GA models ---------------------------------------

    private List<AlgorithmActivity> loadActivities(Integer semesterId, SettingDetailResponse setting) {
        // TODO faculty-scope: restrict to Jurusan.jurusanIds() once a request-scoped
        // caller faculty is threaded through generate() (see phase8.md §9 / R6).
        List<Activity> entities = activityRepository.findAllForScheduling(semesterId);
        Set<Integer> allowActIds  = idSet(setting, "activity");       // CUSTOM_ACTIVITY
        Set<Integer> allowType    = idSet(setting, "activityType");   // ACTIVITY_TYPE
        Set<Integer> allowJurusan = idSet(setting, "jurusan");        // JURUSAN

        List<AlgorithmActivity> out = new ArrayList<>();
        for (Activity a : entities) {
            if (setting != null) {
                if (!allowActIds.isEmpty() && !allowActIds.contains(a.getId())) continue;
                if (!allowType.isEmpty() && !allowType.contains(a.getActivityType().getId())) continue;
                if (!allowJurusan.isEmpty() && a.getCourse().getJurusan() != null
                        && !allowJurusan.contains(a.getCourse().getJurusan().getId())) continue;
            }
            out.add(toActivity(a, setting));
        }
        return out;
    }

    private AlgorithmActivity toActivity(Activity a, SettingDetailResponse setting) {
        var c = a.getCourse();
        AlgorithmCourse course = new AlgorithmCourse(
                c.getCode(), c.getType() != null ? c.getType().name() : null,
                c.getTingkat() != null ? c.getTingkat() : 0, c.getKonsentrasi());

        Set<String> niks = new HashSet<>();
        for (ActivityConstraint ac : activityConstraintRepository.findByActivity_Id(a.getId())) {
            if (ac.getType() == ConstraintType.LECTURER) niks.add(ac.getValue());
        }

        List<SlotsWithPriority> slots = new ArrayList<>();
        for (SlotActivity sa : slotActivityRepository.findByActivity_Id(a.getId())) {
            if (passesSlotFilter(sa.getSlot(), setting)) {
                slots.add(new SlotsWithPriority(sa.getSlot().getId(), sa.getPriority()));
            }
        }
        return new AlgorithmActivity(a.getId(), a.getSemester().getId(), course,
                a.getCourseClass(), a.getCourseSession(), a.getActivityType().getId(),
                a.getQuota(), a.getDuration(), slots, niks);
    }

    /** Setting slot filter: ROOM_TYPE, ROOM_OWNER, WAKTU (hour), HARI (day). */
    private boolean passesSlotFilter(Slot slot, SettingDetailResponse setting) {
        if (setting == null) return true;
        var room = slot.getRoom(); var time = slot.getTime();
        Set<Integer> types = idSet(setting, "roomType");
        Set<Integer> owners = idSet(setting, "room");
        Set<Integer> hours = idSet(setting, "waktu");
        Set<Integer> days  = idSet(setting, "hari");
        if (!types.isEmpty() && !types.contains(room.getRoomType().getId())) return false;
        if (!owners.isEmpty() && !owners.contains(room.getId())) return false;
        if (!hours.isEmpty() && !hours.contains(time.getHour())) return false;
        if (!days.isEmpty()  && !days.contains(time.getDay())) return false;
        return true;
    }

    private AlgorithmSlot toSlot(Slot s) {
        var t = s.getTime();
        return new AlgorithmSlot(s.getId(), s.getRoom().getId(),
                new AlgorithmTime(t.getId(), t.getDay(), t.getHour()));
    }
    private AlgorithmRoom toRoom(Room r) {
        return new AlgorithmRoom(r.getId(), r.getRoomCode(), r.getRoomType().getId(),
                r.getCapacity(), r.getBuilding(), r.getFloor(),
                r.getParentRoom() != null ? r.getParentRoom().getId() : -1);
    }
    private List<Schedule> mapLocked(List<ScheduleDto> locked) {
        if (locked == null) return List.of();
        List<Schedule> out = new ArrayList<>();
        for (ScheduleDto d : locked) {
            Schedule s = new Schedule();
            s.setActivityId(d.getActivityId()); s.setRoomId(d.getRoomId());
            s.setDay(d.getDay()); s.setStartTime(d.getStartTime()); s.setEndTime(d.getEndTime());
            out.add(s);
        }
        return out;
    }

    // ---- mapping: GA Result → frontend response (port of getAlgorithm tail) --

    private GenerateResponse mapResult(AlgorithmResult result, GAContext ctx, Set<Integer> lockedIds) {
        List<Schedule> placed = result.getSchedule().stream()
                .filter(sch -> sch.getSlotIds() != null && !sch.getSlotIds().isEmpty())
                .toList();

        // Final safety net: Problem.setSchedule only guards physical-slot double-booking: an
        // unavoidable lecturer/course-class/curriculum conflict can still slip through as two
        // "successfully scheduled" activities on different slots at the same day+hour. Demote
        // those (but never a locked one) before they reach the caller — see PairwiseConflictResolver.
        Set<Integer> pairwiseConflicted = pairwiseConflictResolver.findConflicts(placed, ctx, lockedIds);

        List<ScheduleDto> inserted = new ArrayList<>();
        for (Schedule sch : placed) {
            if (pairwiseConflicted.contains(sch.getActivityId())) continue;
            AlgorithmSlot first = ctx.getSlotById(sch.getSlotIds().get(0));
            AlgorithmActivity act = ctx.getActivityByIdx(ctx.getActivityIndexById(sch.getActivityId()));
            inserted.add(new ScheduleDto(act.getId(), first.getRoomId(),
                    first.getTime().getDay(), first.getTime().getHour(),
                    first.getTime().getHour() + act.getDuration()));
        }

        Set<Integer> conflictSet = new HashSet<>(result.getConflictedActIds());
        conflictSet.addAll(pairwiseConflicted);
        List<Integer> conflicts = conflictSet.stream().sorted().toList();

        Set<Integer> notInsertedSet = new HashSet<>(conflictSet);
        notInsertedSet.addAll(result.getNotInsertedActIds());
        List<Integer> notInserted = notInsertedSet.stream().sorted().toList();

        return GenerateResponse.result(inserted, conflicts, notInserted);
    }

    // ---- save (port of TableController::saveData) ---------------------------

    @Transactional
    public void save(SaveTimetableRequest req) {
        Integer semesterId = currentSemester().getId();
        resultRepository.deleteBySemester_Id(semesterId);       // clear current semester's results

        for (Integer actId : req.getNotInserted()) {
            Result r = new Result();
            r.setSemester(semesterRepository.getReferenceById(semesterId));
            r.setActivity(activityRepository.getReferenceById(actId));
            r.setValid(false);
            resultRepository.save(r);
        }
        for (ScheduleDto d : req.getSchedules()) {
            Result r = new Result();
            r.setSemester(semesterRepository.getReferenceById(semesterId));
            r.setActivity(activityRepository.getReferenceById(d.getActivityId()));
            r.setRoom(roomRepository.getReferenceById(d.getRoomId()));
            r.setDay(String.valueOf(d.getDay()));
            r.setStartTime(LocalTime.of(d.getStartTime(), 0));
            r.setEndTime(LocalTime.of(d.getEndTime(), 0));
            r.setValid(true);
            resultRepository.save(r);
        }
        slotActivityService.revalidate();   // Laravel: Activity::validateSlots(...) after save
    }

    // ---- read: prepareScheduleData / getData --------------------------------

    @Transactional(readOnly = true)
    public ScheduleDataResponse prepareScheduleData(Integer semesterId) {
        List<Result> results = resultRepository.findBySemester_Id(semesterId);
        List<ScheduleDto> inserted = new ArrayList<>();
        Set<Integer> placed = new HashSet<>();
        for (Result r : results) {
            if (!Boolean.TRUE.equals(r.getValid()) || r.getRoom() == null || r.getDay() == null) continue;
            inserted.add(new ScheduleDto(r.getActivity().getId(), r.getRoom().getId(),
                    Integer.parseInt(r.getDay()), r.getStartTime().getHour(), r.getEndTime().getHour()));
            placed.add(r.getActivity().getId());
        }
        List<Integer> notInserted = activityRepository.findBySemester_Id(semesterId).stream()
                .map(Activity::getId).filter(id -> !placed.contains(id)).toList();
        return new ScheduleDataResponse(inserted, notInserted);
    }

    /** Display-only aggregation for the timetable grid — no algorithm logic. */
    @Transactional(readOnly = true)
    public TimetableDataResponse getData() {
        Map<Integer, ActivityResponse> activities = activityService.findAll().stream()
                .collect(Collectors.toMap(ActivityResponse::getId, a -> a));
        Map<Integer, RoomResponse> rooms = roomService.findAll().stream()
                .collect(Collectors.toMap(RoomResponse::getId, r -> r));
        return new TimetableDataResponse(activities, rooms);
    }

    // ---- helpers ------------------------------------------------------------

    private Set<Integer> idSet(SettingDetailResponse setting, String key) {
        if (setting == null || setting.getConstraints() == null) return Set.of();
        List<String> vals = setting.getConstraints().getOrDefault(key, List.of());
        Set<Integer> out = new HashSet<>();
        for (String v : vals) try { out.add(Integer.valueOf(v)); } catch (NumberFormatException ignored) {}
        return out;
    }
    private Semester currentSemester() {
        return semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current semester is set"));
    }
}
