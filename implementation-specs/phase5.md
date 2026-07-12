# Phase 5 Implementation Plan — Scheduling Domain: `activity/`

> **Roadmap ref:** [migration-roadmap.md](./migration-roadmap.md) → Phase 5
> **Depends on:** Phase 1 (`config/`, `common/`), Phase 2 (`semester/`), Phase 3 (`course/`, `jurusan/`), Phase 4 (`lecturer/`, `room/`).
> **Target dir:** `new/timetabling-backend/`
> **Legacy ref (read-only):** `timetabling_laravel/` — primary source `app/Models/Activity.php`, `app/Http/Controllers/ActivityController.php`, `app/Http/Controllers/ActivityTypeController.php`, `app/Repositories/ActivityParalelRepository.php`, `app/Repositories/ActivityGapRepository.php`.
> **Partial Spring ref (read-only):** `timetabling_laravel/genetic/timetablingapp/.../activity/` — useful for shape, but see ⚠️ §1 (it maps a non-existent `constraint_type` column and uses `int semester_id`).
> **Goal:** Activity CRUD with type / constraint / paralel / gap sub-features. Creating or updating an activity persists its lecturer/room/room-type constraints transactionally. `ddl-auto=validate` must pass against `activities`, `activity_types`, `activity_constraints`, `activity_paralels`, `activity_gaps`.

---

## 0. Summary of Work

| Action | Count | Notes |
|--------|-------|-------|
| **Files to ADD** | **24** | `activity/` root (6) + `type/` (6) + `constraint/` (8, incl. enum + converter) + `paralel/` (2) + `gap/` (2) |
| **Files to EDIT** | **2** | `course/CourseService.java` and `lecturer/LecturerService.java` — wire the delete-guards left as `// TODO Phase 5` in Phases 3/4 |
| **Files to DELETE** | **0** | Greenfield additions |

> The roadmap estimated **21** files. The real ADD count is **24** because the `constraint/` sub-package needs a `ConstraintType` enum **and** a `ConstraintTypeConverter` (same reason Phase 4 needed `LecturerTimeTypeConverter`), and it carries a full standalone CRUD stack (controller/service/request/response) mirroring the legacy `activity_constraints` API resource.

Build the `activity/` domain **after** Phases 3 & 4 because `Activity` FK-joins to `courses.code`, references `activity_types`, and its constraints store lecturer NIKs / room IDs / room-type IDs owned by those phases.

---

## 1. ⚠️ Schema Reality Check (read before coding)

I inspected the actual Laravel migrations (`database/migrations/2020_08_19_065006_create_activities_table.php`, `..._create_activity_types_table.php`, `..._create_activity_constraints_table.php`, `2023_04_01_020238_create_activity_paralels.php`, `2023_04_03_022615_create_activity_gaps_table.php`). **Five details will break `ddl-auto=validate`, an `INSERT`, or data integrity if missed:**

### 1a. `activities.course_code` is the FK — a **non-standard** join on `courses.code`
```php
$table->string('course_code',12);
$table->foreign('course_code')->references('code')->on('courses');   // NOT courses.id
```
→ `Activity.course` must be `@ManyToOne` with `@JoinColumn(name = "course_code", referencedColumnName = "code")`. There is **no** `course_id` column. To set the relation on create/update, resolve the `Course` via `courseRepository.findByCode(...)` (that method already exists from Phase 3).

### 1b. There is **no** `room_id` / `room_type_id` on `activities`
Those columns are commented out in the migration. Rooms and room-types for an activity live **only** in `activity_constraints`. Do **not** add them to the `Activity` entity (would fail `validate`).

### 1c. `activity_constraints.type` is a plain `VARCHAR` storing `"Lecturer"` / `"Room"` / `"RoomType"`
```php
$table->string('type');        // 'Lecturer' | 'Room' | 'RoomType'
$table->string('value',100);   // NIK, or room id, or room-type id — all stored as strings
```
A naive `@Enumerated(EnumType.STRING)` would persist `"LECTURER"`/`"ROOM"`/`"ROOM_TYPE"` and **corrupt** existing data + break the GA later. → We need a **JPA `AttributeConverter`** mapping the enum to the exact legacy strings (`ConstraintTypeConverter.java`), exactly like Phase 4's `LecturerTimeTypeConverter`. **`value` is always a `String`** even when it holds a numeric room/room-type id.

