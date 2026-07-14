package com.timetablingapp.result;

import com.timetablingapp.common.dto.MessageResponse;
import com.timetablingapp.common.excel.ImportLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService service;
    private final ResultExcelService resultExcelService;

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

    // ---- Excel ---------------------------------------------------------------

    /** GET /api/results/export-siakad/{semesterId} — SIAKAD + "Not Inserted" workbook. */
    @GetMapping("/export-siakad/{semesterId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> exportSiakad(@PathVariable Integer semesterId) {
        return resultExcelService.exportSiakad(semesterId, currentFaculty());
    }

    /** GET /api/results/export-print/{semesterId} — printable per-day room grid. */
    @GetMapping("/export-print/{semesterId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> exportPrint(@PathVariable Integer semesterId) {
        return resultExcelService.exportPrint(semesterId, currentFaculty());
    }

    /** POST /api/results/import — upsert results from an uploaded SIAKAD-format workbook. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImportLog> importExcel(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.importResults(file));
    }

    private String currentFaculty() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof String faculty) {
            return faculty;
        }
        return null;
    }
}
