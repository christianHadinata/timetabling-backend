package com.timetablingapp.lecturer.time;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lecturer-times")
@RequiredArgsConstructor
public class LecturerTimeNAController {

    private final LecturerTimeNAService service;

    /** GET /api/lecturer-times?lecturerId=3 (or all if omitted) */
    @GetMapping
    public ResponseEntity<List<LecturerTimeResponse>> getAll(
            @RequestParam(required = false) Integer lecturerId) {
        return ResponseEntity.ok(
                lecturerId != null ? service.findByLecturerId(lecturerId) : service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LecturerTimeResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<LecturerTimeResponse> create(@Valid @RequestBody LecturerTimeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LecturerTimeResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody LecturerTimeRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Lecturer time deleted successfully"));
    }
}
