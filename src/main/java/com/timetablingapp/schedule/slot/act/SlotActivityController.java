package com.timetablingapp.schedule.slot.act;

import com.timetablingapp.common.dto.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Slot-validation endpoints (ADMIN only).
 * Laravel origin: SlotActivityController (GET /activity/revalidate, GET /activity/reset)
 * and ActivityController@index's "vlock" flag → GET /status.
 */
@RestController
@RequestMapping("/api/slot-activities")
@RequiredArgsConstructor
public class SlotActivityController {

    private final SlotActivityService service;

    /** POST /api/slot-activities/revalidate — recompute valid (activity, slot) pairs. */
    @PostMapping("/revalidate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> revalidate() {
        int written = service.revalidate();
        return ResponseEntity.ok(MessageResponse.success(
                "Revalidation complete — " + written + " slot-activity pairs written"));
    }

    /** POST /api/slot-activities/reset — clear slot_acts for the current semester. */
    @PostMapping("/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> reset() {
        int deleted = service.reset();
        return ResponseEntity.ok(MessageResponse.success(
                "Reset complete — " + deleted + " slot-activity rows removed"));
    }

    /** GET /api/slot-activities/status — { stale: true|false } (was Laravel "vlock"). */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok(Map.of("stale", service.isStale()));
    }
}
