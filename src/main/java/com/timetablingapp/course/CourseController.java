package com.timetablingapp.course;

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
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final CourseExcelService courseExcelService;

    /**
     * GET /api/courses — List all courses (filtered by faculty)
     */
    @GetMapping
    public ResponseEntity<List<CourseResponse>> getAll() {
        String faculty = getCurrentUserFaculty();
        return ResponseEntity.ok(courseService.findAllByFaculty(faculty));
    }

    /**
     * GET /api/courses/{id} — Get course by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<CourseResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(courseService.findById(id));
    }

    /**
     * POST /api/courses — Create course (any authenticated user)
     */
    @PostMapping
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CourseRequest request) {
        CourseResponse response = courseService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /api/courses/{id} — Update course (any authenticated user)
     */
    @PutMapping("/{id}")
    public ResponseEntity<CourseResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.ok(courseService.update(id, request));
    }

    /**
     * DELETE /api/courses/{id} — Delete course (any authenticated user)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        courseService.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Course deleted successfully"));
    }

    /**
     * GET /api/courses/{id}/info — Get course info with jurusan details
     * Mirrors Laravel: CourseController.getInfo($courseCode)
     */
    @GetMapping("/{id}/info")
    public ResponseEntity<CourseInfoResponse> getInfo(@PathVariable Integer id) {
        return ResponseEntity.ok(courseService.getCourseInfo(id));
    }

    /**
     * GET /api/courses/by-jurusan/{id} — Get courses by jurusan ID
     * Mirrors Laravel: CourseController.getCourses($jurusanId)
     */
    @GetMapping("/by-jurusan/{id}")
    public ResponseEntity<List<CourseResponse>> getByJurusan(@PathVariable Integer id) {
        return ResponseEntity.ok(courseService.findByJurusanId(id));
    }

    /**
     * GET /api/courses/export — Download the course upload template (.xlsx).
     * Mirrors Laravel: CourseController.downloadExcel()
     */
    @GetMapping("/export")
    public ResponseEntity<Resource> export() {
        return courseExcelService.downloadTemplate();
    }

    /**
     * POST /api/courses/import — Bulk-create courses from a filled template.
     * Mirrors Laravel: CourseController.uploadExcel()
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportLog> importExcel(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(courseService.importCourses(file));
    }

    /**
     * Extracts the faculty of the currently authenticated user from the JWT.
     */
    private String getCurrentUserFaculty() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof String faculty) {
            return faculty;
        }
        return null;
    }
}
