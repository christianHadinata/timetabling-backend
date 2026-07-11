package com.timetablingapp.semester;

import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemesterService implements BaseCrudService<SemesterResponse, SemesterRequest, Integer> {

    private final SemesterRepository semesterRepository;

    // ──────────────────────────────────────
    // Standard CRUD Operations
    // ──────────────────────────────────────

    @Override
    public List<SemesterResponse> findAll() {
        return semesterRepository.findAllByOrderByIdDesc().stream()
                .map(SemesterResponse::fromEntity)
                .toList();
    }

    @Override
    public SemesterResponse findById(Integer id) {
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", id));
        return SemesterResponse.fromEntity(semester);
    }

    @Override
    @Transactional
    public SemesterResponse create(SemesterRequest request) {
        Semester semester = new Semester();
        semester.setType(request.getType());
        semester.setAcademicYear(request.getAcademicYear());
        semester.setCurrent(request.getCurrent());

        Semester saved = semesterRepository.save(semester);
        return SemesterResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public SemesterResponse update(Integer id, SemesterRequest request) {
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", id));

        semester.setType(request.getType());
        semester.setAcademicYear(request.getAcademicYear());
        semester.setCurrent(request.getCurrent());

        Semester saved = semesterRepository.save(semester);
        return SemesterResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", id));
        semesterRepository.delete(semester);
    }

    // ──────────────────────────────────────
    // Special Operations
    // ──────────────────────────────────────

    /**
     * Set the current semester.
     * 1. Unset all semesters as current
     * 2. Set the specified semester as current
     *
     * Mirrors Laravel: SemesterRepository.setCurrent($id)
     */
    @Transactional
    public SemesterResponse setCurrent(Integer id) {
        // Verify semester exists
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", id));

        // 1. Unset all current flags
        semesterRepository.unsetAllCurrent();

        // 2. Set the new current
        semesterRepository.setCurrentById(id);

        // Refresh the entity to get updated state
        semester.setCurrent(true);
        return SemesterResponse.fromEntity(semester);
    }

    /**
     * Create the next semester based on the latest semester.
     *
     * Semester rotation logic (from Laravel SemesterController.next()):
     * - Pendek → Ganjil (new academic year: year+1/year+2)
     * - Ganjil → Genap (same academic year)
     * - Genap  → Pendek (same academic year)
     *
     * @throws BadRequestException if the latest semester is not set as current
     */
    @Transactional
    public SemesterResponse next() {
        Semester last = semesterRepository.findFirstByOrderByIdDesc()
                .orElseThrow(() -> new BadRequestException("No semesters exist yet"));

        if (!last.getCurrent()) {
            throw new BadRequestException(
                    "Please set the latest semester as current before creating the next one");
        }

        String newType;
        String newAcademicYear;

        switch (last.getType()) {
            case "Pendek" -> {
                // Pendek → Ganjil with new academic year
                String[] split = last.getAcademicYear().split("/");
                int year1 = Integer.parseInt(split[0]) + 1;
                int year2 = Integer.parseInt(split[1]) + 1;
                newAcademicYear = year1 + "/" + year2;
                newType = "Ganjil";
            }
            case "Ganjil" -> {
                // Ganjil → Genap, same academic year
                newAcademicYear = last.getAcademicYear();
                newType = "Genap";
            }
            case "Genap" -> {
                // Genap → Pendek, same academic year
                newAcademicYear = last.getAcademicYear();
                newType = "Pendek";
            }
            default -> throw new BadRequestException(
                    "Unknown semester type: " + last.getType());
        }

        Semester newSemester = new Semester();
        newSemester.setAcademicYear(newAcademicYear);
        newSemester.setType(newType);
        newSemester.setCurrent(false); // Not set as current by default

        Semester saved = semesterRepository.save(newSemester);
        return SemesterResponse.fromEntity(saved);
    }

    /**
     * Duplicate activities, constraints, and settings from a source semester
     * to the current semester.
     *
     * Mirrors Laravel: SemesterController.duplicate()
     *
     * NOTE: The full duplication logic requires Activity, ActivityConstraint,
     * Setting, and SettingConstraint repositories which are implemented in
     * later phases (Phase 5 and Phase 6). For now, this method accepts
     * the source semester ID and will be completed when those dependencies
     * are available.
     *
     * @param sourceSemesterId the semester to copy from
     * @throws BadRequestException if no current semester is set
     */
    @Transactional
    public SemesterResponse duplicate(Integer sourceSemesterId) {
        // Verify source semester exists
        semesterRepository.findById(sourceSemesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", sourceSemesterId));

        // Get current semester
        Semester currentSemester = semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current semester is set"));

        // TODO: Phase 5 & 6 — Copy activities, activity constraints,
        // settings, and setting constraints from sourceSemesterId
        // to currentSemester.getId()
        //
        // The Laravel implementation (SemesterController.duplicate) does:
        // 1. Copy all activities from source semester → current semester
        // 2. Copy all activity constraints for each copied activity
        // 3. Copy all settings from source semester → current semester
        // 4. Copy all setting constraints for each copied setting
        // 5. Lock validation (vlock.lock())

        log.warn("Semester duplicate: Activity/Setting duplication is deferred to Phase 5/6. " +
                "Source semester {} → Current semester {}", sourceSemesterId, currentSemester.getId());

        return SemesterResponse.fromEntity(currentSemester);
    }

    /**
     * Remove all data associated with the current semester.
     *
     * Mirrors Laravel: SemesterController.removeCurSem()
     *
     * NOTE: Similar to duplicate(), this requires dependencies from later phases.
     * Will be completed in Phase 5/6.
     *
     * @throws BadRequestException if no current semester is set
     */
    @Transactional
    public void removeCurrentSemesterData() {
        Semester currentSemester = semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current semester is set"));

        // TODO: Phase 5 & 6 — Delete all results, activity constraints,
        // activities, setting constraints, and settings for the current semester.
        //
        // The Laravel implementation (SemesterController.removeCurSem) does:
        // 1. Delete results for current semester
        // 2. Delete activity constraints for each activity in current semester
        // 3. Delete activities for current semester
        // 4. Delete setting constraints for each setting in current semester
        // 5. Delete settings for current semester

        log.warn("Remove current semester data: Deferred to Phase 5/6. " +
                "Current semester ID: {}", currentSemester.getId());
    }
}
