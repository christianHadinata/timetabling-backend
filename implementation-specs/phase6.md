# Phase 6 — Implementation Plan: Results & Settings (`result/` and `setting/`)

> **Document Version:** 1.0
> **Date:** 2026-07-13
> **Depends on:** Phase 2 (`semester/`), Phase 4 (`room/`, `room/type/`), Phase 5 (`activity/`, `activity/type/`), Phase 3 (`jurusan/`)
> **Roadmap reference:** [migration-roadmap.md → Phase 6](migration-roadmap.md)
> **Legacy source (read-only):** `timetabling_laravel/`

---

## 1. Goal & Scope

Port two Laravel domains into the Spring Boot backend:

1. **`result/`** — persistence of scheduling results (activity → room/day/time assignments), with semester filtering and a `valid` flag. This is what the genetic algorithm (Phase 8) writes and what the timetable view reads.
2. **`setting/`** — algorithm "setting profiles": a named subset of rooms, room types, activity types, activities, days (`hari`), hours (`waktu`), and departments (`jurusan`) that constrains a generation run. Constraints are stored in `setting_constraints`, grouped by `settingable_type`.

**In scope (Phase 6):**
- Full CRUD for `Result` and `Setting` (JSON REST, matching the conventions established in Phases 2–5).
- `SettingConstraint` create/replace logic grouped by type.
- `SettingDetailResponse` returning constraints as an expanded `Map<type, List<value>>` (with "not stored ⇒ all selected" default expansion — mirrors `Setting::getByType()`).
- Bulk "clear results for a semester" endpoint (mirrors `ResultController@destroy`).
- **Wire up the deferred cross-cutting TODOs from earlier phases** that depend on `result/` + `setting/`:
  - `SemesterService.duplicate()` — copy activities + activity constraints + settings + setting constraints from source semester to current.
  - `SemesterService.removeCurrentSemesterData()` — delete results, activities (+constraints), settings (+constraints) for the current semester.
  - `ActivityService.delete()` — guard against deleting an activity that a scheduled `Result` uses, and cascade-delete its results.

**Out of scope (deferred):**
- **Excel import/export** for results (`export-siakad`, `download-print`, `uploadExcelResult`) → **Phase 9**. Controller methods are stubbed with `501 Not Implemented` placeholders and a `TODO Phase 9` marker.
- **`slot_acts` cascade / revalidation lock (`vlock`)** on result/activity mutation → **Phase 7** (kept as `TODO Phase 7` markers, consistent with existing Phase 5 code).
- Setting create/edit **form-option tree endpoints** (`provideFieldData` room/jurusan trees from the Blade controller) — those were server-rendered Blade helpers; the REST frontend builds trees client-side from existing list endpoints. Not reintroduced.

---

## 2. Source → Target Mapping

| Target (Spring Boot) | Legacy source (Laravel) |
|----------------------|--------------------------|
| `result/Result.java` | `app/Models/Result.php`, `database/migrations/2020_08_19_125951_create_results_table.php`, `2020_09_15_201409_add_result_valid_column.php` |
| `result/ResultService.java` / `ResultController.java` | `app/Http/Controllers/ResultController.php`, `app/Repositories/ResultRepository.php` |
| `setting/Setting.java` | `app/Models/Setting.php`, `database/migrations/2020_10_25_234434_create_settings_table.php` |
| `setting/SettingableType.java` | `Setting::ROOM_TYPE / ROOM_OWNER / ACTIVITY_TYPE / CUSTOM_ACTIVITY / WAKTU / HARI / JURUSAN` constants |
| `setting/constraint/SettingConstraint.java` | `app/Models/SettingConstraint.php`, `2020_10_29_130238_create_settings_constraints.php` |
| `setting/SettingService.java` / `SettingController.java` | `app/Http/Controllers/SettingController.php`, `app/Repositories/SettingRepository.php`, `SettingConstraintRepository.php` |
| `SemesterService.duplicate/removeCurrentSemesterData` (edit) | `app/Http/Controllers/SemesterController.php@duplicate/removeCurSem` |

---

## 3. Database Schema (existing tables — `ddl-auto=validate` only)

No DDL is generated. The entities below must map **exactly** to the existing Laravel tables.

### `results`
| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | INT UNSIGNED AUTO_INCREMENT | no | PK |
| `semester_id` | INT UNSIGNED | no | FK → `semesters.id` |
| `activity_id` | INT UNSIGNED | no | FK → `activities.id` |
| `room_id` | INT UNSIGNED | **yes** | FK → `rooms.id` (null = unassigned) |
| `day` | TEXT | **yes** | day-of-week string ("1".."6") |
| `start_time` | TIME | **yes** | |
| `end_time` | TIME | **yes** | |
| `valid` | TINYINT | no (default 1) | boolean flag |
| `created_at`, `updated_at` | TIMESTAMP | yes | |
| `deleted_at` | TIMESTAMP | yes | **soft delete** |

