package com.timetablingapp.schedule.algorithm;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.timetablingapp.common.dto.MessageResponse;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.schedule.algorithm.dto.*;
import com.timetablingapp.semester.SemesterRepository;

@RestController
@RequestMapping("/api/timetable")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;
    private final SemesterRepository semesterRepository;

    /** POST /api/timetable/generate — kick off GA. Returns { jobId }. Add ?wait=true for sync. */
    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenerateResponse> generate(@RequestBody GenerateRequest req,
                                                     @RequestParam(defaultValue = "false") boolean wait) {
        if (wait) return ResponseEntity.ok(timetableService.generateSync(req, null));
        return ResponseEntity.ok(GenerateResponse.job(timetableService.startGenerate(req)));
    }

    /** POST /api/timetable/save — persist inserted + notInserted, then revalidate. */
    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> save(@RequestBody SaveTimetableRequest req) {
        timetableService.save(req);
        return ResponseEntity.ok(MessageResponse.success("Timetable saved"));
    }

    /** GET /api/timetable/init-schedule — current semester's placed/unplaced (ADMIN). */
    @GetMapping("/init-schedule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScheduleDataResponse> initSchedule() {
        Integer sid = semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current semester is set"))
                .getId();
        return ResponseEntity.ok(timetableService.prepareScheduleData(sid));
    }

    /** GET /api/timetable/schedule/{semesterId} — view any semester's schedule (auth). */
    @GetMapping("/schedule/{semesterId}")
    public ResponseEntity<ScheduleDataResponse> schedule(@PathVariable Integer semesterId) {
        return ResponseEntity.ok(timetableService.prepareScheduleData(semesterId));
    }

    /** GET /api/timetable/data — activities+rooms maps for the display grid (auth). */
    @GetMapping("/data")
    public ResponseEntity<TimetableDataResponse> data() {
        return ResponseEntity.ok(timetableService.getData());
    }
}
