package com.timetablingapp.setting;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService service;

    @GetMapping
    public ResponseEntity<List<SettingResponse>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    /** Detail with constraints grouped by type (expanded defaults). */
    @GetMapping("/{id}")
    public ResponseEntity<SettingDetailResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.findDetail(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SettingResponse> create(@Valid @RequestBody SettingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SettingResponse> update(
            @PathVariable Integer id, @Valid @RequestBody SettingRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Setting deleted successfully"));
    }
}
