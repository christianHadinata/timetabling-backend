package com.timetablingapp.lecturer;

import com.timetablingapp.common.dto.MessageResponse;
import com.timetablingapp.common.excel.ImportLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/lecturers")
@RequiredArgsConstructor
public class LecturerController {

    private final LecturerService service;
    private final LecturerExcelService lecturerExcelService;

    @GetMapping
    public ResponseEntity<List<LecturerResponse>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LecturerResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<LecturerResponse> create(@Valid @RequestBody LecturerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LecturerResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody LecturerRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Lecturer deleted successfully"));
    }

    /** GET /api/lecturers/export — download the lecturer upload template. */
    @GetMapping("/export")
    public ResponseEntity<Resource> export() {
        return lecturerExcelService.downloadTemplate();
    }

    /** GET /api/lecturers/export-time — download the lecturer-time upload template. */
    @GetMapping("/export-time")
    public ResponseEntity<Resource> exportTime() {
        return lecturerExcelService.downloadTimeTemplate();
    }

    /** POST /api/lecturers/import — bulk-create lecturers from a filled template. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportLog> importExcel(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.importLecturers(file, getCurrentUserFaculty()));
    }

    /** POST /api/lecturers/import-time — bulk-create lecturer time entries from a filled template. */
    @PostMapping(value = "/import-time", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportLog> importTime(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.importLecturerTimes(file));
    }

    private String getCurrentUserFaculty() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof String faculty) {
            return faculty;
        }
        return null;
    }
}