### `settings`
| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | INT UNSIGNED AUTO_INCREMENT | no | PK |
| `semester_id` | INT UNSIGNED | no | FK → `semesters.id` |
| `name` | VARCHAR(255) | no | |
| `created_at`, `updated_at` | TIMESTAMP | yes | |
| `deleted_at` | TIMESTAMP | yes | **soft delete** |

### `setting_constraints`
| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | INT UNSIGNED AUTO_INCREMENT | no | PK |
| `setting_id` | INT UNSIGNED | no | FK → `settings.id` |
| `settingable_value` | VARCHAR(255) | no | stored value (id / hour / day) |
| `settingable_type` | VARCHAR(255) | no | one of the 7 `SettingableType` keys |
| `created_at`, `updated_at` | TIMESTAMP | yes | |
| — | — | — | **UNIQUE(`setting_id`,`settingable_value`,`settingable_type`)**, **no soft delete** |

---

## 4. Files to Create

```
new/timetabling-backend/src/main/java/com/timetablingapp/
├── result/
│   ├── Result.java                     # @Entity (soft delete)
│   ├── ResultRepository.java
│   ├── ResultController.java           # CRUD + clear-by-semester + Phase-9 export stubs
│   ├── ResultService.java
│   ├── ResultRequest.java
│   └── ResultResponse.java
│
└── setting/
    ├── Setting.java                    # @Entity (soft delete)
    ├── SettingableType.java            # enum (7 constants) + db value
    ├── SettingableTypeConverter.java   # AttributeConverter<SettingableType,String>
    ├── SettingRepository.java
    ├── SettingController.java          # CRUD
    ├── SettingService.java
    ├── SettingRequest.java
    ├── SettingResponse.java            # list row: id, name, semester label
    ├── SettingDetailResponse.java      # id, name, semesterId + Map<type,List<value>>
    ├── SettingDefaultsProvider.java    # expands "not stored ⇒ all" defaults
    └── constraint/
        ├── SettingConstraint.java      # @Entity (NO soft delete)
        ├── SettingConstraintRepository.java
        └── SettingConstraintDto.java
```

> The roadmap estimated ~15 files; this plan lands at **19** (adds two converters/helpers — `SettingableTypeConverter` and `SettingDefaultsProvider` — following the existing `ConstraintTypeConverter` pattern from Phase 5, and keeps `SettingDetailResponse` + `SettingResponse` split). This is a faithful expansion, not scope creep.

## Files to Edit

| File | Change |
|------|--------|
| `semester/SemesterService.java` | Implement `duplicate()` and `removeCurrentSemesterData()` bodies (remove Phase 5/6 TODOs) using new repositories. |
| `activity/ActivityService.java` | In `delete()`: guard on scheduled result + cascade-delete results (remove `TODO Phase 6` block). |
| `activity/ActivityRepository.java` | No change (uses `ResultRepository` injected into `ActivityService`). |

## Files to Delete
None.

---

## 5. `result/` — Detailed Implementation

### 5.1 `Result.java`

```java
package com.timetablingapp.result;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import com.timetablingapp.room.Room;
import com.timetablingapp.semester.Semester;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalTime;

@Entity
@Table(name = "results")
@SQLDelete(sql = "UPDATE results SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Result extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    /** Nullable — a result may exist before a room is assigned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(columnDefinition = "TEXT")
    private String day;             // "1".."6", nullable

    @Column(name = "start_time")
    private LocalTime startTime;    // nullable

    @Column(name = "end_time")
    private LocalTime endTime;      // nullable

    /** Laravel: tinyInteger default 1. */
    @Column(nullable = false)
    private Boolean valid = true;
}
```

**Notes**
- `day`/`start_time`/`end_time` are nullable (added by `add_result_valid_column` migration).
- `valid` maps `TINYINT` → `Boolean` (Hibernate handles the conversion).
- Soft-delete via `@SQLDelete`/`@SQLRestriction`, identical to `Activity`.

### 5.2 `ResultRepository.java`

