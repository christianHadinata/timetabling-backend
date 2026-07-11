package com.timetablingapp.activity;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /** GET /api/activities?semesterId=  (defaults to current semester, faculty-scoped) */
    @GetMapping
    public ResponseEntity<List<ActivityResponse>> getAll(
            @RequestParam(required = false) Integer semesterId) {
        return ResponseEntity.ok(
                activityService.findAllByFacultyAndSemester(getCurrentUserFaculty(), semesterId));
    }

    /** GET /api/activities/semester/{semesterId} — mirrors legacy /activities/sems/{sems} */
    @GetMapping("/semester/{semesterId}")
    public ResponseEntity<List<ActivityResponse>> getBySemester(@PathVariable Integer semesterId) {
        return ResponseEntity.ok(
                activityService.findAllByFacultyAndSemester(getCurrentUserFaculty(), semesterId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivityResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(activityService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ActivityResponse> create(@Valid @RequestBody ActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activityService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ActivityResponse> update(
            @PathVariable Integer id, @Valid @RequestBody ActivityRequest request) {
        return ResponseEntity.ok(activityService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        activityService.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Activity deleted successfully"));
    }

    /** GET /api/activities/{id}/paralel-candidates — same tingkat + jurusan + semester */
    @GetMapping("/{id}/paralel-candidates")
    public ResponseEntity<List<ActivityResponse.ParalelDto>> paralelCandidates(@PathVariable Integer id) {
        return ResponseEntity.ok(activityService.getParalelCandidates(id));
    }

    // NOTE: Excel import/export endpoints (/export, /export-all, /import) are added in Phase 9.

    private String getCurrentUserFaculty() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof String faculty) {
            return faculty;
        }
        return null;
    }
}
