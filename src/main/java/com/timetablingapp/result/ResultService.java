package com.timetablingapp.result;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.activity.ActivityRepository;
import com.timetablingapp.activity.constraint.ActivityConstraint;
import com.timetablingapp.activity.constraint.ActivityConstraintRepository;
import com.timetablingapp.activity.constraint.ConstraintType;
import com.timetablingapp.activity.type.ActivityType;
import com.timetablingapp.activity.type.ActivityTypeRepository;
import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.excel.ImportLog;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.course.CourseRepository;
import com.timetablingapp.room.Room;
import com.timetablingapp.room.RoomRepository;
import com.timetablingapp.schedule.validate.ValidateLockService;
import com.timetablingapp.semester.Semester;
import com.timetablingapp.semester.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResultService implements BaseCrudService<ResultResponse, ResultRequest, Integer> {

    private final ResultRepository resultRepository;
    private final ActivityRepository activityRepository;
    private final RoomRepository roomRepository;
    private final SemesterRepository semesterRepository;
    private final ValidateLockService validateLockService;
    private final ResultExcelService resultExcelService;
    private final ActivityConstraintRepository activityConstraintRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final CourseRepository courseRepository;

    // ---- reads ---------------------------------------------------------------

    /** All results, or scoped to a semester when semesterId is given. */
    public List<ResultResponse> findAll(Integer semesterId) {
        List<Result> rows = (semesterId != null)
                ? resultRepository.findBySemester_Id(semesterId)
                : resultRepository.findAll();
        return rows.stream().map(ResultResponse::fromEntity).toList();
    }

    @Override
    public List<ResultResponse> findAll() {
        return findAll(null);
    }

    @Override
    public ResultResponse findById(Integer id) {
        return ResultResponse.fromEntity(getOrThrow(id));
    }

    // ---- writes --------------------------------------------------------------

    @Override
    @Transactional
    public ResultResponse create(ResultRequest request) {
        Result r = new Result();
        apply(r, request, /*allowSemesterDefault*/ true);
        ResultResponse saved = ResultResponse.fromEntity(resultRepository.save(r));
        validateLockService.lock();
        return saved;
    }

    @Override
    @Transactional
    public ResultResponse update(Integer id, ResultRequest request) {
        Result r = getOrThrow(id);
        apply(r, request, /*allowSemesterDefault*/ false);
        ResultResponse saved = ResultResponse.fromEntity(resultRepository.save(r));
        validateLockService.lock();
        return saved;
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        resultRepository.delete(getOrThrow(id));
        validateLockService.lock();
    }

    /**
     * Clear every result for a semester.
     * Mirrors ResultController@destroy -> ResultRepository::deleteBasedOnSemesterId.
     */
    @Transactional
    public int deleteBySemester(Integer semesterId) {
        List<Result> rows = resultRepository.findBySemester_Id(semesterId);
        resultRepository.deleteAll(rows);   // honors @SQLDelete
        validateLockService.lock();
        return rows.size();
    }

    // ---- Excel import (mirrors ResultController@uploadExcelResult) ------------

    /**
     * Import scheduling results. For each row: if a Result already exists for the activity
     * (code, class, session) and its room resolves, update its room/day/times. Otherwise
     * find-or-create the Activity (+ lecturer & room-type constraints) and create a valid Result.
     */
    public ImportLog importResults(MultipartFile file) {
        ImportLog log = new ImportLog("result");
        Semester sem = currentSemester();

        for (ResultExcelService.ResultRow h : resultExcelService.parse(file)) {
            String id = h.courseCode() + "(" + h.courseClass() + ")-" + h.courseSession();
            try {
                Room room = (h.roomCode() != null)
                        ? roomRepository.findByRoomCode(h.roomCode()).orElse(null) : null;

                List<Result> existing = resultRepository.findByActivityKey(
                        h.courseCode(), h.courseClass(), h.courseSession());

                if (!existing.isEmpty()) {
                    if (room != null) {
                        Result res = existing.get(0);
                        res.setRoom(room);
                        res.setDay(String.valueOf(h.day()));
                        res.setStartTime(h.start());
                        res.setEndTime(h.end());
                        resultRepository.save(res);
                        log.ok(id);
                    }
                    continue;
                }

                // No existing result: find or create the activity.
                Activity activity = activityRepository
                        .findFirstByCourse_CodeAndCourseClassAndCourseSession(
                                h.courseCode(), h.courseClass(), h.courseSession())
                        .orElse(null);
                if (activity == null) {
                    activity = createActivityFromResult(sem, h);
                    if (activity == null) {
                        log.fail(id, "Mata kuliah tidak ada pada database.");
                        continue;
                    }
                }
                if (room == null) {
                    log.fail(id, "Ruangan tidak ada pada database: " + h.roomCode());
                    continue;
                }
                for (String nik : h.niks()) saveActivityConstraint(activity, ConstraintType.LECTURER, nik);
                saveActivityConstraint(activity, ConstraintType.ROOM_TYPE,
                        String.valueOf(room.getRoomType().getId()));

                Result res = new Result();
                res.setSemester(sem);
                res.setActivity(activity);
                res.setRoom(room);
                res.setDay(String.valueOf(h.day()));
                res.setStartTime(h.start());
                res.setEndTime(h.end());
                res.setValid(true);
                resultRepository.save(res);
                log.ok(id);
            } catch (Exception e) {
                log.fail(id, "Exception: " + e.getMessage());
            }
        }
        validateLockService.lock();
        return log;
    }

    /** Create an Activity implied by a result row. Returns null when the course code is unknown. */
    private Activity createActivityFromResult(Semester sem, ResultExcelService.ResultRow h) {
        var course = courseRepository.findByCode(h.courseCode()).orElse(null);
        if (course == null) return null;

        int hours = (h.start() != null && h.end() != null)
                ? Math.abs((int) Duration.between(h.start(), h.end()).toHours()) : 0;
        ActivityType type = activityTypeRepository.findByName(h.actType())
                .orElseGet(() -> activityTypeRepository.findById(1).orElse(null));

        Activity a = new Activity();
        a.setSemester(sem);
        a.setCourse(course);
        a.setCourseClass(h.courseClass());
        a.setCourseSession(h.courseSession());
        a.setDuration(hours);
        a.setQuota(h.quota() != null ? h.quota() : 0);
        a.setActivityType(type);
        return activityRepository.save(a);
    }

    private void saveActivityConstraint(Activity a, ConstraintType type, String value) {
        if (value == null || value.isBlank()) return;
        ActivityConstraint c = new ActivityConstraint();
        c.setActivity(a);
        c.setType(type);
        c.setValue(value.trim());
        activityConstraintRepository.save(c);
    }

    // ---- helpers -------------------------------------------------------------

    private void apply(Result r, ResultRequest req, boolean allowSemesterDefault) {
        Semester semester = (req.getSemesterId() != null)
                ? semesterRepository.findById(req.getSemesterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", req.getSemesterId()))
                : (allowSemesterDefault ? currentSemester() : r.getSemester());

        Activity activity = activityRepository.findById(req.getActivityId())
                .orElseThrow(() -> new ResourceNotFoundException("Activity", "id", req.getActivityId()));

        Room room = null;
        if (req.getRoomId() != null) {
            room = roomRepository.findById(req.getRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room", "id", req.getRoomId()));
        }

        r.setSemester(semester);
        r.setActivity(activity);
        r.setRoom(room);
        r.setDay(req.getDay());
        r.setStartTime(req.getStartTime());
        r.setEndTime(req.getEndTime());
        r.setValid(req.getValid() != null ? req.getValid() : Boolean.TRUE);
    }

    private Semester currentSemester() {
        return semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current semester is set"));
    }

    private Result getOrThrow(Integer id) {
        return resultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Result", "id", id));
    }
}
