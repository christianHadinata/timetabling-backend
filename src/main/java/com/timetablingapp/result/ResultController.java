package com.timetablingapp.result;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService service;

    /** GET /api/results?semesterId= (all when omitted). */
    @GetMapping
    public ResponseEntity<List<ResultResponse>> getAll(
            @RequestParam(required = false) Integer semesterId) {
        return ResponseEntity.ok(service.findAll(semesterId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResultResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResultResponse> create(@Valid @RequestBody ResultRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResultResponse> update(
            @PathVariable Integer id, @Valid @RequestBody ResultRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Result deleted successfully"));
    }

    /**
     * DELETE /api/results/semester/{semesterId} — clear a whole semester's schedule.
     * Mirrors Laravel ResultController@destroy(semesterId).
     */
    @DeleteMapping("/semester/{semesterId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> clearSemester(@PathVariable Integer semesterId) {
        int n = service.deleteBySemester(semesterId);
        return ResponseEntity.ok(MessageResponse.success("Cleared " + n + " result(s) for semester " + semesterId));
    }

    // ---- Excel — DEFERRED TO PHASE 9 ----------------------------------------
    // GET  /api/results/export-siakad/{semesterId}
    // GET  /api/results/export-print/{semesterId}
    // POST /api/results/import
    @GetMapping({"/export-siakad/{semesterId}", "/export-print/{semesterId}"})
    public ResponseEntity<Void> exportStub(@PathVariable Integer semesterId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Excel export lands in Phase 9");
    }
}
