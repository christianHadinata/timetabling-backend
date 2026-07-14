package com.timetablingapp.room;

import com.timetablingapp.common.dto.MessageResponse;
import com.timetablingapp.common.excel.ImportLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService service;
    private final RoomExcelService roomExcelService;

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Room deleted successfully"));
    }

    /** GET /api/rooms/export — download the room upload template. */
    @GetMapping("/export")
    public ResponseEntity<Resource> export() {
        return roomExcelService.downloadTemplate();
    }

    /** POST /api/rooms/import — bulk-create rooms from a filled template. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportLog> importExcel(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.importRooms(file));
    }
}
