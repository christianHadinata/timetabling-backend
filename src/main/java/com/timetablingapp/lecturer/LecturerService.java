package com.timetablingapp.lecturer;

import com.timetablingapp.activity.constraint.ActivityConstraintRepository;
import com.timetablingapp.activity.constraint.ConstraintType;
import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.DuplicateResourceException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.lecturer.time.LecturerTimeNARepository;
import com.timetablingapp.lecturer.time.LecturerTimeResponse;
import com.timetablingapp.lecturer.time.LecturerTimeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LecturerService implements BaseCrudService<LecturerResponse, LecturerRequest, Integer> {

    private final LecturerRepository lecturerRepository;
    private final LecturerTimeNARepository timeRepository;
    private final ActivityConstraintRepository activityConstraintRepository;

    @Override
    public List<LecturerResponse> findAll() {
        return lecturerRepository.findAllByOrderByNikAsc().stream()
                .map(this::toResponse).toList();
    }

    @Override
    public LecturerResponse findById(Integer id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional
    public LecturerResponse create(LecturerRequest request) {
        if (lecturerRepository.existsByNik(request.getNik())) {
            throw new DuplicateResourceException("Lecturer", "nik", request.getNik());
        }
        Lecturer l = new Lecturer();
        apply(l, request);
        Lecturer saved = lecturerRepository.save(l);
        // TODO Phase 7: validateLockRepository.lock();
        return toResponse(saved);
    }

    @Override
    @Transactional
    public LecturerResponse update(Integer id, LecturerRequest request) {
        Lecturer l = getOrThrow(id);
        if (!l.getNik().equals(request.getNik()) && lecturerRepository.existsByNik(request.getNik())) {
            throw new DuplicateResourceException("Lecturer", "nik", request.getNik());
        }
        apply(l, request);
        Lecturer saved = lecturerRepository.save(l);
        // TODO Phase 7: validateLockRepository.lock();
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Lecturer l = getOrThrow(id);
        // Laravel LecturerController@destroy guard: block if lecturer has time entries.
        if (timeRepository.existsByLecturer_Id(id)) {
            throw new BadRequestException(
                "Cannot delete lecturer: remove their time entries first.");
        }
        // Phase 5: block deletion when an ActivityConstraint references this lecturer's nik.
        if (activityConstraintRepository.existsByTypeAndValue(ConstraintType.LECTURER, l.getNik())) {
            throw new BadRequestException(
                "Cannot delete lecturer: they are assigned to one or more activities.");
        }
        lecturerRepository.delete(l);
    }

    // ---- helpers -------------------------------------------------------------

    private void apply(Lecturer l, LecturerRequest request) {
        l.setNik(request.getNik());
        l.setName(request.getName());
        l.setHomeBase(request.getHomeBase());
        l.setAlias(request.getAlias());
    }

    private LecturerResponse toResponse(Lecturer l) {
        List<LecturerTimeResponse> na = timeRepository
                .findByLecturer_IdAndType(l.getId(), LecturerTimeType.NOT_AVAILABLE).stream()
                .map(LecturerTimeResponse::fromEntity).toList();
        List<LecturerTimeResponse> prio = timeRepository
                .findByLecturer_IdAndType(l.getId(), LecturerTimeType.PRIORITY).stream()
                .map(LecturerTimeResponse::fromEntity).toList();
        return LecturerResponse.fromEntity(l, na, prio);
    }

    private Lecturer getOrThrow(Integer id) {
        return lecturerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer", "id", id));
    }
}
