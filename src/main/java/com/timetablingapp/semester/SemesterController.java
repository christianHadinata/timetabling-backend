package com.timetablingapp.semester;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/semesters")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService semesterService;

    /**
     * GET /api/semesters — List all semesters (any authenticated user)
     */
    @GetMapping
    public ResponseEntity<List<SemesterResponse>> getAll() {
        return ResponseEntity.ok(semesterService.findAll());
    }

    /**
     * GET /api/semesters/{id} — Get semester by ID (any authenticated user)
     */
    @GetMapping("/{id}")
    public ResponseEntity<SemesterResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(semesterService.findById(id));
    }

    /**
     * POST /api/semesters — Create semester (ADMIN only)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SemesterResponse> create(@Valid @RequestBody SemesterRequest request) {
        SemesterResponse response = semesterService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /api/semesters/{id} — Update semester (ADMIN only)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SemesterResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody SemesterRequest request) {
        return ResponseEntity.ok(semesterService.update(id, request));
    }

    /**
     * DELETE /api/semesters/{id} — Delete semester (ADMIN only)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        semesterService.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Semester deleted successfully"));
    }

    /**
     * POST /api/semesters/next — Create next semester (ADMIN only)
     * Automatically determines the next semester type and academic year.
     */
    @PostMapping("/next")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SemesterResponse> next() {
        SemesterResponse response = semesterService.next();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/semesters/duplicate — Duplicate semester data (ADMIN only)
     * Copies activities, constraints, and settings from a source semester
     * to the current semester.
     */
    @PostMapping("/duplicate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SemesterResponse> duplicate(
            @RequestBody SetCurrentSemesterRequest request) {
        SemesterResponse response = semesterService.duplicate(request.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/semesters/current — Set current semester (any authenticated user)
     * Unsets the previous current semester and sets the new one.
     */
    @PutMapping("/current")
    public ResponseEntity<SemesterResponse> setCurrent(
            @Valid @RequestBody SetCurrentSemesterRequest request) {
        return ResponseEntity.ok(semesterService.setCurrent(request.getId()));
    }

    /**
     * DELETE /api/semesters/current — Remove current semester data (ADMIN only)
     * Removes all activities, constraints, settings, and results for the current semester.
     */
    @DeleteMapping("/current")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> removeCurrentSemesterData() {
        semesterService.removeCurrentSemesterData();
        return ResponseEntity.ok(
                MessageResponse.success("Current semester data removed successfully"));
    }
}
