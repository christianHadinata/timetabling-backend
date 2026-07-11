package com.timetablingapp.activity.constraint;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activity-constraints")
@RequiredArgsConstructor
public class ActivityConstraintController {

    private final ActivityConstraintService service;

    /** GET /api/activity-constraints?activityId=42 */
    @GetMapping
    public ResponseEntity<List<ActivityConstraintResponse>> getByActivity(
            @RequestParam Integer activityId) {
        return ResponseEntity.ok(service.findByActivityId(activityId));
    }

    @PostMapping
    public ResponseEntity<ActivityConstraintResponse> create(
            @Valid @RequestBody ActivityConstraintRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Activity constraint deleted successfully"));
    }
}