```java
package com.timetablingapp.result;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResultRepository extends JpaRepository<Result, Integer> {

    /** Results for one semester (timetable view / export). */
    List<Result> findBySemester_Id(Integer semesterId);

    /**
     * Bulk soft-delete every result of a semester.
     * Mirrors ResultRepository::deleteBasedOnSemesterId($semId).
     * Spring Data derived delete loads rows then delete()s each, honoring @SQLDelete.
     */
    void deleteBySemester_Id(Integer semesterId);

    /** Cascade support for ActivityService.delete(). */
    List<Result> findByActivity_Id(Integer activityId);
    void deleteByActivity_Id(Integer activityId);

    /**
     * Delete-guard for ActivityService: is this activity actually scheduled
     * (assigned to a room)? Mirrors ActivityController@destroy's check.
     */
    boolean existsByActivity_IdAndRoomIsNotNull(Integer activityId);
}
```

### 5.3 `ResultRequest.java`

```java
package com.timetablingapp.result;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ResultRequest {

    /** Optional — null means "current semester". */
    private Integer semesterId;

    @NotNull(message = "activityId is required")
    private Integer activityId;

    /** Nullable — result may be created before room assignment. */
    private Integer roomId;

    private String day;
    private LocalTime startTime;   // "HH:mm[:ss]"
    private LocalTime endTime;

    /** Defaults to true (matches DB default). */
    private Boolean valid = true;
}
```

### 5.4 `ResultResponse.java`

```java
package com.timetablingapp.result;

import lombok.*;

import java.time.LocalTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ResultResponse {

    private Integer id;
    private Integer semesterId;
    private Integer activityId;
    private String  activityName;   // Activity.getName() convenience for the timetable view
    private Integer roomId;
    private String  roomName;
    private String  day;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean valid;

    public static ResultResponse fromEntity(Result r) {
        return ResultResponse.builder()
                .id(r.getId())
                .semesterId(r.getSemester() != null ? r.getSemester().getId() : null)
                .activityId(r.getActivity() != null ? r.getActivity().getId() : null)
                .activityName(r.getActivity() != null ? r.getActivity().getName() : null)
                .roomId(r.getRoom() != null ? r.getRoom().getId() : null)
                .roomName(r.getRoom() != null ? r.getRoom().getName() : null)
                .day(r.getDay())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .valid(r.getValid())
                .build();
    }
}
```

> Confirm `Room.getName()` exists (Phase 4). If `Room` exposes `name` via a different accessor, adjust `roomName` accordingly.

### 5.5 `ResultService.java`

```java
package com.timetablingapp.result;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.activity.ActivityRepository;
import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.room.Room;
import com.timetablingapp.room.RoomRepository;
import com.timetablingapp.semester.Semester;
import com.timetablingapp.semester.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResultService implements BaseCrudService<ResultResponse, ResultRequest, Integer> {

    private final ResultRepository resultRepository;
    private final ActivityRepository activityRepository;
    private final RoomRepository roomRepository;
    private final SemesterRepository semesterRepository;

    // ---- reads ---------------------------------------------------------------

    /** All results, or scoped to a semester when semesterId is given. */
    public List<ResultResponse> findAll(Integer semesterId) {
        List<Result> rows = (semesterId != null)
                ? resultRepository.findBySemester_Id(semesterId)
                : resultRepository.findAll();
        return rows.stream().map(ResultResponse::fromEntity).toList();
    }

    @Override
    public List<ResultResponse> findAll() {
        return findAll(null);
    }

    @Override
    public ResultResponse findById(Integer id) {
        return ResultResponse.fromEntity(getOrThrow(id));
    }

    // ---- writes --------------------------------------------------------------

    @Override
    @Transactional
    public ResultResponse create(ResultRequest request) {
        Result r = new Result();
        apply(r, request, /*allowSemesterDefault*/ true);
        return ResultResponse.fromEntity(resultRepository.save(r));
    }

    @Override
    @Transactional
    public ResultResponse update(Integer id, ResultRequest request) {
        Result r = getOrThrow(id);
        apply(r, request, /*allowSemesterDefault*/ false);
        return ResultResponse.fromEntity(resultRepository.save(r));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        resultRepository.delete(getOrThrow(id));
        // TODO Phase 7: validateLockRepository.lock();
    }

    /**
     * Clear every result for a semester.
     * Mirrors ResultController@destroy → ResultRepository::deleteBasedOnSemesterId.
     */
    @Transactional
    public int deleteBySemester(Integer semesterId) {
        List<Result> rows = resultRepository.findBySemester_Id(semesterId);
        resultRepository.deleteAll(rows);   // honors @SQLDelete
        // TODO Phase 7: validateLockRepository.lock();
        return rows.size();
    }

    // ---- helpers -------------------------------------------------------------

    private void apply(Result r, ResultRequest req, boolean allowSemesterDefault) {
        Semester semester = (req.getSemesterId() != null)
                ? semesterRepository.findById(req.getSemesterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", req.getSemesterId()))
                : (allowSemesterDefault ? currentSemester() : r.getSemester());

        Activity activity = activityRepository.findById(req.getActivityId())
                .orElseThrow(() -> new ResourceNotFoundException("Activity", "id", req.getActivityId()));

        Room room = null;
        if (req.getRoomId() != null) {
            room = roomRepository.findById(req.getRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room", "id", req.getRoomId()));
        }

        r.setSemester(semester);
        r.setActivity(activity);
        r.setRoom(room);
        r.setDay(req.getDay());
        r.setStartTime(req.getStartTime());
        r.setEndTime(req.getEndTime());
        r.setValid(req.getValid() != null ? req.getValid() : Boolean.TRUE);
    }

    private Semester currentSemester() {
        return semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current semester is set"));
    }

    private Result getOrThrow(Integer id) {
        return resultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Result", "id", id));
    }
}
```

