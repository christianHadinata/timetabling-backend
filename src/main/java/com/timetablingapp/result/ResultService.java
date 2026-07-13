package com.timetablingapp.result;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.activity.ActivityRepository;
import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.room.Room;
import com.timetablingapp.room.RoomRepository;
import com.timetablingapp.schedule.validate.ValidateLockService;
import com.timetablingapp.semester.Semester;
import com.timetablingapp.semester.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResultService implements BaseCrudService<ResultResponse, ResultRequest, Integer> {

    private final ResultRepository resultRepository;
    private final ActivityRepository activityRepository;
    private final RoomRepository roomRepository;
    private final SemesterRepository semesterRepository;
    private final ValidateLockService validateLockService;

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
