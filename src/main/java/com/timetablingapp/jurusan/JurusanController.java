package com.timetablingapp.jurusan;

import com.timetablingapp.common.dto.MessageResponse;
import com.timetablingapp.jurusan.konsentrasi.KonsentrasiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jurusans")
@RequiredArgsConstructor
public class JurusanController {

    private final JurusanService jurusanService;

    /**
     * GET /api/jurusans — List all jurusans (filtered by faculty for non-admin users)
     */
    @GetMapping
    public ResponseEntity<List<JurusanResponse>> getAll() {
        String faculty = getCurrentUserFaculty();
        return ResponseEntity.ok(jurusanService.findAllByFaculty(faculty));
    }

    /**
     * GET /api/jurusans/{id} — Get jurusan by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<JurusanResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(jurusanService.findById(id));
    }

    /**
     * POST /api/jurusans — Create jurusan (ADMIN only)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JurusanResponse> create(@Valid @RequestBody JurusanRequest request) {
        JurusanResponse response = jurusanService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /api/jurusans/{id} — Update jurusan (ADMIN only)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JurusanResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody JurusanRequest request) {
        return ResponseEntity.ok(jurusanService.update(id, request));
    }

    /**
     * DELETE /api/jurusans/{id} — Delete jurusan (ADMIN only)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        jurusanService.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Jurusan deleted successfully"));
    }

    /**
     * GET /api/jurusans/{jurusanId}/konsentrasi — Get concentrations for a jurusan
     */
    @GetMapping("/{jurusanId}/konsentrasi")
    public ResponseEntity<List<KonsentrasiResponse>> getKonsentrasi(
            @PathVariable Integer jurusanId) {
        return ResponseEntity.ok(jurusanService.getKonsentrasiByJurusanId(jurusanId));
    }

    /**
     * Extracts the faculty of the currently authenticated user from the JWT.
     * Returns null for admin users (faculty is null/empty).
     */
    private String getCurrentUserFaculty() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof String faculty) {
            return faculty;
        }
        return null;
    }
}