> `RoomRepository` accessor name (`findById`) is standard `JpaRepository`; verify `RoomRepository` from Phase 4 is a `JpaRepository<Room, Integer>`.

### 5.6 `ResultController.java`

```java
package com.timetablingapp.result;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService service;

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
    public ResponseEntity<ResultResponse> create(@Valid @RequestBody ResultRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResultResponse> update(
            @PathVariable Integer id, @Valid @RequestBody ResultRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Result deleted successfully"));
    }

    /**
     * DELETE /api/results/semester/{semesterId} — clear a whole semester's schedule.
     * Mirrors Laravel ResultController@destroy(semesterId).
     */
    @DeleteMapping("/semester/{semesterId}")
    public ResponseEntity<MessageResponse> clearSemester(@PathVariable Integer semesterId) {
        int n = service.deleteBySemester(semesterId);
        return ResponseEntity.ok(MessageResponse.success("Cleared " + n + " result(s) for semester " + semesterId));
    }

    // ---- Excel — DEFERRED TO PHASE 9 ----------------------------------------
    // GET  /api/results/export-siakad/{semesterId}
    // GET  /api/results/export-print/{semesterId}
    // POST /api/results/import
    @GetMapping({"/export-siakad/{semesterId}", "/export-print/{semesterId}"})
    public ResponseEntity<Void> exportStub(@PathVariable Integer semesterId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Excel export lands in Phase 9");
    }
}
```

> Prefer the `ResponseStatusException` stub over silently omitting the routes, so the frontend gets a clear `501` rather than a `404` during the interim. Remove/replace in Phase 9.

---

## 6. `setting/` — Detailed Implementation

### 6.1 `SettingableType.java`

Enum keyed to the exact Laravel string constants (these are the values stored in `setting_constraints.settingable_type`).

```java
package com.timetablingapp.setting;

import java.util.Arrays;

/** Mirrors Setting::ROOM_TYPE / ROOM_OWNER / ACTIVITY_TYPE / CUSTOM_ACTIVITY / WAKTU / HARI / JURUSAN. */
public enum SettingableType {
    ROOM_TYPE("roomType"),
    ROOM_OWNER("room"),
    ACTIVITY_TYPE("activityType"),
    CUSTOM_ACTIVITY("activity"),
    WAKTU("waktu"),
    HARI("hari"),
    JURUSAN("jurusan");

    private final String dbValue;

    SettingableType(String dbValue) { this.dbValue = dbValue; }

    public String getDbValue() { return dbValue; }

    public static SettingableType fromDbValue(String v) {
        return Arrays.stream(values())
                .filter(t -> t.dbValue.equals(v))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown settingable_type: " + v));
    }
}
```

### 6.2 `SettingableTypeConverter.java`

Same pattern as `activity/constraint/ConstraintTypeConverter`.

```java
package com.timetablingapp.setting;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class SettingableTypeConverter implements AttributeConverter<SettingableType, String> {

    @Override
    public String convertToDatabaseColumn(SettingableType attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public SettingableType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SettingableType.fromDbValue(dbData);
    }
}
```

### 6.3 `Setting.java`

```java
package com.timetablingapp.setting;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import com.timetablingapp.semester.Semester;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "settings")
@SQLDelete(sql = "UPDATE settings SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Setting extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @NotBlank
    @Column(nullable = false)
    private String name;
}
```

### 6.4 `constraint/SettingConstraint.java`

**No soft delete** — extends `BaseEntity` (timestamps only). Hard-deleted on setting update/delete.

