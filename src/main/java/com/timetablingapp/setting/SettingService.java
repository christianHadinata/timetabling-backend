package com.timetablingapp.setting;

import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.semester.Semester;
import com.timetablingapp.semester.SemesterRepository;
import com.timetablingapp.setting.constraint.SettingConstraint;
import com.timetablingapp.setting.constraint.SettingConstraintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettingService implements BaseCrudService<SettingResponse, SettingRequest, Integer> {

    private final SettingRepository settingRepository;
    private final SettingConstraintRepository constraintRepository;
    private final SemesterRepository semesterRepository;
    private final SettingDefaultsProvider defaults;

    // ---- reads ---------------------------------------------------------------

    @Override
    public List<SettingResponse> findAll() {
        return settingRepository.findAll().stream().map(SettingResponse::fromEntity).toList();
    }

    /** Detail with constraints expanded (Setting::getByType). */
    public SettingDetailResponse findDetail(Integer id) {
        Setting s = getOrThrow(id);

        // group stored rows by type
        Map<SettingableType, List<String>> stored = constraintRepository.findBySetting_Id(id).stream()
                .collect(Collectors.groupingBy(
                        SettingConstraint::getType,
                        Collectors.mapping(SettingConstraint::getValue, Collectors.toList())));

        // for every type: stored rows OR full defaults when none stored
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (SettingableType t : SettingableType.values()) {
            List<String> vals = stored.getOrDefault(t, defaults.defaultsFor(t));
            List<String> sorted = new ArrayList<>(vals);
            Collections.sort(sorted);   // mirrors Laravel sort()
            out.put(t.getDbValue(), sorted);
        }

        var sem = s.getSemester();
        return SettingDetailResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .semesterId(sem != null ? sem.getId() : null)
                .typeAndSemester(sem != null ? sem.getType() + " " + sem.getAcademicYear() : null)
                .constraints(out)
                .build();
    }

    /** BaseCrudService.findById returns the light list row; use findDetail for constraints. */
    @Override
    public SettingResponse findById(Integer id) {
        return SettingResponse.fromEntity(getOrThrow(id));
    }

    // ---- writes --------------------------------------------------------------

    @Override
    @Transactional
    public SettingResponse create(SettingRequest request) {
        Semester semester = (request.getSemesterId() != null)
                ? semesterRepository.findById(request.getSemesterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", request.getSemesterId()))
                : currentSemester();

        Setting s = new Setting();
        s.setName(request.getName());
        s.setSemester(semester);
        Setting saved = settingRepository.save(s);

        writeConstraints(saved, request);
        return SettingResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public SettingResponse update(Integer id, SettingRequest request) {
        Setting s = getOrThrow(id);
        s.setName(request.getName());               // semester not changed on update (matches legacy)
        settingRepository.save(s);

        constraintRepository.deleteBySetting_Id(id); // hard delete then re-insert
        writeConstraints(s, request);
        return SettingResponse.fromEntity(s);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Setting s = getOrThrow(id);
        constraintRepository.deleteBySetting_Id(id); // remove constraints first (Laravel destroy)
        settingRepository.delete(s);                 // soft delete setting
    }

    // ---- helpers -------------------------------------------------------------

    /**
     * Insert one row per selected value, skipping types marked "select all".
     * Mirrors SettingController@store/update foreach(Setting::FIELD).
     */
    private void writeConstraints(Setting setting, SettingRequest request) {
        Set<String> selectAll = (request.getSelectAll() != null)
                ? new HashSet<>(request.getSelectAll()) : Set.of();
        Map<String, List<String>> constraints = (request.getConstraints() != null)
                ? request.getConstraints() : Map.of();

        for (SettingableType type : SettingableType.values()) {
            String key = type.getDbValue();
            if (selectAll.contains(key)) continue;               // "all" -> store nothing

            List<String> values = constraints.getOrDefault(key, List.of());
            // Laravel optimization: if a field's selection equals the full set, store nothing.
            if (values.size() == defaults.defaultsFor(type).size()) continue;

            for (String v : values) {
                if (v == null || v.isBlank()) continue;
                SettingConstraint c = new SettingConstraint();
                c.setSetting(setting);
                c.setType(type);
                c.setValue(v.trim());
                constraintRepository.save(c);
            }
        }
    }

    private Semester currentSemester() {
        return semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current semester is set"));
    }

    private Setting getOrThrow(Integer id) {
        return settingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Setting", "id", id));
    }
}
