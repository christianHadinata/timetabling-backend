package com.timetablingapp.schedule.slot.act;

import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.schedule.validate.ValidateLockService;
import com.timetablingapp.semester.Semester;
import com.timetablingapp.semester.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the slot-validation endpoints.
 * Mirrors Laravel SlotActivityController@revalidate / @resetAll.
 */
@Service
@RequiredArgsConstructor
public class SlotActivityService {

    private final SlotValidationService slotValidationService;
    private final SlotActivityRepository slotActivityRepository;
    private final ValidateLockService validateLockService;
    private final SemesterRepository semesterRepository;

    /** POST /revalidate — recompute all valid slot-activity pairs, then clear the dirty flag. */
    @Transactional
    public int revalidate() {
        Integer semesterId = currentSemester().getId();
        int written = slotValidationService.revalidate(semesterId);
        validateLockService.open();     // slot_acts now fresh
        return written;
    }

    /** POST /reset — hard-delete all slot_acts for the current semester. */
    @Transactional
    public int reset() {
        Integer semesterId = currentSemester().getId();
        return slotActivityRepository.deleteBySemesterId(semesterId);
    }

    /** GET /status — is slot_acts stale? */
    @Transactional(readOnly = true)
    public boolean isStale() {
        return validateLockService.isStale();
    }

    private Semester currentSemester() {
        return semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current semester is set"));
    }
}