```java
package com.timetablingapp.setting.constraint;

import com.timetablingapp.common.base.BaseEntity;
import com.timetablingapp.setting.Setting;
import com.timetablingapp.setting.SettingableType;
import com.timetablingapp.setting.SettingableTypeConverter;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "setting_constraints",
       uniqueConstraints = @UniqueConstraint(
           name = "setting_constraint_const",
           columnNames = {"setting_id", "settingable_value", "settingable_type"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SettingConstraint extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "setting_id", nullable = false)
    private Setting setting;

    @Column(name = "settingable_value", nullable = false)
    private String value;

    @Convert(converter = SettingableTypeConverter.class)
    @Column(name = "settingable_type", nullable = false)
    private SettingableType type;
}
```

### 6.5 `constraint/SettingConstraintRepository.java`

```java
package com.timetablingapp.setting.constraint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettingConstraintRepository extends JpaRepository<SettingConstraint, Integer> {

    List<SettingConstraint> findBySetting_Id(Integer settingId);

    /** Hard delete (no soft delete on this table) — used on update & delete. */
    void deleteBySetting_Id(Integer settingId);
}
```

### 6.6 `constraint/SettingConstraintDto.java`

Lightweight carrier used by responses (and by the copy logic in `SemesterService`).

```java
package com.timetablingapp.setting.constraint;

import com.timetablingapp.setting.SettingableType;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SettingConstraintDto {
    private SettingableType type;
    private String value;

    public static SettingConstraintDto fromEntity(SettingConstraint c) {
        return SettingConstraintDto.builder()
                .type(c.getType())
                .value(c.getValue())
                .build();
    }
}
```

### 6.7 `SettingRepository.java`

```java
package com.timetablingapp.setting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettingRepository extends JpaRepository<Setting, Integer> {

    List<Setting> findBySemester_Id(Integer semesterId);
}
```

### 6.8 `SettingRequest.java`

The Laravel form posted one array per field plus a `semua` (select-all) array. In REST we carry a `constraints` map (type → selected values) and an explicit `selectAll` set. **When a type is in `selectAll` (or absent from the map), no rows are stored — meaning "no restriction / all allowed"** (exactly the Laravel `if(isset($semua[$field])) continue;` behavior).

```java
package com.timetablingapp.setting;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SettingRequest {

    @NotBlank(message = "name is required")
    private String name;

    /** Optional — null means "current semester" (create only; ignored on update). */
    private Integer semesterId;

    /**
     * type key (SettingableType.dbValue, e.g. "roomType") → selected values.
     * Values are stringified ids / hours / days, matching setting_constraints.settingable_value.
     */
    private Map<String, List<String>> constraints;

    /**
     * Types where the user selected "all" — no rows are persisted for these
     * (absence of a constraint row is interpreted as "everything allowed").
     * Mirrors the Laravel `semua` array.
     */
    private List<String> selectAll;
}
```

### 6.9 `SettingResponse.java` (list row)

```java
package com.timetablingapp.setting;

import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SettingResponse {

    private Integer id;
    private String  name;
    private Integer semesterId;
    /** "{type} {academic_year}" — mirrors SettingController@index typeAndSemester. */
    private String  typeAndSemester;

    public static SettingResponse fromEntity(Setting s) {
        var sem = s.getSemester();
        String label = (sem != null) ? (sem.getType() + " " + sem.getAcademicYear()) : null;
        return SettingResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .semesterId(sem != null ? sem.getId() : null)
                .typeAndSemester(label)
                .build();
    }
}
```

> Verify `Semester` getters `getType()` / `getAcademicYear()` (used already in `SemesterService.next()` — they exist).

### 6.10 `SettingDetailResponse.java`

```java
package com.timetablingapp.setting;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SettingDetailResponse {

    private Integer id;
    private String  name;
    private Integer semesterId;
    private String  typeAndSemester;

    /**
     * Constraints grouped by settingable_type (dbValue key) → list of values.
     * A type with NO stored rows is expanded to the full default set
     * (all ids / all hours / all days) — mirrors Setting::getByType().
     */
    private Map<String, List<String>> constraints;
}
```

### 6.11 `SettingDefaultsProvider.java`

Ports `Setting::getFieldClass()` — the "select all" default value sets, used to expand a type that has no stored constraint rows.

