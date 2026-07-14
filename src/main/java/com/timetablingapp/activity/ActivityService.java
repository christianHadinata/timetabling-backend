package com.timetablingapp.activity;

import com.timetablingapp.activity.constraint.ActivityConstraint;
import com.timetablingapp.activity.constraint.ActivityConstraintRepository;
import com.timetablingapp.activity.constraint.ConstraintType;
import com.timetablingapp.activity.gap.ActivityGap;
import com.timetablingapp.activity.gap.ActivityGapRepository;
import com.timetablingapp.activity.paralel.ActivityParalel;
import com.timetablingapp.activity.paralel.ActivityParalelRepository;
import com.timetablingapp.activity.type.ActivityType;
import com.timetablingapp.activity.type.ActivityTypeRepository;
import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.excel.ImportLog;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.DuplicateResourceException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.course.Course;
import com.timetablingapp.course.CourseRepository;
import com.timetablingapp.jurusan.JurusanService;
import com.timetablingapp.result.ResultRepository;
import com.timetablingapp.room.Room;
import com.timetablingapp.room.RoomRepository;
import com.timetablingapp.room.type.RoomType;
import com.timetablingapp.room.type.RoomTypeRepository;
import com.timetablingapp.schedule.slot.act.SlotActivityRepository;
import com.timetablingapp.schedule.validate.ValidateLockService;
import com.timetablingapp.semester.Semester;
import com.timetablingapp.semester.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityService implements BaseCrudService<ActivityResponse, ActivityRequest, Integer> {

    private final ActivityRepository activityRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final ActivityConstraintRepository constraintRepository;
    private final ActivityParalelRepository paralelRepository;
    private final ActivityGapRepository gapRepository;
    private final CourseRepository courseRepository;
    private final SemesterRepository semesterRepository;
    private final JurusanService jurusanService;
    private final ResultRepository resultRepository;
    private final SlotActivityRepository slotActivityRepository;
    private final ValidateLockService validateLockService;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final ActivityExcelService activityExcelService;
    private final com.timetablingapp.lecturer.LecturerRepository lecturerRepository;

    // ---- reads ---------------------------------------------------------------

    /** Faculty-scoped list for a semester (defaults to current). Mirrors ActivityController@index. */
    public List<ActivityResponse> findAllByFacultyAndSemester(String faculty, Integer semesterId) {
        Integer sem = (semesterId != null) ? semesterId : currentSemester().getId();
        List<Integer> jurusanIds = jurusanService.getJurusanIds(faculty);
        return activityRepository
                .findBySemester_IdAndCourse_Jurusan_IdIn(sem, jurusanIds).stream()
                .map(this::toListResponse)
                .toList();
    }

    @Override
    public List<ActivityResponse> findAll() {
        return activityRepository.findAll().stream().map(this::toListResponse).toList();
    }

    /** Detail view — includes paralels + gaps. Mirrors ActivityController@show. */
    @Override
    public ActivityResponse findById(Integer id) {
        return toDetailResponse(getOrThrow(id));
    }

    // ---- writes --------------------------------------------------------------

    @Override
    @Transactional
    public ActivityResponse create(ActivityRequest request) {
        Semester semester = (request.getSemesterId() != null)
                ? semesterRepository.findById(request.getSemesterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", request.getSemesterId()))
                : currentSemester();

        // Duplicate guard — mirrors ActivityController::isDuplicate().
        if (activityRepository.existsBySemester_IdAndCourse_CodeAndCourseClassAndCourseSession(
                semester.getId(), request.getCourseCode(),
                request.getCourseClass(), request.getCourseSession())) {
            throw new DuplicateResourceException(
                    "Activity",
                    "course",
                    request.getCourseCode() + "(" + request.getCourseClass()
                            + ") session " + request.getCourseSession());
        }

        Activity activity = new Activity();
        activity.setSemester(semester);
        apply(activity, request);
        Activity saved = activityRepository.save(activity);

        syncConstraints(saved, request);
        validateLockService.lock();
        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public ActivityResponse update(Integer id, ActivityRequest request) {
        Activity activity = getOrThrow(id);
        apply(activity, request);              // semester is not changed on update (matches legacy)
        Activity saved = activityRepository.save(activity);

        syncConstraints(saved, request);
        syncParalels(saved, request);
        syncGaps(saved, request);
        validateLockService.lock();
        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Activity activity = getOrThrow(id);

        // Legacy ActivityController@destroy blocks delete when a scheduled Result uses it.
        if (resultRepository.existsByActivity_IdAndRoomIsNotNull(id)) {
            throw new BadRequestException(
                "Cannot delete activity: it is used by a scheduled result.");
        }

        // Cascade: remove any (unscheduled) results referencing this activity.
        resultRepository.deleteByActivity_Id(id);

        constraintRepository.deleteByActivity_Id(id);
        paralelRepository.deleteAllForActivity(id);
        gapRepository.deleteAllForActivity(id);
        slotActivityRepository.deleteByActivityId(id);   // slot_acts has no soft-delete
        activityRepository.delete(activity);
        validateLockService.lock();
    }

    /** Paralel candidates for the edit form. Mirrors ActivityController::getParalelCandidates(). */
    public List<ActivityResponse.ParalelDto> getParalelCandidates(Integer id) {
        Activity a = getOrThrow(id);
        return activityRepository.findBySemester_IdAndCourse_Jurusan_IdAndCourse_Tingkat(
                        a.getSemester().getId(),
                        a.getCourse().getJurusan().getId(),
                        a.getCourse().getTingkat()).stream()
                .map(this::toParalelDto)
                .toList();
    }

    // ---- Excel import (mirrors ActivityController@uploadExcel) ----------------

    /**
     * Import activities into the current semester. Per row validates: no duplicate
     * (semester, code, class, session); every lecturer NIK exists; course exists; every room
     * code exists; every room-type name exists. Then creates the activity and its constraints
     * (Lecturer=nik, RoomType=id, Room=id). Bad rows are logged and skipped.
     */
    public ImportLog importActivities(MultipartFile file) {
        ImportLog log = new ImportLog("activity");
        Semester sem = currentSemester();

        Map<String, Integer> roomTypeIdByName = roomTypeRepository.findAll().stream()
                .collect(Collectors.toMap(RoomType::getName, RoomType::getId, (a, b) -> a));
        Map<String, Integer> roomIdByCode = roomRepository.findAll().stream()
                .collect(Collectors.toMap(Room::getRoomCode, Room::getId, (a, b) -> a));

        for (ActivityExcelService.ActivityRow row : activityExcelService.parse(file)) {
            String id = row.courseCode() + "(" + row.courseClass() + ")-" + row.courseSession();
            try {
                if (activityRepository.existsBySemester_IdAndCourse_CodeAndCourseClassAndCourseSession(
                        sem.getId(), row.courseCode(), row.courseClass(), row.courseSession())) {
                    log.fail(id, "Ditemukan duplikat pada database untuk semester saat ini.");
                    continue;
                }
                if (row.lecturerNiks().stream().anyMatch(n -> !lecturerRepository.existsByNik(n))) {
                    log.fail(id, "Salah satu(atau lebih) NIK pengajar tidak ada pada database.");
                    continue;
                }
                Course course = courseRepository.findByCode(row.courseCode()).orElse(null);
                if (course == null) {
                    log.fail(id, "Mata kuliah tidak ada pada database.");
                    continue;
                }
                if (row.roomCodes().stream().anyMatch(c -> !roomIdByCode.containsKey(c))) {
                    log.fail(id, "Salah satu(atau lebih) Ruangan tidak ada pada database.");
                    continue;
                }
                if (row.roomTypeNames().stream().anyMatch(t -> !roomTypeIdByName.containsKey(t))) {
                    log.fail(id, "Salah satu(atau lebih) Tipe Ruangan tidak ada pada database.");
                    continue;
                }
                ActivityType type = activityTypeRepository.findByName(row.activityType())
                        .orElseGet(() -> activityTypeRepository.findById(1)
                                .orElseThrow(() -> new BadRequestException("No activity type available")));

                Activity a = new Activity();
                a.setSemester(sem);
                a.setCourse(course);
                a.setCourseClass(row.courseClass());
                a.setCourseSession(row.courseSession());
                a.setDuration(row.duration());
                a.setQuota(row.quota());
                a.setActivityType(type);
                Activity saved = activityRepository.save(a);

                for (String nik : row.lecturerNiks())
                    saveImportConstraint(saved, ConstraintType.LECTURER, nik);
                for (String rt : row.roomTypeNames())
                    saveImportConstraint(saved, ConstraintType.ROOM_TYPE, String.valueOf(roomTypeIdByName.get(rt)));
                for (String rc : row.roomCodes())
                    saveImportConstraint(saved, ConstraintType.ROOM, String.valueOf(roomIdByCode.get(rc)));

                log.ok(id);
            } catch (Exception e) {
                log.fail(id, "Exception: " + e.getMessage());
            }
        }
        validateLockService.lock();
        return log;
    }

    private void saveImportConstraint(Activity a, ConstraintType type, String value) {
        ActivityConstraint c = new ActivityConstraint();
        c.setActivity(a);
        c.setType(type);
        c.setValue(value.trim());
        constraintRepository.save(c);
    }

    // ---- helpers -------------------------------------------------------------

    private void apply(Activity activity, ActivityRequest request) {
        Course course = courseRepository.findByCode(request.getCourseCode())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "code", request.getCourseCode()));
        ActivityType type = activityTypeRepository.findById(request.getActivityTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("ActivityType", "id", request.getActivityTypeId()));
        activity.setCourse(course);
        activity.setCourseClass(request.getCourseClass());
        activity.setCourseSession(request.getCourseSession());
        activity.setDuration(request.getDuration());
        activity.setQuota(request.getQuota());
        activity.setActivityType(type);
    }

    /** Soft-delete existing constraints, then recreate — mirrors legacy store/update. */
    private void syncConstraints(Activity activity, ActivityRequest request) {
        constraintRepository.deleteByActivity_Id(activity.getId());
        createConstraints(activity, ConstraintType.LECTURER, request.getLecturerNiks());
        createConstraints(activity, ConstraintType.ROOM, toStrings(request.getRoomIds()));
        createConstraints(activity, ConstraintType.ROOM_TYPE, toStrings(request.getRoomTypeIds()));
    }

    private void createConstraints(Activity activity, ConstraintType type, List<String> values) {
        if (values == null) return;
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            ActivityConstraint c = new ActivityConstraint();
            c.setActivity(activity);
            c.setType(type);
            c.setValue(v.trim());
            constraintRepository.save(c);
        }
    }

    private void syncParalels(Activity activity, ActivityRequest request) {
        if (request.getParalelActivityIds() == null) return;
        paralelRepository.deleteAllForActivity(activity.getId());
        for (Integer withId : request.getParalelActivityIds()) {
            paralelRepository.save(new ActivityParalel(null, activity.getId(), withId));
        }
    }

    private void syncGaps(Activity activity, ActivityRequest request) {
        if (request.getGaps() == null) return;
        gapRepository.deleteAllForActivity(activity.getId());
        for (ActivityRequest.GapDto g : request.getGaps()) {
            gapRepository.save(new ActivityGap(null, activity.getId(), g.getActivityId(), g.getMinGap()));
        }
    }

    private List<String> toStrings(List<Integer> ids) {
        if (ids == null) return null;
        return ids.stream().map(String::valueOf).toList();
    }

    private Semester currentSemester() {
        return semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current semester is set"));
    }

    private Activity getOrThrow(Integer id) {
        return activityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", "id", id));
    }

    // ---- response mapping ----------------------------------------------------

    private ActivityResponse toListResponse(Activity a) {
        return baseResponse(a).build();
    }

    private ActivityResponse toDetailResponse(Activity a) {
        return baseResponse(a)
                .paralels(loadParalels(a.getId()))
                .gaps(loadGaps(a.getId()))
                .build();
    }

    private ActivityResponse.ActivityResponseBuilder baseResponse(Activity a) {
        // group this activity's constraints by type in one query
        Map<ConstraintType, List<String>> byType = constraintRepository.findByActivity_Id(a.getId())
                .stream()
                .collect(Collectors.groupingBy(
                        ActivityConstraint::getType,
                        Collectors.mapping(ActivityConstraint::getValue, Collectors.toList())));

        List<String> lecturerNiks = byType.getOrDefault(ConstraintType.LECTURER, List.of());
        List<Integer> roomIds = toInts(byType.get(ConstraintType.ROOM));
        List<Integer> roomTypeIds = toInts(byType.get(ConstraintType.ROOM_TYPE));

        return ActivityResponse.builder()
                .id(a.getId())
                .semesterId(a.getSemester() != null ? a.getSemester().getId() : null)
                .courseCode(a.getCourse() != null ? a.getCourse().getCode() : null)
                .courseName(a.getCourse() != null ? a.getCourse().getName() : null)
                .courseClass(a.getCourseClass())
                .courseSession(a.getCourseSession())
                .duration(a.getDuration())
                .quota(a.getQuota())
                .activityTypeId(a.getActivityType() != null ? a.getActivityType().getId() : null)
                .activityTypeName(a.getActivityType() != null ? a.getActivityType().getName() : null)
                .name(a.getName())
                .color(a.getCourse() != null ? a.getCourse().getColor() : null)
                .lecturerNiks(new ArrayList<>(lecturerNiks))
                .roomIds(roomIds)
                .roomTypeIds(roomTypeIds);
    }

    private List<ActivityResponse.ParalelDto> loadParalels(Integer id) {
        List<ActivityParalel> rows = paralelRepository.findByActivityIdOrWithActivityId(id, id);
        List<ActivityResponse.ParalelDto> out = new ArrayList<>();
        for (ActivityParalel p : rows) {
            Integer other = p.getActivityId().equals(id) ? p.getWithActivityId() : p.getActivityId();
            activityRepository.findById(other).ifPresent(o -> out.add(toParalelDto(o)));
        }
        return out;
    }

    private List<ActivityResponse.GapDto> loadGaps(Integer id) {
        List<ActivityGap> rows = gapRepository.findByActivityIdOrWithActivityId(id, id);
        List<ActivityResponse.GapDto> out = new ArrayList<>();
        for (ActivityGap g : rows) {
            Integer other = g.getActivityId().equals(id) ? g.getWithActivityId() : g.getActivityId();
            activityRepository.findById(other).ifPresent(o -> out.add(
                    ActivityResponse.GapDto.builder()
                            .id(o.getId())
                            .courseCode(o.getCourse().getCode())
                            .courseName(o.getCourse().getName())
                            .courseClass(o.getCourseClass())
                            .courseSession(o.getCourseSession())
                            .activityType(o.getActivityType().getName())
                            .minGap(g.getMinGap())
                            .build()));
        }
        return out;
    }

    private ActivityResponse.ParalelDto toParalelDto(Activity o) {
        return ActivityResponse.ParalelDto.builder()
                .id(o.getId())
                .courseCode(o.getCourse().getCode())
                .courseName(o.getCourse().getName())
                .courseClass(o.getCourseClass())
                .courseSession(o.getCourseSession())
                .activityType(o.getActivityType().getName())
                .build();
    }

    private List<Integer> toInts(List<String> vals) {
        if (vals == null) return List.of();
        return vals.stream().map(Integer::valueOf).toList();
    }
}
