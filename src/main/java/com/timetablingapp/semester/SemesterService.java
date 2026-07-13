package com.timetablingapp.semester;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.activity.ActivityRepository;
import com.timetablingapp.activity.constraint.ActivityConstraint;
import com.timetablingapp.activity.constraint.ActivityConstraintRepository;
import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.result.ResultRepository;
import com.timetablingapp.schedule.slot.act.SlotActivityRepository;
import com.timetablingapp.schedule.validate.ValidateLockService;
import com.timetablingapp.setting.Setting;
import com.timetablingapp.setting.SettingRepository;
import com.timetablingapp.setting.constraint.SettingConstraint;
import com.timetablingapp.setting.constraint.SettingConstraintRepository;
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
    private final ActivityRepository activityRepository;
    private final ActivityConstraintRepository activityConstraintRepository;
    private final SettingRepository settingRepository;
    private final SettingConstraintRepository settingConstraintRepository;
    private final ResultRepository resultRepository;
    private final SlotActivityRepository slotActivityRepository;
    private final ValidateLockService validateLockService;

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
        validateLockService.lock();
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

        // 1 & 2. Copy activities from source semester → current semester, with their constraints.
        for (Activity old : activityRepository.findBySemester_Id(sourceSemesterId)) {
            Activity copy = new Activity();
            copy.setSemester(currentSemester);
            copy.setCourse(old.getCourse());
            copy.setCourseClass(old.getCourseClass());
            copy.setCourseSession(old.getCourseSession());
            copy.setDuration(old.getDuration());
            copy.setQuota(old.getQuota());
            copy.setActivityType(old.getActivityType());
            Activity savedActivity = activityRepository.save(copy);

            for (ActivityConstraint oc : activityConstraintRepository.findByActivity_Id(old.getId())) {
                ActivityConstraint nc = new ActivityConstraint();
                nc.setActivity(savedActivity);
                nc.setType(oc.getType());
                nc.setValue(oc.getValue());
                activityConstraintRepository.save(nc);
            }
        }

        // 3 & 4. Copy settings from source semester → current semester, with their constraints.
        for (Setting old : settingRepository.findBySemester_Id(sourceSemesterId)) {
            Setting copy = new Setting();
            copy.setSemester(currentSemester);
            copy.setName(old.getName());
            Setting savedSetting = settingRepository.save(copy);

            for (SettingConstraint oc : settingConstraintRepository.findBySetting_Id(old.getId())) {
                SettingConstraint nc = new SettingConstraint();
                nc.setSetting(savedSetting);
                nc.setType(oc.getType());
                nc.setValue(oc.getValue());
                settingConstraintRepository.save(nc);
            }
        }

        validateLockService.lock();
        log.info("Semester duplicate: copied activities/settings from semester {} to current semester {}",
                sourceSemesterId, currentSemester.getId());

        return SemesterResponse.fromEntity(currentSemester);
    }

    /**
     * Remove all data associated with the current semester.
     *
     * Mirrors Laravel: SemesterController.removeCurSem()
     *
     * @throws BadRequestException if no current semester is set
     */
    @Transactional
    public void removeCurrentSemesterData() {
        Semester currentSemester = semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current semester is set"));
        Integer semId = currentSemester.getId();

        // 1. Results
        resultRepository.deleteBySemester_Id(semId);

        // 2. Slot-acts, then activity constraints, then activities
        slotActivityRepository.deleteBySemesterId(semId);   // hard-delete, before activities
        List<Activity> activities = activityRepository.findBySemester_Id(semId);
        for (Activity a : activities) {
            activityConstraintRepository.deleteByActivity_Id(a.getId());
            // NOTE: activity_paralels / activity_gaps are cleared via ActivityService.delete
            // when activities are removed individually; bulk removal here mirrors Laravel.
        }
        for (Activity a : activities) {
            activityRepository.delete(a);
        }

        // 3. Setting constraints, then settings
        List<Setting> settings = settingRepository.findBySemester_Id(semId);
        for (Setting s : settings) {
            settingConstraintRepository.deleteBySetting_Id(s.getId());
        }
        for (Setting s : settings) {
            settingRepository.delete(s);
        }

        validateLockService.lock();
        log.info("Remove current semester data: cleared results/activities/settings for semester {}", semId);
    }
}