```java
package com.timetablingapp.setting;

import com.timetablingapp.activity.ActivityRepository;
import com.timetablingapp.activity.type.ActivityTypeRepository;
import com.timetablingapp.jurusan.JurusanRepository;
import com.timetablingapp.room.RoomRepository;
import com.timetablingapp.room.type.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class SettingDefaultsProvider {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final ActivityRepository activityRepository;
    private final JurusanRepository jurusanRepository;

    /** All allowed values for a type when the setting stores no explicit rows. */
    public List<String> defaultsFor(SettingableType type) {
        return switch (type) {
            case ROOM_TYPE     -> ids(roomTypeRepository.findAll().stream().map(rt -> rt.getId()));
            case ROOM_OWNER    -> ids(roomRepository.findAll().stream().map(r -> r.getId()));
            case ACTIVITY_TYPE -> ids(activityTypeRepository.findAll().stream().map(at -> at.getId()));
            case CUSTOM_ACTIVITY -> ids(activityRepository.findAll().stream().map(a -> a.getId()));
            case WAKTU -> IntStream.rangeClosed(7, 23).mapToObj(String::valueOf).toList();   // hours 7..23
            case HARI  -> IntStream.rangeClosed(1, 6).mapToObj(String::valueOf).toList();    // Mon..Sat
            case JURUSAN -> ids(jurusanRepository.findAll().stream().map(j -> j.getId()));
        };
    }

    private List<String> ids(java.util.stream.Stream<? extends Object> idStream) {
        return idStream.map(String::valueOf).toList();
    }
}
```

> **Verification needed against Phase 3/4 repos:** confirm class/getter names — `RoomTypeRepository` (Phase 4), `RoomRepository` (Phase 4), `ActivityTypeRepository` (Phase 5), `JurusanRepository` (Phase 3), and each entity's `getId()`. Adjust the lambdas if the entity id type differs. `WAKTU`/`HARI` ranges are hard-coded exactly as in `Setting::getFieldClass()`.

### 6.12 `SettingService.java`

```java
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
            if (selectAll.contains(key)) continue;               // "all" ⇒ store nothing

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
```

**Business logic notes (verbatim from Laravel):**
- Store/update iterate `Setting::FIELD`; a field present in `semua` is skipped (⇒ no rows ⇒ "all allowed").
- The `count == full-set` short-circuit (`if(count($request[$field]) == count(getFieldClass()[$field])) continue;`) is preserved for **every** type (Laravel applied it explicitly only to `CUSTOM_ACTIVITY`, but treating "all selected" as "store nothing" uniformly matches the `getByType` read-side expansion — this keeps read/write symmetric and avoids storing redundant full sets). Confirm this generalization is acceptable; if strict parity is required, gate it to `CUSTOM_ACTIVITY` only.
- Update does a **hard delete** of existing constraints then re-inserts (`$setting->constraint()->delete()` on a non-soft-delete model = hard delete).
- Delete soft-deletes the setting and hard-deletes its constraints.

### 6.13 `SettingController.java`

```java
package com.timetablingapp.setting;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<SettingResponse> create(@Valid @RequestBody SettingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SettingResponse> update(
            @PathVariable Integer id, @Valid @RequestBody SettingRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Setting deleted successfully"));
    }
}
```

---

## 7. Cross-cutting Edits (deferred TODOs now unblocked)

### 7.1 `activity/ActivityService.java` — `delete()`

Replace the `TODO Phase 6` block (lines ~116–119) with a real guard + cascade. Inject `ResultRepository`.

```java
// add to constructor-injected fields
private final ResultRepository resultRepository;   // com.timetablingapp.result.ResultRepository

@Override
@Transactional
public void delete(Integer id) {
    Activity activity = getOrThrow(id);

    // Legacy ActivityController@destroy: block delete when a scheduled Result (room assigned) uses it.
    if (resultRepository.existsByActivity_IdAndRoomIsNotNull(id)) {
        throw new BadRequestException(
            "Cannot delete activity: it is used by a scheduled result.");
    }

    // Cascade: remove any (unscheduled) results referencing this activity.
    resultRepository.deleteByActivity_Id(id);

    constraintRepository.deleteByActivity_Id(id);
    paralelRepository.deleteAllForActivity(id);
    gapRepository.deleteAllForActivity(id);
    activityRepository.delete(activity);
    // TODO Phase 7: cascade delete slot_acts + validateLockRepository.lock();
}
```

> This introduces a compile dependency `activity → result`. `result` already depends on `activity` (FK). Since these are Spring beans wired at runtime (not a Java package cycle that breaks compilation), the bidirectional bean reference is fine. Keep the `TODO Phase 7` slot-acts note.

### 7.2 `semester/SemesterService.java` — `duplicate()` and `removeCurrentSemesterData()`

Inject the repositories needed and implement the bodies (mirroring `SemesterController@duplicate` / `@removeCurSem`). New injected fields:

