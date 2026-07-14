package com.timetablingapp.schedule.slot.act;

import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.schedule.validate.ValidateLockService;
import com.timetablingapp.semester.Semester;
import com.timetablingapp.semester.SemesterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotActivityServiceTest {

    @Mock SlotValidationService slotValidationService;
    @Mock SlotActivityRepository slotActivityRepository;
    @Mock ValidateLockService validateLockService;
    @Mock SemesterRepository semesterRepository;
    @InjectMocks SlotActivityService service;

    private Semester currentSemester() {
        Semester s = new Semester();
        s.setId(5);
        s.setCurrent(true);
        return s;
    }

    @Test
    void revalidate_recomputesForCurrentSemesterAndOpensLock() {
        when(semesterRepository.findByCurrentTrue()).thenReturn(Optional.of(currentSemester()));
        when(slotValidationService.revalidate(5)).thenReturn(42);

        int written = service.revalidate();

        assertEquals(42, written);
        verify(slotValidationService).revalidate(5);
        verify(validateLockService).open();
    }

    @Test
    void revalidate_noCurrentSemester_throwsAndSkipsLock() {
        when(semesterRepository.findByCurrentTrue()).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> service.revalidate());
        verify(validateLockService, never()).open();
    }

    @Test
    void reset_deletesSlotActsForCurrentSemester() {
        when(semesterRepository.findByCurrentTrue()).thenReturn(Optional.of(currentSemester()));
        when(slotActivityRepository.deleteBySemesterId(5)).thenReturn(7);

        int deleted = service.reset();

        assertEquals(7, deleted);
        verify(slotActivityRepository).deleteBySemesterId(5);
    }

    @Test
    void isStale_delegatesToValidateLockService() {
        when(validateLockService.isStale()).thenReturn(true);
        assertTrue(service.isStale());

        when(validateLockService.isStale()).thenReturn(false);
        assertFalse(service.isStale());
    }
}
