package com.timetablingapp.course;

import com.timetablingapp.activity.ActivityRepository;
import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.DuplicateResourceException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.jurusan.Jurusan;
import com.timetablingapp.jurusan.JurusanRepository;
import com.timetablingapp.jurusan.JurusanService;
import com.timetablingapp.schedule.validate.ValidateLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService implements BaseCrudService<CourseResponse, CourseRequest, Integer> {

    private final CourseRepository courseRepository;
    private final JurusanRepository jurusanRepository;
    private final JurusanService jurusanService;
    private final ActivityRepository activityRepository;
    private final ValidateLockService validateLockService;

    @Override
    public List<CourseResponse> findAll() {
        return courseRepository.findAll().stream()
                .map(CourseResponse::fromEntity)
                .toList();
    }

    /**
     * Find all courses filtered by the authenticated user's faculty.
     * Admin sees all courses, faculty user sees only courses in their faculty's jurusans.
     *
     * Mirrors Laravel: Course::whereIn("jurusan_id", Jurusan::jurusanIds())->with(['jurusan'])->get()
     */
    public List<CourseResponse> findAllByFaculty(String faculty) {
        List<Integer> jurusanIds = jurusanService.getJurusanIds(faculty);
        return courseRepository.findByJurusanIdIn(jurusanIds).stream()
                .map(CourseResponse::fromEntity)
                .toList();
    }

    @Override
    public CourseResponse findById(Integer id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        return CourseResponse.fromEntity(course);
    }

    @Override
    @Transactional
    public CourseResponse create(CourseRequest request) {
        // Check for duplicate course code
        if (courseRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Course", "code", request.getCode());
        }

        Jurusan jurusan = jurusanRepository.findById(request.getJurusanId())
                .orElseThrow(() -> new ResourceNotFoundException("Jurusan", "id", request.getJurusanId()));

        Course course = new Course();
        course.setCode(request.getCode());
        course.setName(request.getName());
        course.setType(request.getType());
        course.setTingkat(request.getTingkat());
        course.setKonsentrasi(request.getKonsentrasi());
        course.setJurusan(jurusan);

        Course saved = courseRepository.save(course);
        validateLockService.lock();
        return CourseResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public CourseResponse update(Integer id, CourseRequest request) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));

        // Check for duplicate code if changed
        if (!course.getCode().equals(request.getCode())
                && courseRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Course", "code", request.getCode());
        }

        Jurusan jurusan = jurusanRepository.findById(request.getJurusanId())
                .orElseThrow(() -> new ResourceNotFoundException("Jurusan", "id", request.getJurusanId()));

        course.setCode(request.getCode());
        course.setName(request.getName());
        course.setType(request.getType());
        course.setTingkat(request.getTingkat());
        course.setKonsentrasi(request.getKonsentrasi());
        course.setJurusan(jurusan);

        Course saved = courseRepository.save(course);
        validateLockService.lock();
        return CourseResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));

        // Phase 5: block deletion when any activity references this course code.
        if (activityRepository.existsByCourse_Code(course.getCode())) {
            throw new BadRequestException(
                "Cannot delete course: it is used by one or more activities.");
        }

        courseRepository.delete(course);
        validateLockService.lock();
    }

    /**
     * Find all courses belonging to a specific jurusan.
     * Mirrors Laravel: CourseController.getCourses($jurusanId)
     */
    public List<CourseResponse> findByJurusanId(Integer jurusanId) {
        // Verify jurusan exists
        jurusanRepository.findById(jurusanId)
                .orElseThrow(() -> new ResourceNotFoundException("Jurusan", "id", jurusanId));

        return courseRepository.findByJurusanId(jurusanId).stream()
                .map(CourseResponse::fromEntity)
                .toList();
    }

    /**
     * Get course info with jurusan details.
     * Mirrors Laravel: CourseController.getInfo($courseCode)
     *
     * NOTE: Activity summary will be added in Phase 5.
     */
    public CourseInfoResponse getCourseInfo(Integer id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));

        return CourseInfoResponse.fromEntity(course);
    }
}