```java
private final ActivityRepository activityRepository;
private final ActivityConstraintRepository activityConstraintRepository;
private final SettingRepository settingRepository;
private final SettingConstraintRepository settingConstraintRepository;
private final ResultRepository resultRepository;
```

**`duplicate(sourceSemesterId)`** — copy activities (+constraints) and settings (+constraints) from source → current:

```java
@Transactional
public SemesterResponse duplicate(Integer sourceSemesterId) {
    semesterRepository.findById(sourceSemesterId)
            .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", sourceSemesterId));
    Semester current = semesterRepository.findByCurrentTrue()
            .orElseThrow(() -> new BadRequestException("No current semester is set"));

    // 1. Activities + their constraints
    for (Activity old : activityRepository.findBySemester_Id(sourceSemesterId)) {
        Activity copy = new Activity();
        copy.setSemester(current);
        copy.setCourse(old.getCourse());
        copy.setCourseClass(old.getCourseClass());
        copy.setCourseSession(old.getCourseSession());
        copy.setDuration(old.getDuration());
        copy.setQuota(old.getQuota());
        copy.setActivityType(old.getActivityType());
        Activity savedActivity = activityRepository.save(copy);

        for (ActivityConstraint oc : activityConstraintRepository.findByActivity_Id(old.getId())) {
            ActivityConstraint nc = new ActivityConstraint();
            nc.setActivity(savedActivity);
            nc.setType(oc.getType());
            nc.setValue(oc.getValue());
            activityConstraintRepository.save(nc);
        }
    }

    // 2. Settings + their constraints
    for (Setting old : settingRepository.findBySemester_Id(sourceSemesterId)) {
        Setting copy = new Setting();
        copy.setSemester(current);
        copy.setName(old.getName());
        Setting savedSetting = settingRepository.save(copy);

        for (SettingConstraint oc : settingConstraintRepository.findBySetting_Id(old.getId())) {
            SettingConstraint nc = new SettingConstraint();
            nc.setSetting(savedSetting);
            nc.setType(oc.getType());
            nc.setValue(oc.getValue());
            settingConstraintRepository.save(nc);
        }
    }
    // TODO Phase 7: validateLockRepository.lock();
    return SemesterResponse.fromEntity(current);
}
```

**`removeCurrentSemesterData()`** — delete results, activities (+constraints), settings (+constraints) for current semester:

```java
@Transactional
public void removeCurrentSemesterData() {
    Semester current = semesterRepository.findByCurrentTrue()
            .orElseThrow(() -> new BadRequestException("No current semester is set"));
    Integer semId = current.getId();

    // 1. Results
    resultRepository.deleteBySemester_Id(semId);

    // 2. Activity constraints, then activities
    for (Activity a : activityRepository.findBySemester_Id(semId)) {
        activityConstraintRepository.deleteByActivity_Id(a.getId());
        // TODO Phase 7: also clear activity_paralels / activity_gaps / slot_acts
    }
    for (Activity a : activityRepository.findBySemester_Id(semId)) {
        activityRepository.delete(a);
    }

    // 3. Setting constraints, then settings
    for (Setting s : settingRepository.findBySemester_Id(semId)) {
        settingConstraintRepository.deleteBySetting_Id(s.getId());
    }
    for (Setting s : settingRepository.findBySemester_Id(semId)) {
        settingRepository.delete(s);
    }
    // TODO Phase 7: validateLockRepository.lock();
}
```

> Note: `deleteBySemester_Id` on `ResultRepository` and `deleteByActivity_Id` on constraint repos rely on Spring Data derived deletes loading rows first (so `@SQLDelete` fires for soft-delete tables). The `setting_constraints` table has no soft delete, so its derived delete issues a plain `DELETE` — correct.

> If `SemesterService` currently marks these methods with `@Slf4j` warn-logs and Phase-5/6 TODO comments, remove those comments once implemented.

### 7.3 `room/RoomService.java` — `delete()` (found during implementation, not in original file list)

While sweeping the codebase for `TODO Phase 6` markers after implementing `result/`, a second deferred guard was found in `RoomService.delete()` — the Laravel `RoomController@destroy` also blocks deletion when any `Result` row references the room (`Result::where('room_id', $id)->get()`, unconditional on `valid`). This is a one-line addition, not a new file.

Add to `ResultRepository`:

```java
/**
 * Delete-guard for RoomService: is any result using this room?
 * Mirrors Laravel RoomController@destroy: Result::where('room_id', $id)->get().
 */
boolean existsByRoom_Id(Integer roomId);
```