### 1d. The genetic/ reference maps a column that does **not** exist
`genetic/.../activity/constraint/ActivityConstraint.java` declares `private String constraint_type;`. That column is **not** in the migration. **Omit it** — mapping it fails `validate`. (Also ignore the genetic ref's `int semester_id`; we model a proper `@ManyToOne Semester`.)

### 1e. `activity_paralels` and `activity_gaps` have **no timestamps and no soft-delete**
```php
// activity_paralels
$table->increments('id');
$table->integer('id_act')->unsigned();       // FK activities.id
$table->integer('with_id_act')->unsigned();  // FK activities.id
// NO timestamps(), NO softDeletes()
// activity_gaps additionally:
$table->integer('min_gap')->unsigned();
```
→ `ActivityParalel` / `ActivityGap` extend **nothing** (not `BaseEntity`, not `BaseSoftDeleteEntity`), carry **no** `@SQLDelete`/`@SQLRestriction`, and use hard deletes.

### Full column reference

**`activities`** (soft delete)
| Column | Type | Notes |
|--------|------|-------|
| id | INT PK auto | |
| semester_id | INT unsigned | FK → semesters.id |
| course_code | VARCHAR(12) | ⚠️ FK → **courses.code** (§1a) |
| course_class | VARCHAR(3) | required |
| course_session | INT | required |
| duration | INT | required (hours) |
| quota | INT | required |
| activity_type_id | INT unsigned | FK → activity_types.id |
| created_at / updated_at / deleted_at | | soft delete |

**`activity_types`** (soft delete)
| Column | Type | Notes |
|--------|------|-------|
| id | INT PK auto | |
| name | TEXT | required |
| created_at / updated_at / deleted_at | | soft delete |

**`activity_constraints`** (soft delete)
| Column | Type | Notes |
|--------|------|-------|
| id | INT PK auto | |
| activity_id | INT unsigned | FK → activities.id |
| type | VARCHAR | ⚠️ `"Lecturer"`/`"Room"`/`"RoomType"` — §1c |
| value | VARCHAR(100) | NIK or room id or room-type id (as string) |
| created_at / updated_at / deleted_at | | soft delete |

**`activity_paralels`** (⚠️ no timestamps / no soft delete — §1e)
| Column | Type | Notes |
|--------|------|-------|
| id | INT PK auto | |
| id_act | INT unsigned | FK → activities.id |
| with_id_act | INT unsigned | FK → activities.id |

**`activity_gaps`** (⚠️ no timestamps / no soft delete — §1e)
| Column | Type | Notes |
|--------|------|-------|
| id | INT PK auto | |
| id_act | INT unsigned | FK → activities.id |
| with_id_act | INT unsigned | FK → activities.id |
| min_gap | INT unsigned | minimum day-gap between the two activities |

---

## 2. Target File Tree (24 files to ADD)

```
new/timetabling-backend/src/main/java/com/timetablingapp/
└── activity/
    ├── Activity.java                       # @Entity (soft delete, FK course_code→courses.code, FK semester, FK activityType)
    ├── ActivityRepository.java
    ├── ActivityController.java             # CRUD /api/activities  (+ semester filter, paralel-candidates)
    ├── ActivityService.java                # orchestrates constraints + paralels + gaps transactionally
    ├── ActivityRequest.java                # + lecturerNiks[], roomIds[], roomTypeIds[], paralelActivityIds[], gaps[]
    ├── ActivityResponse.java               # + activityTypeName, color, lecturerNiks[], roomIds[], roomTypeIds[], paralels[], gaps[]
    │
    ├── type/
    │   ├── ActivityType.java               # @Entity (soft delete)
    │   ├── ActivityTypeRepository.java
    │   ├── ActivityTypeController.java     # CRUD /api/activity-types
    │   ├── ActivityTypeService.java
    │   ├── ActivityTypeRequest.java
    │   └── ActivityTypeResponse.java
    │
    ├── constraint/
    │   ├── ActivityConstraint.java         # @Entity (soft delete, FK activity)
    │   ├── ConstraintType.java             # enum LECTURER / ROOM / ROOM_TYPE
    │   ├── ConstraintTypeConverter.java    # ⚠️ maps enum ↔ "Lecturer"/"Room"/"RoomType" (§1c)
    │   ├── ActivityConstraintRepository.java
    │   ├── ActivityConstraintController.java # /api/activity-constraints  (list?activityId= / create / delete)
    │   ├── ActivityConstraintService.java
    │   ├── ActivityConstraintRequest.java
    │   └── ActivityConstraintResponse.java
    │
    ├── paralel/
    │   ├── ActivityParalel.java            # @Entity (NO soft delete, NO timestamps)
    │   └── ActivityParalelRepository.java
    │
    └── gap/
        ├── ActivityGap.java               # @Entity (NO soft delete, NO timestamps)
        └── ActivityGapRepository.java
```

Files to EDIT (see §9):
```
├── course/CourseService.java              # replace `// TODO Phase 5` delete-guard
└── lecturer/LecturerService.java          # uncomment the ActivityConstraint delete-guard
```

---

## 3. Conventions carried over from Phases 3 & 4

All Phase 5 code follows the established patterns (verified in `course/`, `lecturer/`, `room/`):

- Soft-delete entities extend `BaseSoftDeleteEntity` + `@SQLDelete(...)` + `@SQLRestriction("deleted_at IS NULL")`. Join-only tables with no soft delete extend nothing.
- Services implement `BaseCrudService<RES, REQ, ID>` (note the generic order: **Response, Request, Id**) and throw `ResourceNotFoundException` / `DuplicateResourceException` / `BadRequestException`.
- Controllers are thin, hand-written `@RestController` + `@RequestMapping` + `@RequiredArgsConstructor` constructor injection (Phase 3/4 do **not** extend `BaseCrudController`; they write explicit endpoints so they can add filters). Faculty scoping is read from the JWT via `SecurityContextHolder` (see `CourseController.getCurrentUserFaculty()`).
- Response DTOs use Lombok `@Builder` + a static `fromEntity(...)` factory; request DTOs carry Jakarta validation annotations.
- IDs are `Integer` (matches Laravel `increments('id')`).
- Legacy string enums use an `AttributeConverter` (never `@Enumerated`).
- Mutations that would dirty the slot-validation cache leave a **deferred hook** `// TODO Phase 7: validateLockRepository.lock();` (mirrors Phase 4).

---

## 4. Package `activity/type/` — ActivityType (build first, no deps)

Legacy: `ActivityTypeController.php` (standard CRUD; `destroy` blocks deletion when any activity uses the type).

### 4.1 ADD `activity/type/ActivityType.java`
```java
package com.timetablingapp.activity.type;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "activity_types")
@SQLDelete(sql = "UPDATE activity_types SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityType extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    private String name;
}
```

### 4.2 ADD `activity/type/ActivityTypeRepository.java`
```java
package com.timetablingapp.activity.type;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityTypeRepository extends JpaRepository<ActivityType, Integer> {
}
```

### 4.3 ADD `activity/type/ActivityTypeRequest.java`
```java
package com.timetablingapp.activity.type;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityTypeRequest {

    @NotBlank(message = "Activity type name is required")
    private String name;
}
```

### 4.4 ADD `activity/type/ActivityTypeResponse.java`
```java
package com.timetablingapp.activity.type;

import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ActivityTypeResponse {

    private Integer id;
    private String name;

    public static ActivityTypeResponse fromEntity(ActivityType t) {
        return ActivityTypeResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .build();
    }
}
```

### 4.5 ADD `activity/type/ActivityTypeService.java`
Note the cross-package delete-guard mirrors `ActivityTypeController@destroy` ("Tidak dapat menghapus tipe, karena sedang digunakan pada aktivitas."). It references `ActivityRepository` — Spring resolves the mutual `type ↔ activity` dependency fine at runtime.
```java
package com.timetablingapp.activity.type;

import com.timetablingapp.activity.ActivityRepository;
import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityTypeService
        implements BaseCrudService<ActivityTypeResponse, ActivityTypeRequest, Integer> {

    private final ActivityTypeRepository repository;
    private final ActivityRepository activityRepository;

    @Override
    public List<ActivityTypeResponse> findAll() {
        return repository.findAll().stream().map(ActivityTypeResponse::fromEntity).toList();
    }

    @Override
    public ActivityTypeResponse findById(Integer id) {
        return ActivityTypeResponse.fromEntity(getOrThrow(id));
    }

    @Override
    @Transactional
    public ActivityTypeResponse create(ActivityTypeRequest request) {
        ActivityType t = new ActivityType();
        t.setName(request.getName());
        return ActivityTypeResponse.fromEntity(repository.save(t));
    }

    @Override
    @Transactional
    public ActivityTypeResponse update(Integer id, ActivityTypeRequest request) {
        ActivityType t = getOrThrow(id);
        t.setName(request.getName());
        return ActivityTypeResponse.fromEntity(repository.save(t));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        ActivityType t = getOrThrow(id);
        // Legacy ActivityTypeController@destroy guard.
        if (activityRepository.existsByActivityType_Id(id)) {
            throw new BadRequestException(
                "Cannot delete activity type: it is still used by one or more activities.");
        }
        repository.delete(t);
    }

    private ActivityType getOrThrow(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityType", "id", id));
    }
}
```

### 4.6 ADD `activity/type/ActivityTypeController.java`
```java
package com.timetablingapp.activity.type;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activity-types")
@RequiredArgsConstructor
public class ActivityTypeController {

    private final ActivityTypeService service;

    @GetMapping
    public ResponseEntity<List<ActivityTypeResponse>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivityTypeResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ActivityTypeResponse> create(@Valid @RequestBody ActivityTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ActivityTypeResponse> update(
            @PathVariable Integer id, @Valid @RequestBody ActivityTypeRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Activity type deleted successfully"));
    }
}
```

---

## 5. Package `activity/constraint/` — ActivityConstraint

### 5.1 ADD `activity/constraint/ConstraintType.java`
```java
package com.timetablingapp.activity.constraint;

/**
 * Legacy `type` column stores the raw strings "Lecturer" / "Room" / "RoomType".
 * Persisted via {@link ConstraintTypeConverter} — do NOT use @Enumerated,
 * which would write "LECTURER"/"ROOM"/"ROOM_TYPE" and break existing data + the GA.
 */
public enum ConstraintType {
    LECTURER("Lecturer"),
    ROOM("Room"),
    ROOM_TYPE("RoomType");

    private final String dbValue;

    ConstraintType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static ConstraintType fromDbValue(String value) {
        for (ConstraintType t : values()) {
            if (t.dbValue.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown activity constraint type: " + value);
    }
}
```

### 5.2 ADD `activity/constraint/ConstraintTypeConverter.java`
```java
package com.timetablingapp.activity.constraint;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ConstraintTypeConverter implements AttributeConverter<ConstraintType, String> {

    @Override
    public String convertToDatabaseColumn(ConstraintType attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public ConstraintType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ConstraintType.fromDbValue(dbData);
    }
}
```

### 5.3 ADD `activity/constraint/ActivityConstraint.java`
⚠️ **No `constraint_type` field** (§1d). `value` is always `String` (§1c).
```java
package com.timetablingapp.activity.constraint;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "activity_constraints")
@SQLDelete(sql = "UPDATE activity_constraints SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityConstraint extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Convert(converter = ConstraintTypeConverter.class)
    @Column(nullable = false)
    private ConstraintType type;

    @Column(length = 100, nullable = false)
    private String value;          // lecturer NIK, or room id, or room-type id (as string)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;
}
```

### 5.4 ADD `activity/constraint/ActivityConstraintRepository.java`
```java
package com.timetablingapp.activity.constraint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityConstraintRepository extends JpaRepository<ActivityConstraint, Integer> {

    /** All constraints for one activity (both list + response mapping). */
    List<ActivityConstraint> findByActivity_Id(Integer activityId);

    /** Constraints of one kind for one activity. */
    List<ActivityConstraint> findByActivity_IdAndType(Integer activityId, ConstraintType type);

    /**
     * Soft-delete every constraint of an activity (Spring Data derived delete loads
     * each row and calls delete(), so @SQLDelete is honored — matches Laravel's
     * ActivityConstraint::where('activity_id',$id)->delete()).
     */
    void deleteByActivity_Id(Integer activityId);

    /**
     * Delete-guard for LecturerService: is any activity assigned this lecturer NIK?
     * Mirrors Laravel LecturerController@destroy check.
     */
    boolean existsByTypeAndValue(ConstraintType type, String value);
}
```

### 5.5 ADD `activity/constraint/ActivityConstraintRequest.java`
```java
package com.timetablingapp.activity.constraint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityConstraintRequest {

    @NotNull(message = "activityId is required")
    private Integer activityId;

    @NotNull(message = "type is required (LECTURER, ROOM or ROOM_TYPE)")
    private ConstraintType type;

    @NotBlank(message = "value is required")
    private String value;
}
```

### 5.6 ADD `activity/constraint/ActivityConstraintResponse.java`
```java
package com.timetablingapp.activity.constraint;

import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ActivityConstraintResponse {

    private Integer id;
    private Integer activityId;
    private ConstraintType type;
    private String value;

    public static ActivityConstraintResponse fromEntity(ActivityConstraint c) {
        return ActivityConstraintResponse.builder()
                .id(c.getId())
                .activityId(c.getActivity() != null ? c.getActivity().getId() : null)
                .type(c.getType())
                .value(c.getValue())
                .build();
    }
}
```

### 5.7 ADD `activity/constraint/ActivityConstraintService.java`
Standalone create/list/delete resource (legacy `activity_constraints` API). Bulk sync during activity create/update lives in `ActivityService` (§7), not here.
```java
package com.timetablingapp.activity.constraint;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.activity.ActivityRepository;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityConstraintService {

    private final ActivityConstraintRepository repository;
    private final ActivityRepository activityRepository;

    public List<ActivityConstraintResponse> findByActivityId(Integer activityId) {
        return repository.findByActivity_Id(activityId).stream()
                .map(ActivityConstraintResponse::fromEntity).toList();
    }

    @Transactional
    public ActivityConstraintResponse create(ActivityConstraintRequest request) {
        Activity activity = activityRepository.findById(request.getActivityId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Activity", "id", request.getActivityId()));
        ActivityConstraint c = new ActivityConstraint();
        c.setActivity(activity);
        c.setType(request.getType());
        c.setValue(request.getValue());
        return ActivityConstraintResponse.fromEntity(repository.save(c));
    }

    @Transactional
    public void delete(Integer id) {
        ActivityConstraint c = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityConstraint", "id", id));
        repository.delete(c);
    }
}
```

### 5.8 ADD `activity/constraint/ActivityConstraintController.java`
```java
package com.timetablingapp.activity.constraint;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activity-constraints")
@RequiredArgsConstructor
public class ActivityConstraintController {

    private final ActivityConstraintService service;

    /** GET /api/activity-constraints?activityId=42 */
    @GetMapping
    public ResponseEntity<List<ActivityConstraintResponse>> getByActivity(
            @RequestParam Integer activityId) {
        return ResponseEntity.ok(service.findByActivityId(activityId));
    }

    @PostMapping
    public ResponseEntity<ActivityConstraintResponse> create(
            @Valid @RequestBody ActivityConstraintRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Activity constraint deleted successfully"));
    }
}
```

---

## 6. Packages `activity/paralel/` and `activity/gap/`

⚠️ Plain entities — no base class, no soft delete, no timestamps (§1e). Modeled with raw `Integer` FK columns (not `@ManyToOne`) so the symmetric `id_act = X OR with_id_act = X` lookup stays a one-liner (mirrors `ActivityParalelRepository::getParalelCoursesWith`).

### 6.1 ADD `activity/paralel/ActivityParalel.java`
```java
package com.timetablingapp.activity.paralel;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "activity_paralels")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityParalel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_act", nullable = false)
    private Integer activityId;

    @Column(name = "with_id_act", nullable = false)
    private Integer withActivityId;
}
```

### 6.2 ADD `activity/paralel/ActivityParalelRepository.java`
```java
package com.timetablingapp.activity.paralel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityParalelRepository extends JpaRepository<ActivityParalel, Integer> {

    /** Mirrors ActivityParalelRepository::getParalelCoursesWith($actId). */
    List<ActivityParalel> findByActivityIdOrWithActivityId(Integer activityId, Integer withActivityId);

    /** Mirrors ActivityParalelRepository::deleteAll($actId). */
    @Modifying
    @Query("DELETE FROM ActivityParalel p WHERE p.activityId = :id OR p.withActivityId = :id")
    void deleteAllForActivity(@Param("id") Integer activityId);
}
```

### 6.3 ADD `activity/gap/ActivityGap.java`
```java
package com.timetablingapp.activity.gap;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "activity_gaps")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityGap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_act", nullable = false)
    private Integer activityId;

    @Column(name = "with_id_act", nullable = false)
    private Integer withActivityId;

    @Column(name = "min_gap", nullable = false)
    private Integer minGap;
}
```

### 6.4 ADD `activity/gap/ActivityGapRepository.java`
```java
package com.timetablingapp.activity.gap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityGapRepository extends JpaRepository<ActivityGap, Integer> {

    /** Mirrors ActivityGapRepository::getActivityGaps($actId). */
    List<ActivityGap> findByActivityIdOrWithActivityId(Integer activityId, Integer withActivityId);

    /** Mirrors ActivityGapRepository::deleteAll($actId). */
    @Modifying
    @Query("DELETE FROM ActivityGap g WHERE g.activityId = :id OR g.withActivityId = :id")
    void deleteAllForActivity(@Param("id") Integer activityId);
}
```

---

## 7. Package `activity/` — Activity (aggregate root)

### 7.1 ADD `activity/Activity.java`
⚠️ FK via `course_code` → `courses.code` (§1a); `@ManyToOne Semester` instead of the genetic ref's `int semester_id`.
```java
package com.timetablingapp.activity;

import com.timetablingapp.activity.type.ActivityType;
import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import com.timetablingapp.course.Course;
import com.timetablingapp.semester.Semester;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "activities")
@SQLDelete(sql = "UPDATE activities SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Activity extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    /** ⚠️ Non-standard FK: activities.course_code references courses.code. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_code", referencedColumnName = "code", nullable = false)
    private Course course;

    @NotBlank
    @Column(name = "course_class", length = 3, nullable = false)
    private String courseClass;

    @NotNull
    @Column(name = "course_session", nullable = false)
    private Integer courseSession;

    @NotNull
    @Column(nullable = false)
    private Integer duration;

    @NotNull
    @Column(nullable = false)
    private Integer quota;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_type_id", nullable = false)
    private ActivityType activityType;

    /**
     * Display name — mirrors Activity::getNameAttribute():
     * "{course.name} {course_code} - {course_class} ({course_session})"
     */
    @Transient
    public String getName() {
        String courseName = course != null ? course.getName() : "";
        String code = course != null ? course.getCode() : "";
        return courseName + " " + code + " - " + courseClass + " (" + courseSession + ")";
    }
}
```

### 7.2 ADD `activity/ActivityRepository.java`
```java
package com.timetablingapp.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Integer> {

    /**
     * Activities in a semester, restricted to the caller's faculty jurusans.
     * Mirrors ActivityController@index:
     *   Activity::whereHas('course', fn($q) => $q->whereIn('jurusan_id', Jurusan::jurusanIds()))
     *           ->where('semester_id', $sems)->get()
     */
    List<Activity> findBySemester_IdAndCourse_Jurusan_IdIn(Integer semesterId, List<Integer> jurusanIds);

    /** All activities in a semester (used by revalidate / semester duplicate in later phases). */
    List<Activity> findBySemester_Id(Integer semesterId);

    /**
     * Paralel candidates: same tingkat + same jurusan + same semester.
     * Mirrors ActivityController::getParalelCandidates($tingkat, $jurusanId, $semester).
     */
    List<Activity> findBySemester_IdAndCourse_Jurusan_IdAndCourse_Tingkat(
            Integer semesterId, Integer jurusanId, Integer tingkat);

    /** Duplicate check — mirrors ActivityRepository::contains(). */
    boolean existsBySemester_IdAndCourse_CodeAndCourseClassAndCourseSession(
            Integer semesterId, String courseCode, String courseClass, Integer courseSession);

    /** Delete-guards. */
    boolean existsByCourse_Code(String courseCode);       // CourseService delete guard (§9)
    boolean existsByActivityType_Id(Integer activityTypeId); // ActivityTypeService delete guard (§4.5)
}
```

### 7.3 ADD `activity/ActivityRequest.java`
Clean camelCase contract. `semesterId` optional — defaults to the current semester (Laravel `store` forces the current semester; keep it overridable for imports/tests). Constraint lists replace the legacy `rooms[] / roomTypes[] / lecturers[]` form fields.
```java
package com.timetablingapp.activity;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityRequest {

    /** Optional — null means "current semester". */
    private Integer semesterId;

    @NotBlank(message = "courseCode is required")
    private String courseCode;

    @NotBlank(message = "courseClass is required")
    private String courseClass;

    @NotNull @Min(1)
    private Integer courseSession;

    @NotNull @Min(1)
    private Integer duration;

    @NotNull @Min(1)
    private Integer quota;

    @NotNull(message = "activityTypeId is required")
    private Integer activityTypeId;

    /** Constraint values. All optional; empty ⇒ "no restriction" (matches legacy). */
    private List<String> lecturerNiks;
    private List<Integer> roomIds;
    private List<Integer> roomTypeIds;

    /** Update-only extras. */
    private List<Integer> paralelActivityIds;
    private List<GapDto> gaps;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class GapDto {
        @NotNull private Integer activityId;   // the "with" activity
        @NotNull @Min(1) private Integer minGap;
    }
}
```

### 7.4 ADD `activity/ActivityResponse.java`
Aggregates constraints (always) + paralels/gaps (detail only — left `null` on list responses to avoid N+1). `color` reuses `Course.getColor()` from Phase 3.
```java
package com.timetablingapp.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivityResponse {

    private Integer id;
    private Integer semesterId;
    private String courseCode;
    private String courseName;
    private String courseClass;
    private Integer courseSession;
    private Integer duration;
    private Integer quota;
    private Integer activityTypeId;
    private String activityTypeName;
    private String name;                 // computed display name
    private String color;                // from course → jurusan → color + tingkat

    private List<String> lecturerNiks;
    private List<Integer> roomIds;
    private List<Integer> roomTypeIds;

    // Detail-only (null on list responses)
    private List<ParalelDto> paralels;
    private List<GapDto> gaps;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ParalelDto {
        private Integer id;              // paralel activity id
        private String courseCode;
        private String courseName;
        private String courseClass;
        private Integer courseSession;
        private String activityType;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GapDto {
        private Integer id;              // the "with" activity id
        private String courseCode;
        private String courseName;
        private String courseClass;
        private Integer courseSession;
        private String activityType;
        private Integer minGap;
    }
}
```
> The `fromEntity` mapping is done inside `ActivityService` (it needs the constraint repository), so no static factory here — mirrors how `LecturerService.toResponse` assembles its nested lists.

### 7.5 ADD `activity/ActivityService.java`
The heart of Phase 5 — transactional create/update that also syncs constraints, and (on update) paralels/gaps. Constraint sync mirrors `ActivityController@store/@update`: soft-delete existing rows, then recreate.
```java
package com.timetablingapp.activity;

import com.timetablingapp.activity.constraint.ActivityConstraint;
import com.timetablingapp.activity.constraint.ActivityConstraintRepository;
import com.timetablingapp.activity.constraint.ConstraintType;
import com.timetablingapp.activity.gap.ActivityGap;
import com.timetablingapp.activity.gap.ActivityGapRepository;
import com.timetablingapp.activity.paralel.ActivityParalel;
import com.timetablingapp.activity.paralel.ActivityParalelRepository;
import com.timetablingapp.activity.type.ActivityType;
import com.timetablingapp.activity.type.ActivityTypeRepository;
import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.DuplicateResourceException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.course.Course;
import com.timetablingapp.course.CourseRepository;
import com.timetablingapp.jurusan.JurusanService;
import com.timetablingapp.semester.Semester;
import com.timetablingapp.semester.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityService implements BaseCrudService<ActivityResponse, ActivityRequest, Integer> {

    private final ActivityRepository activityRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final ActivityConstraintRepository constraintRepository;
    private final ActivityParalelRepository paralelRepository;
    private final ActivityGapRepository gapRepository;
    private final CourseRepository courseRepository;
    private final SemesterRepository semesterRepository;
    private final JurusanService jurusanService;

    // ---- reads ---------------------------------------------------------------

    /** Faculty-scoped list for a semester (defaults to current). Mirrors ActivityController@index. */
    public List<ActivityResponse> findAllByFacultyAndSemester(String faculty, Integer semesterId) {
        Integer sem = (semesterId != null) ? semesterId : currentSemester().getId();
        List<Integer> jurusanIds = jurusanService.getJurusanIds(faculty);
        return activityRepository
                .findBySemester_IdAndCourse_Jurusan_IdIn(sem, jurusanIds).stream()
                .map(this::toListResponse)
                .toList();
    }

    @Override
    public List<ActivityResponse> findAll() {
        return activityRepository.findAll().stream().map(this::toListResponse).toList();
    }

    /** Detail view — includes paralels + gaps. Mirrors ActivityController@show. */
    @Override
    public ActivityResponse findById(Integer id) {
        return toDetailResponse(getOrThrow(id));
    }

    // ---- writes --------------------------------------------------------------

    @Override
    @Transactional
    public ActivityResponse create(ActivityRequest request) {
        Semester semester = (request.getSemesterId() != null)
                ? semesterRepository.findById(request.getSemesterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", request.getSemesterId()))
                : currentSemester();

        // Duplicate guard — mirrors ActivityController::isDuplicate().
        if (activityRepository.existsBySemester_IdAndCourse_CodeAndCourseClassAndCourseSession(
                semester.getId(), request.getCourseCode(),
                request.getCourseClass(), request.getCourseSession())) {
            throw new DuplicateResourceException(
                    "Activity",
                    "course",
                    request.getCourseCode() + "(" + request.getCourseClass()
                            + ") session " + request.getCourseSession());
        }

        Activity activity = new Activity();
        activity.setSemester(semester);
        apply(activity, request);
        Activity saved = activityRepository.save(activity);

        syncConstraints(saved, request);
        // TODO Phase 7: validateLockRepository.lock();
        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public ActivityResponse update(Integer id, ActivityRequest request) {
        Activity activity = getOrThrow(id);
        apply(activity, request);              // semester is not changed on update (matches legacy)
        Activity saved = activityRepository.save(activity);

        syncConstraints(saved, request);
        syncParalels(saved, request);
        syncGaps(saved, request);
        // TODO Phase 7: validateLockRepository.lock();
        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Activity activity = getOrThrow(id);

        // Legacy ActivityController@destroy blocks delete when a scheduled Result uses it.
        // TODO Phase 6: if (resultRepository.existsByActivity_IdAndRoomIdNotNull(id))
        //                   throw new BadRequestException("Cannot delete: a result uses this activity.");
        // TODO Phase 6/7: cascade delete slot_acts + results for this activity.

        constraintRepository.deleteByActivity_Id(id);
        paralelRepository.deleteAllForActivity(id);
        gapRepository.deleteAllForActivity(id);
        activityRepository.delete(activity);
        // TODO Phase 7: validateLockRepository.lock();
    }

    /** Paralel candidates for the edit form. Mirrors ActivityController::getParalelCandidates(). */
    public List<ActivityResponse.ParalelDto> getParalelCandidates(Integer id) {
        Activity a = getOrThrow(id);
        return activityRepository.findBySemester_IdAndCourse_Jurusan_IdAndCourse_Tingkat(
                        a.getSemester().getId(),
                        a.getCourse().getJurusan().getId(),
                        a.getCourse().getTingkat()).stream()
                .map(this::toParalelDto)
                .toList();
    }

    // ---- helpers -------------------------------------------------------------

    private void apply(Activity activity, ActivityRequest request) {
        Course course = courseRepository.findByCode(request.getCourseCode())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "code", request.getCourseCode()));
        ActivityType type = activityTypeRepository.findById(request.getActivityTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("ActivityType", "id", request.getActivityTypeId()));
        activity.setCourse(course);
        activity.setCourseClass(request.getCourseClass());
        activity.setCourseSession(request.getCourseSession());
        activity.setDuration(request.getDuration());
        activity.setQuota(request.getQuota());
        activity.setActivityType(type);
    }

    /** Soft-delete existing constraints, then recreate — mirrors legacy store/update. */
    private void syncConstraints(Activity activity, ActivityRequest request) {
        constraintRepository.deleteByActivity_Id(activity.getId());
        createConstraints(activity, ConstraintType.LECTURER, request.getLecturerNiks());
        createConstraints(activity, ConstraintType.ROOM, toStrings(request.getRoomIds()));
        createConstraints(activity, ConstraintType.ROOM_TYPE, toStrings(request.getRoomTypeIds()));
    }

    private void createConstraints(Activity activity, ConstraintType type, List<String> values) {
        if (values == null) return;
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            ActivityConstraint c = new ActivityConstraint();
            c.setActivity(activity);
            c.setType(type);
            c.setValue(v.trim());
            constraintRepository.save(c);
        }
    }

    private void syncParalels(Activity activity, ActivityRequest request) {
        if (request.getParalelActivityIds() == null) return;
        paralelRepository.deleteAllForActivity(activity.getId());
        for (Integer withId : request.getParalelActivityIds()) {
            paralelRepository.save(new ActivityParalel(null, activity.getId(), withId));
        }
    }

    private void syncGaps(Activity activity, ActivityRequest request) {
        if (request.getGaps() == null) return;
        gapRepository.deleteAllForActivity(activity.getId());
        for (ActivityRequest.GapDto g : request.getGaps()) {
            gapRepository.save(new ActivityGap(null, activity.getId(), g.getActivityId(), g.getMinGap()));
        }
    }

    private List<String> toStrings(List<Integer> ids) {
        if (ids == null) return null;
        return ids.stream().map(String::valueOf).toList();
    }

    private Semester currentSemester() {
        return semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current semester is set"));
    }

    private Activity getOrThrow(Integer id) {
        return activityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", "id", id));
    }

    // ---- response mapping ----------------------------------------------------

    private ActivityResponse toListResponse(Activity a) {
        return baseResponse(a).build();
    }

    private ActivityResponse toDetailResponse(Activity a) {
        return baseResponse(a)
                .paralels(loadParalels(a.getId()))
                .gaps(loadGaps(a.getId()))
                .build();
    }

    private ActivityResponse.ActivityResponseBuilder baseResponse(Activity a) {
        // group this activity's constraints by type in one query
        Map<ConstraintType, List<String>> byType = constraintRepository.findByActivity_Id(a.getId())
                .stream()
                .collect(Collectors.groupingBy(
                        ActivityConstraint::getType,
                        Collectors.mapping(ActivityConstraint::getValue, Collectors.toList())));

        List<String> lecturerNiks = byType.getOrDefault(ConstraintType.LECTURER, List.of());
        List<Integer> roomIds = toInts(byType.get(ConstraintType.ROOM));
        List<Integer> roomTypeIds = toInts(byType.get(ConstraintType.ROOM_TYPE));

        return ActivityResponse.builder()
                .id(a.getId())
                .semesterId(a.getSemester() != null ? a.getSemester().getId() : null)
                .courseCode(a.getCourse() != null ? a.getCourse().getCode() : null)
                .courseName(a.getCourse() != null ? a.getCourse().getName() : null)
                .courseClass(a.getCourseClass())
                .courseSession(a.getCourseSession())
                .duration(a.getDuration())
                .quota(a.getQuota())
                .activityTypeId(a.getActivityType() != null ? a.getActivityType().getId() : null)
                .activityTypeName(a.getActivityType() != null ? a.getActivityType().getName() : null)
                .name(a.getName())
                .color(a.getCourse() != null ? a.getCourse().getColor() : null)
                .lecturerNiks(new ArrayList<>(lecturerNiks))
                .roomIds(roomIds)
                .roomTypeIds(roomTypeIds);
    }

    private List<ActivityResponse.ParalelDto> loadParalels(Integer id) {
        List<ActivityParalel> rows = paralelRepository.findByActivityIdOrWithActivityId(id, id);
        List<ActivityResponse.ParalelDto> out = new ArrayList<>();
        for (ActivityParalel p : rows) {
            Integer other = p.getActivityId().equals(id) ? p.getWithActivityId() : p.getActivityId();
            activityRepository.findById(other).ifPresent(o -> out.add(toParalelDto(o)));
        }
        return out;
    }

    private List<ActivityResponse.GapDto> loadGaps(Integer id) {
        List<ActivityGap> rows = gapRepository.findByActivityIdOrWithActivityId(id, id);
        List<ActivityResponse.GapDto> out = new ArrayList<>();
        for (ActivityGap g : rows) {
            Integer other = g.getActivityId().equals(id) ? g.getWithActivityId() : g.getActivityId();
            activityRepository.findById(other).ifPresent(o -> out.add(
                    ActivityResponse.GapDto.builder()
                            .id(o.getId())
                            .courseCode(o.getCourse().getCode())
                            .courseName(o.getCourse().getName())
                            .courseClass(o.getCourseClass())
                            .courseSession(o.getCourseSession())
                            .activityType(o.getActivityType().getName())
                            .minGap(g.getMinGap())
                            .build()));
        }
        return out;
    }

    private ActivityResponse.ParalelDto toParalelDto(Activity o) {
        return ActivityResponse.ParalelDto.builder()
                .id(o.getId())
                .courseCode(o.getCourse().getCode())
                .courseName(o.getCourse().getName())
                .courseClass(o.getCourseClass())
                .courseSession(o.getCourseSession())
                .activityType(o.getActivityType().getName())
                .build();
    }

    private List<Integer> toInts(List<String> vals) {
        if (vals == null) return List.of();
        return vals.stream().map(Integer::valueOf).toList();
    }
}
```

### 7.6 ADD `activity/ActivityController.java`
Faculty scoping copied from `CourseController` (§3). Legacy `web.php` exposed `GET /activities/sems/{sems}` — kept here as `GET /api/activities/semester/{semesterId}`.
```java
package com.timetablingapp.activity;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /** GET /api/activities?semesterId=  (defaults to current semester, faculty-scoped) */
    @GetMapping
    public ResponseEntity<List<ActivityResponse>> getAll(
            @RequestParam(required = false) Integer semesterId) {
        return ResponseEntity.ok(
                activityService.findAllByFacultyAndSemester(getCurrentUserFaculty(), semesterId));
    }

    /** GET /api/activities/semester/{semesterId} — mirrors legacy /activities/sems/{sems} */
    @GetMapping("/semester/{semesterId}")
    public ResponseEntity<List<ActivityResponse>> getBySemester(@PathVariable Integer semesterId) {
        return ResponseEntity.ok(
                activityService.findAllByFacultyAndSemester(getCurrentUserFaculty(), semesterId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivityResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(activityService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ActivityResponse> create(@Valid @RequestBody ActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activityService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ActivityResponse> update(
            @PathVariable Integer id, @Valid @RequestBody ActivityRequest request) {
        return ResponseEntity.ok(activityService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        activityService.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Activity deleted successfully"));
    }

    /** GET /api/activities/{id}/paralel-candidates — same tingkat + jurusan + semester */
    @GetMapping("/{id}/paralel-candidates")
    public ResponseEntity<List<ActivityResponse.ParalelDto>> paralelCandidates(@PathVariable Integer id) {
        return ResponseEntity.ok(activityService.getParalelCandidates(id));
    }

    // NOTE: Excel import/export endpoints (/export, /export-all, /import) are added in Phase 9.

    private String getCurrentUserFaculty() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof String faculty) {
            return faculty;
        }
        return null;
    }
}
```

---

## 8. Files to EDIT — wire the deferred delete-guards

Phase 5 finally makes `activities` / `activity_constraints` available, so two guards stubbed in earlier phases can now be implemented.

### 8.1 EDIT `course/CourseService.java`
Replace the `// TODO: Phase 5` block in `delete(Integer id)` (mirrors the intent left in Phase 3) and inject `ActivityRepository`.

Add field:
```java
    private final CourseRepository courseRepository;
    private final JurusanRepository jurusanRepository;
    private final JurusanService jurusanService;
    private final com.timetablingapp.activity.ActivityRepository activityRepository; // ADD
```
Replace in `delete(...)`:
```java
        // TODO: Phase 5 — Check if course is used in activities before deletion
        // Mirrors Laravel: Activity::where('course_code', $course->code)->get()
        // If activities exist, throw BadRequestException

        courseRepository.delete(course);
```
with:
```java
        // Phase 5: block deletion when any activity references this course code.
        if (activityRepository.existsByCourse_Code(course.getCode())) {
            throw new BadRequestException(
                "Cannot delete course: it is used by one or more activities.");
        }
        courseRepository.delete(course);
```
(`BadRequestException` is already imported in the `course` package via other services; add the import if missing.)

### 8.2 EDIT `lecturer/LecturerService.java`
Uncomment the guard that Phase 4 left as `// TODO Phase 5` in `delete(Integer id)` and inject the constraint repository.

Add field:
```java
    private final LecturerRepository lecturerRepository;
    private final LecturerTimeNARepository timeRepository;
    private final com.timetablingapp.activity.constraint.ActivityConstraintRepository activityConstraintRepository; // ADD
```
Replace the TODO comment:
```java
        // Laravel also blocks when an ActivityConstraint references this lecturer's nik:
        // TODO Phase 5: if activityConstraintRepository.existsByTypeAndValue("Lecturer", l.getNik())
        //               -> throw BadRequestException(...)
```
with:
```java
        // Phase 5: block deletion when an ActivityConstraint references this lecturer's nik.
        if (activityConstraintRepository.existsByTypeAndValue(
                com.timetablingapp.activity.constraint.ConstraintType.LECTURER, l.getNik())) {
            throw new BadRequestException(
                "Cannot delete lecturer: they are assigned to one or more activities.");
        }
```

---

## 9. Endpoint Summary

| Method | Path | Body / Query | Auth |
|--------|------|--------------|------|
| GET | `/api/activity-types` | — | authenticated |
| GET | `/api/activity-types/{id}` | — | authenticated |
| POST | `/api/activity-types` | `ActivityTypeRequest` | authenticated |
| PUT | `/api/activity-types/{id}` | `ActivityTypeRequest` | authenticated |
| DELETE | `/api/activity-types/{id}` | — (400 if in use) | authenticated |
| GET | `/api/activities` | `?semesterId=` (defaults to current, faculty-scoped) | authenticated |
| GET | `/api/activities/semester/{semesterId}` | — | authenticated |
| GET | `/api/activities/{id}` | — (detail: constraints + paralels + gaps) | authenticated |
| POST | `/api/activities` | `ActivityRequest` (+ constraint lists) | authenticated |
| PUT | `/api/activities/{id}` | `ActivityRequest` (+ constraints/paralels/gaps) | authenticated |
| DELETE | `/api/activities/{id}` | — | authenticated |
| GET | `/api/activities/{id}/paralel-candidates` | — | authenticated |
| GET | `/api/activity-constraints` | `?activityId=` | authenticated |
| POST | `/api/activity-constraints` | `ActivityConstraintRequest` | authenticated |
| DELETE | `/api/activity-constraints/{id}` | — | authenticated |

All are covered by the existing `SecurityConfig` (`anyRequest().authenticated()`), so **`SecurityConfig.java` needs no edit.**

---

## 10. Build Order (dependency-safe)

Compile roughly bottom-up:

1. `activity/type/ActivityType` + `Repository` + `Request` + `Response` (no deps).
2. `activity/constraint/ConstraintType` + `ConstraintTypeConverter` (no deps).
3. `activity/Activity` (needs `ActivityType`, `Course`, `Semester`).
4. `activity/ActivityRepository` (needs `Activity`).
5. `activity/constraint/*` rest (needs `Activity` + `ActivityRepository`).
6. `activity/paralel/*` + `activity/gap/*` (need only their own tables).
7. `activity/type/ActivityTypeService` + `Controller` (needs `ActivityRepository` for the delete-guard).
8. `activity/ActivityRequest` / `ActivityResponse` / `ActivityService` / `ActivityController`.
9. EDIT `CourseService` (needs `ActivityRepository`) and `LecturerService` (needs `ActivityConstraintRepository`).

> Intentional two-way package references (`type ↔ activity`, `course ↔ activity`, `lecturer ↔ activity.constraint`) resolve fine at runtime; just make sure both classes exist before the first `./gradlew build`.

---

## 11. Verification Checklist

```bash
cd new/timetabling-backend
./gradlew clean build          # compiles + schema-validates on context load if a DB is wired
./gradlew bootRun              # smoke test
```

- [ ] `ddl-auto=validate` passes against `activities`, `activity_types`, `activity_constraints`, `activity_paralels`, `activity_gaps` (catches the `course_code` FK, the missing `constraint_type`, and the no-timestamps paralel/gap tables early).
- [ ] `POST /api/activities` creates the activity **and** its `activity_constraints` rows in one transaction; the `type` column reads exactly `"Lecturer"`/`"Room"`/`"RoomType"` (verify with `SELECT type FROM activity_constraints`), **not** `"LECTURER"`.
- [ ] Duplicate `(semesterId, courseCode, courseClass, courseSession)` returns **409** (`DuplicateResourceException`).
- [ ] `POST /api/activities` with no `semesterId` attaches to the current semester; **400** if no current semester is set.
- [ ] `PUT /api/activities/{id}` replaces old constraints (old rows soft-deleted, new rows inserted) and replaces paralels/gaps.
- [ ] `GET /api/activities/{id}` returns `lecturerNiks[] / roomIds[] / roomTypeIds[]`, `paralels[]`, `gaps[]`, computed `name`, and `color`.
- [ ] `GET /api/activities` is faculty-scoped (a non-admin only sees activities whose course jurusan is in their faculty).
- [ ] `GET /api/activities/{id}/paralel-candidates` returns activities of the same tingkat + jurusan + semester.
- [ ] ActivityType CRUD works; deleting a type still used by an activity returns **400**.
- [ ] ActivityConstraint list/create/delete works (`?activityId=` filter).
- [ ] **EDIT check:** deleting a `Course` that has activities returns **400**; deleting a `Lecturer` whose NIK is in an `activity_constraint` returns **400**.

---

## 12. Deferred Hooks (do NOT implement in Phase 5)

Leave these as the `// TODO` comments shown above so later phases wire them in:

| Hook | Legacy source | Owning phase |
|------|---------------|--------------|
| `validateLockRepository.lock()` after activity create/update/delete | `ActivityController` | Phase 7 |
| Recompute `slot_acts` (`Activity::validateSlots`) after mutation | `ActivityController`, `SlotActivityController` | Phase 7 |
| Cascade-delete `slot_acts` on activity delete | `ActivityController@destroy` | Phase 7 |
| Block activity delete when a `Result` (with `room_id`) uses it; cascade-delete `results` | `ActivityController@destroy` | Phase 6 |
| Copy activities + constraints on `SemesterService.duplicate` / clear on `removeCurrentSemesterData` | `SemesterService` (Phase 2 stubs) | Phase 6 |
| Excel import/export (`/export`, `/export-all`, `/import`) | `ActivitiesExcel`, `ActivityController@uploadExcel` | Phase 9 |
| GA-side read models (`AlgorithmActivity`, slot priority) | `Activity::getPriority*`, `generateSlots` | Phase 8 |

---

*End of Phase 5 plan. Ready to execute on approval.*
