package com.timetablingapp.lecturer;

import com.timetablingapp.activity.constraint.ActivityConstraintRepository;
import com.timetablingapp.activity.constraint.ConstraintType;
import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.excel.ImportLog;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.DuplicateResourceException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.jurusan.Jurusan;
import com.timetablingapp.jurusan.JurusanRepository;
import com.timetablingapp.lecturer.time.LecturerTimeNA;
import com.timetablingapp.lecturer.time.LecturerTimeNARepository;
import com.timetablingapp.lecturer.time.LecturerTimeResponse;
import com.timetablingapp.lecturer.time.LecturerTimeType;
import com.timetablingapp.schedule.validate.ValidateLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LecturerService implements BaseCrudService<LecturerResponse, LecturerRequest, Integer> {

    private final LecturerRepository lecturerRepository;
    private final LecturerTimeNARepository timeRepository;
    private final ActivityConstraintRepository activityConstraintRepository;
    private final ValidateLockService validateLockService;
    private final LecturerExcelService lecturerExcelService;
    private final JurusanRepository jurusanRepository;

    @Value("${app.import.default-home-base:1}")
    private Integer defaultHomeBase;

    @Override
    public List<LecturerResponse> findAll() {
        return lecturerRepository.findAllByOrderByNikAsc().stream()
                .map(this::toResponse).toList();
    }

    @Override
    public LecturerResponse findById(Integer id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional
    public LecturerResponse create(LecturerRequest request) {
        if (lecturerRepository.existsByNik(request.getNik())) {
            throw new DuplicateResourceException("Lecturer", "nik", request.getNik());
        }
        Lecturer l = new Lecturer();
        apply(l, request);
        Lecturer saved = lecturerRepository.save(l);
        validateLockService.lock();
        return toResponse(saved);
    }

    @Override
    @Transactional
    public LecturerResponse update(Integer id, LecturerRequest request) {
        Lecturer l = getOrThrow(id);
        if (!l.getNik().equals(request.getNik()) && lecturerRepository.existsByNik(request.getNik())) {
            throw new DuplicateResourceException("Lecturer", "nik", request.getNik());
        }
        apply(l, request);
        Lecturer saved = lecturerRepository.save(l);
        validateLockService.lock();
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Lecturer l = getOrThrow(id);
        // Laravel LecturerController@destroy guard: block if lecturer has time entries.
        if (timeRepository.existsByLecturer_Id(id)) {
            throw new BadRequestException(
                "Cannot delete lecturer: remove their time entries first.");
        }
        // Phase 5: block deletion when an ActivityConstraint references this lecturer's nik.
        if (activityConstraintRepository.existsByTypeAndValue(ConstraintType.LECTURER, l.getNik())) {
            throw new BadRequestException(
                "Cannot delete lecturer: they are assigned to one or more activities.");
        }
        lecturerRepository.delete(l);
        validateLockService.lock();
    }

    // ---- Excel import --------------------------------------------------------

    /**
     * Import lecturers. Skips existing NIKs; sets home_base via {@link #resolveHomeBase}.
     * Mirrors LecturerController@uploadExcel.
     */
    public ImportLog importLecturers(MultipartFile file, String faculty) {
        ImportLog log = new ImportLog("lecturer");
        for (LecturerExcelService.LecturerRow row : lecturerExcelService.parseLecturers(file)) {
            String nik = row.nik() == null ? "" : row.nik().trim();
            String id = nik + "-" + row.name();
            if (nik.isBlank()) continue;
            if (lecturerRepository.existsByNik(nik)) {
                log.fail(id, "Ditemukan duplikat NIK pada database.");
                continue;
            }
            try {
                Lecturer l = new Lecturer();
                l.setNik(nik);
                l.setName(row.name());
                // home_base: legacy rule unknown (Laravel never set it — not fillable).
                // Routed through resolveHomeBase() so it's a one-line patch later. See phase9.md D2.
                l.setHomeBase(resolveHomeBase(faculty));
                lecturerRepository.save(l);
                log.ok(id);
            } catch (Exception e) {
                log.fail(id, "Exception: " + e.getMessage());
            }
        }
        validateLockService.lock();
        return log;
    }

    /**
     * Import lecturer availability/priority times. Resolves lecturer by NIK, dedupes on
     * (lecturer, day, type, start, end). Mirrors LecturerController@uploadExcelTime.
     */
    public ImportLog importLecturerTimes(MultipartFile file) {
        ImportLog log = new ImportLog("lecturer-time");
        Map<String, Lecturer> byNik = lecturerRepository.findAll().stream()
                .collect(Collectors.toMap(Lecturer::getNik, l -> l, (a, b) -> a));

        for (LecturerExcelService.TimeRow row : lecturerExcelService.parseTimes(file)) {
            Lecturer lecturer = byNik.get(row.nik());
            if (lecturer == null) {
                log.fail(row.nik(), "NIK tidak ditemukan.");
                continue;
            }
            boolean exists = timeRepository.existsByLecturer_IdAndDayAndTypeAndStartTimeAndEndTime(
                    lecturer.getId(), row.day(), row.type(), row.start(), row.end());
            if (exists) continue;
            try {
                LecturerTimeNA t = new LecturerTimeNA();
                t.setLecturer(lecturer);
                t.setDay(row.day());
                t.setType(row.type());
                t.setStartTime(row.start());
                t.setEndTime(row.end());
                timeRepository.save(t);
                log.ok(row.nik());
            } catch (Exception e) {
                log.fail(row.nik(), "Exception: " + e.getMessage());
            }
        }
        validateLockService.lock();
        return log;
    }

    // ---- home_base: the ONE place the guess lives (patch here when the rule is known) ----

    /**
     * Resolve a lecturer's home_base on import.
     * LEGACY UNKNOWN: Laravel never wrote this column (absent from $fillable and the sheet).
     * Strategy: the importing user's faculty jurusan id, else the configured default
     * (which the seeder proves exists, so the FK to jurusans stays valid).
     * When the real rule surfaces, change ONLY this method (or app.import.default-home-base).
     */
    private Integer resolveHomeBase(String faculty) {
        if (faculty != null && !faculty.isBlank()) {
            return jurusanRepository.findByFaculty(faculty).stream()
                    .findFirst().map(Jurusan::getId).orElse(defaultHomeBase);
        }
        return defaultHomeBase;
    }

    // ---- helpers -------------------------------------------------------------

    private void apply(Lecturer l, LecturerRequest request) {
        l.setNik(request.getNik());
        l.setName(request.getName());
        l.setHomeBase(request.getHomeBase());
        l.setAlias(request.getAlias());
    }

    private LecturerResponse toResponse(Lecturer l) {
        List<LecturerTimeResponse> na = timeRepository
                .findByLecturer_IdAndType(l.getId(), LecturerTimeType.NOT_AVAILABLE).stream()
                .map(LecturerTimeResponse::fromEntity).toList();
        List<LecturerTimeResponse> prio = timeRepository
                .findByLecturer_IdAndType(l.getId(), LecturerTimeType.PRIORITY).stream()
                .map(LecturerTimeResponse::fromEntity).toList();
        return LecturerResponse.fromEntity(l, na, prio);
    }

    private Lecturer getOrThrow(Integer id) {
        return lecturerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer", "id", id));
    }
}