Inject `ResultRepository` into `RoomService` and replace the `TODO Phase 6` line in `delete()`:

```java
// Laravel RoomController@destroy guard #2: no result uses this room
if (resultRepository.existsByRoom_Id(id)) {
    throw new BadRequestException(
        "Cannot delete room: it is used by one or more scheduled results.");
}
// TODO Phase 7: cascade-delete this room's slots + slot_acts
```

---

## 8. Endpoint Summary

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/results` | GET | Auth | List results (`?semesterId=` filter, all if omitted) |
| `/api/results/{id}` | GET | Auth | Single result |
| `/api/results` | POST | ADMIN | Create result |
| `/api/results/{id}` | PUT | ADMIN | Update result |
| `/api/results/{id}` | DELETE | ADMIN | Delete one result |
| `/api/results/semester/{semesterId}` | DELETE | ADMIN | Clear all results for a semester |
| `/api/results/export-siakad/{semesterId}` | GET | Auth | **Phase 9** (501 stub) |
| `/api/results/export-print/{semesterId}` | GET | Auth | **Phase 9** (501 stub) |
| `/api/settings` | GET | Auth | List settings (with semester label) |
| `/api/settings/{id}` | GET | Auth | Setting detail — constraints grouped by type |
| `/api/settings` | POST | ADMIN | Create setting + constraints |
| `/api/settings/{id}` | PUT | ADMIN | Update setting; replace constraints |
| `/api/settings/{id}` | DELETE | ADMIN | Soft-delete setting; hard-delete constraints |

> Method-level auth (`@PreAuthorize("hasRole('ADMIN')")`) follows whatever pattern Phases 2–5 adopted for write endpoints. If those phases left write-authorization to the security filter chain rather than annotations, match that; do not introduce a new convention here.

---

## 9. Verification Criteria

- [ ] `./gradlew clean build` compiles with the new packages and the two edited services.
- [ ] `ddl-auto=validate` succeeds against `results`, `settings`, `setting_constraints` (app boots).
- [ ] **Result CRUD** works with nullable `room_id`/`day`/`start_time`/`end_time`; `valid` defaults to `true`.
- [ ] `GET /api/results?semesterId=X` returns only that semester's results.
- [ ] `DELETE /api/results/semester/{id}` clears the semester and reports the count.
- [ ] **Setting CRUD** works; `GET /api/settings/{id}` returns constraints grouped by type, with un-stored types expanded to full defaults (matching `Setting::getByType`).
- [ ] Updating a setting replaces old constraints (no orphan rows; unique constraint never violated).
- [ ] Deleting a setting soft-deletes the row and removes its constraints.
- [ ] `SemesterService.duplicate()` copies activities + constraints + settings + constraints into the current semester.
- [ ] `SemesterService.removeCurrentSemesterData()` removes results, activities (+constraints), settings (+constraints) for the current semester.
- [ ] `ActivityService.delete()` blocks when a scheduled result uses the activity and cascades unscheduled results otherwise.
- [ ] Swagger UI lists all new endpoints.

---

## 10. Implementation Order (suggested)

1. `result/` entity + repository + DTOs + service + controller (self-contained, no new deps).
2. `setting/` enum + converter + entity + `constraint/` package.
3. `SettingDefaultsProvider` (verify Phase 3/4/5 repo + getter names first).
4. `setting/` service + response DTOs + controller.
5. Edit `ActivityService.delete()` (+ inject `ResultRepository`).
6. Edit `SemesterService.duplicate()` / `removeCurrentSemesterData()`.
7. Boot with `ddl-auto=validate`; smoke-test each endpoint; run through the verification checklist.

---

## 11. Risks / Things to Confirm Before Coding

| Item | Confirm |
|------|---------|
| Phase 4 room accessors | `RoomRepository extends JpaRepository<Room,Integer>`, `Room.getName()`, `Room.getId()` |
| Phase 4/5 repo names for defaults | `RoomTypeRepository`, `ActivityTypeRepository`, `JurusanRepository` packages + `getId()` on entities |
| `valid` column type | `TINYINT` ↔ `Boolean` mapping (if the column is truly `TINYINT(1)` this is fine; if it's a wider int, keep `Boolean` — Hibernate maps 0/≠0) |
| `select-all ⇒ store nothing` generalization | Decide strict parity (only `CUSTOM_ACTIVITY`) vs. uniform (all types). Plan uses uniform; §6.12 documents the toggle. |
| Write-endpoint authorization style | Match Phases 2–5 (annotation vs. filter chain) |
| `Semester.getType()/getAcademicYear()` | Present (already used in `SemesterService.next()`) ✓ |
```
