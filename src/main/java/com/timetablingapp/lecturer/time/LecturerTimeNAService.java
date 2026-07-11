package com.timetablingapp.lecturer.time;

import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.lecturer.Lecturer;
import com.timetablingapp.lecturer.LecturerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LecturerTimeNAService
        implements BaseCrudService<LecturerTimeResponse, LecturerTimeRequest, Integer> {

    private final LecturerTimeNARepository repository;
    private final LecturerRepository lecturerRepository;

    public List<LecturerTimeResponse> findByLecturerId(Integer lecturerId) {
        return repository.findByLecturer_IdOrderByDayAsc(lecturerId).stream()
                .map(LecturerTimeResponse::fromEntity).toList();
    }

    @Override
    public List<LecturerTimeResponse> findAll() {
        return repository.findAll().stream().map(LecturerTimeResponse::fromEntity).toList();
    }

    @Override
    public LecturerTimeResponse findById(Integer id) {
        return LecturerTimeResponse.fromEntity(getOrThrow(id));
    }

    @Override
    @Transactional
    public LecturerTimeResponse create(LecturerTimeRequest request) {
        LecturerTimeNA e = new LecturerTimeNA();
        apply(e, request);
        return LecturerTimeResponse.fromEntity(repository.save(e));
    }

    @Override
    @Transactional
    public LecturerTimeResponse update(Integer id, LecturerTimeRequest request) {
        LecturerTimeNA e = getOrThrow(id);
        apply(e, request);
        return LecturerTimeResponse.fromEntity(repository.save(e));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        repository.delete(getOrThrow(id));
    }

    private void apply(LecturerTimeNA e, LecturerTimeRequest request) {
        Lecturer lecturer = lecturerRepository.findById(request.getLecturerId())
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer", "id", request.getLecturerId()));
        e.setLecturer(lecturer);
        e.setDay(request.getDay());
        e.setStartTime(request.getStartTime());
        e.setEndTime(request.getEndTime());
        e.setType(request.getType());
    }

    private LecturerTimeNA getOrThrow(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LecturerTimeNA", "id", id));
    }
}
