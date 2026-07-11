package com.timetablingapp.common.base;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Abstract base REST controller providing standard CRUD endpoints.
 * <p>
 * Subclasses must:
 * <ul>
 *   <li>Add {@code @RestController} and {@code @RequestMapping} annotations</li>
 *   <li>Provide the concrete service via constructor injection</li>
 * </ul>
 *
 * @param <RES> Response DTO type
 * @param <REQ> Request DTO type
 * @param <ID>  Entity ID type
 */
public abstract class BaseCrudController<RES, REQ, ID> {

    protected final BaseCrudService<RES, REQ, ID> service;

    protected BaseCrudController(BaseCrudService<RES, REQ, ID> service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<RES>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RES> getById(@PathVariable ID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<RES> create(@Valid @RequestBody REQ request) {
        RES created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RES> update(
            @PathVariable ID id,
            @Valid @RequestBody REQ request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable ID id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Resource deleted successfully"));
    }
}
