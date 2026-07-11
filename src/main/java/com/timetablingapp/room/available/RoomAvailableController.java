package com.timetablingapp.room.available;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/room-availables")
@RequiredArgsConstructor
public class RoomAvailableController {

    private final RoomAvailableService service;

    /**
     * GET /api/room-availables            → all
     * GET /api/room-availables?roomId=5    → filtered by room
     */
    @GetMapping
    public ResponseEntity<List<RoomAvailableResponse>> getAll(
            @RequestParam(required = false) Integer roomId) {
        return ResponseEntity.ok(
                roomId != null ? service.findByRoomId(roomId) : service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomAvailableResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<RoomAvailableResponse> create(@Valid @RequestBody RoomAvailableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomAvailableResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody RoomAvailableRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Room availability deleted successfully"));
    }
}
