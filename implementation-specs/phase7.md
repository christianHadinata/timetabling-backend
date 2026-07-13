# Phase 7 — Slot Validation Engine: `schedule/slot/` + `schedule/validate/`

> **Document Version:** 1.0
> **Date:** 2026-07-13
> **Depends on:** Phases 1–6 (config/common, activity/, room/, lecturer/, result/, semester/)
> **Reference roadmap:** [migration-roadmap.md](migration-roadmap.md) §Phase 7
> **Legacy sources:** `timetabling_laravel/app/Models/Activity.php` (`validateSlots`, `isValidCreate`, `getPriority`, lines 171–308 & 484–597), `app/Http/Controllers/SlotActivityController.php`, `app/Repositories/ValidateLockRepository.php`, `app/Models/{Slot,SlotActivity,Time,ValidateLock}.php`

---

## 1. Goal

Port the **slot-validation engine** — the pre-computation step that, for every unscheduled activity, determines the complete set of `(activity, slot)` pairs where the activity *could legally be placed*, together with a **priority** score for each pair. The result is materialised into the `slot_acts` table and later consumed by the genetic algorithm (Phase 8) as the search space.

Concretely this phase delivers:

1. Four JPA entities mapping the existing tables `times`, `slots`, `slot_acts`, `validate_lock` (all pass `ddl-auto=validate`).
2. A **`SlotValidationService`** that faithfully re-implements `Activity::validateSlots()` — the conflict-matrix construction and the `isValidCreate` / `getPriority` checks.
3. A **`ValidateLockService`** implementing the "results are stale" dirty-flag semantics.
4. A **`SlotActivityController`** exposing `revalidate`, `reset`, and a lock-status endpoint.
5. Wiring the dirty-flag `lock()` call into every mutating CRUD service from Phases 3–6 (Activity, Course, Lecturer, Room, Result, Semester) — replacing the `// TODO Phase 7` markers already left in the codebase.

### Important semantic correction to the roadmap

The roadmap says *"`ValidateLock` prevents concurrent revalidation."* That is **not** what the Laravel code does. `validate_lock` is a **single-row dirty flag**:

- `lock = 1` → the master data (activities / rooms / lecturers / results / semester) changed since the last revalidation, so `slot_acts` is **stale** and the UI should prompt the admin to re-run validation.
- `lock = 0` → `slot_acts` is fresh (set by `revalidate` → `vlock->open()`).

Every mutating controller in Laravel calls `$this->vlock->lock()` after a successful write. `revalidate` calls `$this->vlock->open()` at the end. We reproduce exactly this. (It is *not* a mutex and does not block anything.)

---

## 2. Existing database schema (must match — `ddl-auto=validate`)

| Table | Columns | Notes |
|-------|---------|-------|
| `times` | `id` INT unsigned PK, `day` INT, `hour` INT | Lookup table. No timestamps, no soft-delete. Seeded (day 1–7 × hour 7–23). |
| `slots` | `id` INT unsigned PK, `room_id` INT unsigned FK→`rooms`, `time_id` INT unsigned FK→`times` | One row per (room × time). No timestamps, no soft-delete. |
| `slot_acts` | `id` INT unsigned PK, `activity_id` INT unsigned FK→`activities`, `slot_id` INT unsigned FK→`slots`, `priority` INT default 0 | **Hard delete** (no `deleted_at`). No timestamps. |
| `validate_lock` | `id` BIGINT unsigned PK, `lock` TINYINT(1), `created_at`, `updated_at`, `deleted_at` | Uses `id()` → **BIGINT** (→ Java `Long`). Timestamps + soft-delete. |

> ⚠️ **Type detail:** `slots`, `slot_acts`, `times` use `increments()` → `INT unsigned` → Java `Integer`. `validate_lock` uses `id()` → `BIGINT unsigned` → Java `Long`. Mixing these up will fail `validate`.

---

## 3. File inventory

### 3.1 Files to **ADD** (11 files — roadmap listed 9; we split out two converters/services for clarity)

```
src/main/java/com/timetablingapp/schedule/
├── slot/
│   ├── time/
│   │   ├── Time.java                       # @Entity times (lookup)
│   │   └── TimeRepository.java
│   ├── Slot.java                           # @Entity slots
│   ├── SlotRepository.java
│   └── act/
│       ├── SlotActivity.java               # @Entity slot_acts
│       ├── SlotActivityRepository.java
│       ├── SlotActivityController.java     # revalidate / reset / status
│       ├── SlotActivityService.java        # orchestration (reset + revalidate + lock)
│       └── SlotValidationService.java      # THE port of Activity::validateSlots()
└── validate/
    ├── ValidateLock.java                   # @Entity validate_lock
    ├── ValidateLockRepository.java
    └── ValidateLockService.java            # check() / lock() / open()
```

> The roadmap's 9-file count folds `TimeRepository`, `SlotValidationService` and `ValidateLockService` into their siblings. Keeping them separate matches the existing package-by-feature style (every entity has its own repository; heavy logic lives in a dedicated service). Adjust if you prefer the leaner count — the logic is identical.

### 3.2 Files to **EDIT** (wire the dirty flag; replace `// TODO Phase 7` markers)

| File | Change |
|------|--------|
| `result/ResultService.java` | Inject `ValidateLockService`; call `lock()` in `create`, `update`, `delete`, `deleteBySemester`. Replace the two existing `// TODO Phase 7: validateLockRepository.lock();` comments. |
| `activity/ActivityService.java` | Inject `ValidateLockService`; call `lock()` after `create`, `update`, `delete`. |
| `course/CourseService.java` | `lock()` after `create`, `update`, `delete`. |
| `lecturer/LecturerService.java` | `lock()` after `create`, `update`, `delete`. |
| `lecturer/time/LecturerTimeNAService.java` | `lock()` after `create`, `update`, `delete`. |
| `room/RoomService.java` | `lock()` after `create`, `update`, `delete`. |
| `room/available/RoomAvailableService.java` | `lock()` after `create`, `update`, `delete`. |
| `semester/SemesterService.java` | `lock()` after `setCurrent` / `duplicate` (mirrors `SemesterController` line 188). |
| `activity/constraint/ActivityConstraintService.java` | `lock()` after create/delete. |

> These edits are mechanical: add `private final ValidateLockService validateLockService;` to the constructor (already `@RequiredArgsConstructor`) and one `validateLockService.lock();` line inside each `@Transactional` mutation. See §7.

### 3.3 Files to **DELETE**

None. Phase 7 is purely additive plus the wiring edits above.

---

## 4. Entities

### 4.1 `schedule/slot/time/Time.java`

Table `times`: pure lookup, no timestamps, no soft-delete → a plain `@Entity` (does **not** extend `BaseEntity`).

```java
package com.timetablingapp.schedule.slot.time;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "times")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Time {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private Integer day;    // 1..7 (1 = Monday)

    @Column(nullable = false)
    private Integer hour;   // 7..23
}
```

```java
package com.timetablingapp.schedule.slot.time;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TimeRepository extends JpaRepository<Time, Integer> { }
```

### 4.2 `schedule/slot/Slot.java`

Table `slots`: no timestamps, no soft-delete.

```java
package com.timetablingapp.schedule.slot;

import com.timetablingapp.room.Room;
import com.timetablingapp.schedule.slot.time.Time;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "slots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.EAGER)   // time is always needed alongside a slot
    @JoinColumn(name = "time_id", nullable = false)
    private Time time;
}
```

```java
package com.timetablingapp.schedule.slot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Integer> {

    /**
     * All slots with room + time pre-fetched — validation iterates over every slot,
     * so avoid N+1. Mirrors Laravel: Slot::with(['time'])->get() (room is also needed).
     */
    @EntityGraph(attributePaths = {"room", "room.roomType", "room.parentRoom", "time"})
    List<Slot> findAllBy();
}
```

### 4.3 `schedule/slot/act/SlotActivity.java`

Table `slot_acts`: **hard delete**, no timestamps, `priority` default 0.

```java
package com.timetablingapp.schedule.slot.act;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.schedule.slot.Slot;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "slot_acts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SlotActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    @Column(nullable = false)
    private Integer priority = 0;

    public SlotActivity(Activity activity, Slot slot, int priority) {
        this.activity = activity;
        this.slot = slot;
        this.priority = priority;
    }
}
```

```java
package com.timetablingapp.schedule.slot.act;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SlotActivityRepository extends JpaRepository<SlotActivity, Integer> {

    /**
     * Hard-delete all slot_acts belonging to activities of one semester.
     * slot_acts has no deleted_at, so a bulk DELETE is correct here.
     * Mirrors SlotActivityController@resetAll:
     *   SlotActivity::whereHas('activity', fn($q)=>$q->where('semester_id',$id))->delete()
     */
    @Modifying
    @Query("DELETE FROM SlotActivity sa WHERE sa.activity.id IN " +
           "(SELECT a.id FROM Activity a WHERE a.semester.id = :semesterId)")
    int deleteBySemesterId(@Param("semesterId") Integer semesterId);

    /** Clear one activity's slot_acts (used during per-activity rebuild). */
    @Modifying
    @Query("DELETE FROM SlotActivity sa WHERE sa.activity.id = :activityId")
    int deleteByActivityId(@Param("activityId") Integer activityId);
}
```

### 4.4 `schedule/validate/ValidateLock.java`

Table `validate_lock`: **`Long` id (BIGINT)**, timestamps + soft-delete → extends `BaseSoftDeleteEntity`.

```java
package com.timetablingapp.schedule.validate;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "validate_lock")
@SQLDelete(sql = "UPDATE validate_lock SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ValidateLock extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                 // Laravel id() → BIGINT

    /** true = master data changed, slot_acts stale; false = fresh. */
    @Column(nullable = false)
    private Boolean lock = false;

    public ValidateLock(Boolean lock) { this.lock = lock; }
}
```

```java
package com.timetablingapp.schedule.validate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ValidateLockRepository extends JpaRepository<ValidateLock, Long> {

    /** Latest row by created_at — mirrors ->latest('created_at')->first(). */
    Optional<ValidateLock> findFirstByOrderByCreatedAtDesc();
}
```

---

## 5. `ValidateLockService` (port of `ValidateLockRepository.php`)

```java
package com.timetablingapp.schedule.validate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ValidateLockService {

    private final ValidateLockRepository repository;

    /** True when slot_acts is stale (latest row exists and lock = true). */
    @Transactional(readOnly = true)
    public boolean isStale() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(ValidateLock::getLock)
                .orElse(false);
    }

    /** Mark stale — called after any master-data mutation. Upserts the latest row. */
    @Transactional
    public void lock() {
        setLock(true);
    }

    /** Mark fresh — called at the end of a successful revalidate. */
    @Transactional
    public void open() {
        setLock(false);
    }

    private void setLock(boolean value) {
        ValidateLock row = repository.findFirstByOrderByCreatedAtDesc()
                .orElseGet(ValidateLock::new);
        row.setLock(value);
        repository.save(row);   // insert when new, update otherwise (mirrors PHP create/update)
    }
}
```

> **Behavioural note:** Laravel's `open()` blindly updates the latest row and would NPE if none exists; but `revalidate` only runs after the app has been used, so a row always exists. Our `setLock` is null-safe by construction, which is strictly better and observably identical.

---

## 6. `SlotValidationService` — the core port of `Activity::validateSlots()`

This is the heart of the phase. It reproduces the non-model (`ismodel = false`) branch of `Activity::validateSlots()` plus `isValidCreate()` and `getPriority()`.

### 6.1 Algorithm mapping (Laravel → Java)

| Laravel (`Activity.php`) | Java |
|---|---|
| `$data[$day]->rooms[$roomId][$hour]` (true = blocked) | `DayMatrix.roomBlocked[roomId][hour]` |
| `$data[$day]->lecturers[$hour][0][$nik]` (not-available) | `DayMatrix.lecturerNA[hour]` : `Set<String>` |
| `$data[$day]->lecturers[$hour][1][$nik]` (priority) | `DayMatrix.lecturerPriority[hour]` : `Set<String>` |
| `$data[$day]->courses[$hour][$code.$class]` | `DayMatrix.courseTaken[hour]` : `Set<String>` |
| `$data[$day]->bentroks[0][$hour][$key]` (wajib) | `DayMatrix.bentrokWajib[hour]` : `Set<String>` |
| `$data[$day]->bentroks[1][$hour][$key]` (pilihan) | `DayMatrix.bentrokPilihan[hour]` : `Set<String>` |
| `$activity->constraints['Lecturer'/'Room'/'RoomType']` | preloaded `ConstraintBundle` per activity |
| `getBentrokBedaKey()` = `tingkat.",".konsentrasi` | `bedaKey(course)` |
| `getBentrokSamaKey()` = bedaKey + `",".course_class` | `samaKey(activity)` |
| `isWajib()` = `course->type == "Wajib"` | `course.getType() == CourseType.Wajib` |
| `getPriority($time,$data)` | `computePriority(...)` |

Hour range is **7..23** and day range **1..7**, exactly as the loops `for($i=7;$i<24;$i++)` and `for($day=1;$day<=7;$day++)`.

### 6.2 Key faithful-migration decisions

1. **`isExist` semantics.** Laravel `isExist($list,$value)` returns `true` when the list is empty *or* contains the value. So a room/room-type constraint only rejects when the list is **non-empty and does not contain** the candidate → `!set.isEmpty() && !set.contains(x)`.
2. **Room availability default is BLOCKED.** In `validateSlots`, a room with *no* availability window for a given day is `true` (blocked) for every hour; with a window, only hours in `[start,end)` are `false` (free). This matches the older `validateSlots` path the controller actually calls (not the newer `isAvail` OR-semantics in `generateValidateSlots`). We mirror `validateSlots`. One `RoomAvailable` per (room, day) is honoured (Laravel `avails` is keyed by day, last wins).
3. **Scheduled activities are skipped.** Any activity that already appears in a `valid` result for the current semester is left untouched — its existing `slot_acts` are **not** touched. We therefore rebuild `slot_acts` only for *unscheduled* activities (delete-then-insert per activity), which yields the identical end-state to Laravel's in-place diff while being far simpler.
4. **Results source.** Laravel filters results by `valid = 1`, current semester, and `whereHas activity.course jurusan IN Jurusan::jurusanIds()`. `revalidate` is ADMIN-only, and `jurusanIds()` returns all jurusans for an admin, so we load all `valid`, current-semester results. Rows with null `day`/`room`/`start_time` are skipped (they cannot block anything).

### 6.3 Implementation

```java
package com.timetablingapp.schedule.slot.act;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.activity.ActivityRepository;
import com.timetablingapp.activity.constraint.ActivityConstraint;
import com.timetablingapp.activity.constraint.ActivityConstraintRepository;
import com.timetablingapp.activity.constraint.ConstraintType;
import com.timetablingapp.course.Course;
import com.timetablingapp.course.CourseType;
import com.timetablingapp.lecturer.time.LecturerTimeNA;
import com.timetablingapp.lecturer.time.LecturerTimeNARepository;
import com.timetablingapp.lecturer.time.LecturerTimeType;
import com.timetablingapp.result.Result;
import com.timetablingapp.result.ResultRepository;
import com.timetablingapp.room.Room;
import com.timetablingapp.room.RoomRepository;
import com.timetablingapp.room.available.RoomAvailable;
import com.timetablingapp.room.available.RoomAvailableRepository;
import com.timetablingapp.schedule.slot.Slot;
import com.timetablingapp.schedule.slot.SlotRepository;
import com.timetablingapp.schedule.slot.time.Time;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SlotValidationService {

    private static final int MIN_HOUR = 7;
    private static final int MAX_HOUR = 24;   // exclusive
    private static final int MAX_DAY  = 7;

    private final SlotRepository slotRepository;
    private final SlotActivityRepository slotActivityRepository;
    private final ActivityRepository activityRepository;
    private final ActivityConstraintRepository activityConstraintRepository;
    private final RoomRepository roomRepository;
    private final RoomAvailableRepository roomAvailableRepository;
    private final LecturerTimeNARepository lecturerTimeRepository;
    private final ResultRepository resultRepository;

    /** Preloaded activity constraints grouped by type. */
    private record ConstraintBundle(Set<String> lecturerNiks,
                                    Set<Integer> roomIds,
                                    Set<Integer> roomTypeIds) {
        static ConstraintBundle empty() {
            return new ConstraintBundle(new HashSet<>(), new HashSet<>(), new HashSet<>());
        }
    }

    /** Per-day conflict matrices (index by hour 7..23). */
    private static final class DayMatrix {
        final Map<Integer, boolean[]> roomBlocked = new HashMap<>();   // roomId -> [hour]
        final List<Set<String>> lecturerNA       = newHourSets();
        final List<Set<String>> lecturerPriority = newHourSets();
        final List<Set<String>> courseTaken      = newHourSets();
        final List<Set<String>> bentrokWajib     = newHourSets();
        final List<Set<String>> bentrokPilihan   = newHourSets();

        private static List<Set<String>> newHourSets() {
            List<Set<String>> l = new ArrayList<>(MAX_HOUR);
            for (int i = 0; i < MAX_HOUR; i++) l.add(new HashSet<>());
            return l;
        }
    }

    /**
     * Recompute slot_acts for every unscheduled activity of the given semester.
     * Port of Activity::validateSlots($acts) — the ismodel=false branch.
     */
    @Transactional
    public int revalidate(Integer semesterId) {
        // ---- load master data ----------------------------------------------
        List<Activity> activities = activityRepository.findBySemester_Id(semesterId);
        Map<Integer, Activity> activityById = new HashMap<>();
        for (Activity a : activities) activityById.put(a.getId(), a);

        List<Slot> slots = slotRepository.findAllBy();
        List<Room> rooms = roomRepository.findAll();
        Map<Integer, ConstraintBundle> constraintsByActivity = loadConstraints();
        Map<Integer, List<Room>> childrenByParent = indexChildren(rooms);

        // day -> matrix
        Map<Integer, DayMatrix> data = initMatrices(rooms);
        populateLecturerUnavailability(data);
        Set<Integer> scheduled = populateFromResults(
                semesterId, data, activityById, constraintsByActivity, childrenByParent);

        // ---- per-activity rebuild ------------------------------------------
        int written = 0;
        for (Activity activity : activities) {
            if (scheduled.contains(activity.getId())) {
                continue;   // already placed → leave its slot_acts untouched
            }
            slotActivityRepository.deleteByActivityId(activity.getId());

            ConstraintBundle cb = constraintsByActivity
                    .getOrDefault(activity.getId(), ConstraintBundle.empty());
            List<SlotActivity> batch = new ArrayList<>();
            for (Slot slot : slots) {
                if (isValidCreate(slot, activity, cb, data)) {
                    int priority = computePriority(slot, activity, cb, data);
                    batch.add(new SlotActivity(activity, slot, priority));
                }
            }
            slotActivityRepository.saveAll(batch);
            written += batch.size();
        }
        return written;
    }

    // ---- matrix construction ----------------------------------------------------

    private Map<Integer, DayMatrix> initMatrices(List<Room> rooms) {
        // roomId -> day -> availability window (last wins, mirrors Laravel avails keyBy day)
        Map<Integer, Map<Integer, RoomAvailable>> availByRoomDay = new HashMap<>();
        for (RoomAvailable av : roomAvailableRepository.findAll()) {
            availByRoomDay
                .computeIfAbsent(av.getRoom().getId(), k -> new HashMap<>())
                .put(av.getDay(), av);
        }

        Map<Integer, DayMatrix> data = new HashMap<>();
        for (int day = 1; day <= MAX_DAY; day++) {
            DayMatrix m = new DayMatrix();
            for (Room room : rooms) {
                boolean[] blocked = new boolean[MAX_HOUR];
                RoomAvailable av = availByRoomDay
                        .getOrDefault(room.getId(), Map.of()).get(day);
                for (int h = MIN_HOUR; h < MAX_HOUR; h++) {
                    if (av != null) {
                        int start = av.getStartTime().getHour();
                        int end   = av.getEndTime().getHour();
                        blocked[h] = !(h >= start && h < end);
                    } else {
                        blocked[h] = true;   // no window that day → fully blocked
                    }
                }
                m.roomBlocked.put(room.getId(), blocked);
            }
            data.put(day, m);
        }
        return data;
    }

    private void populateLecturerUnavailability(Map<Integer, DayMatrix> data) {
        for (LecturerTimeNA na : lecturerTimeRepository.findAll()) {
            int day = na.getDay();
            DayMatrix m = data.get(day);
            if (m == null) continue;
            String nik = na.getLecturer().getNik();
            int s = na.getStartTime().getHour();
            int e = na.getEndTime().getHour();
            for (int h = s; h < e && h < MAX_HOUR; h++) {
                if (h < MIN_HOUR) continue;
                if (na.getType() == LecturerTimeType.PRIORITY) {
                    m.lecturerPriority.get(h).add(nik);
                } else {
                    m.lecturerNA.get(h).add(nik);
                }
            }
        }
    }

    /** Block slots already occupied by valid results; return scheduled activity ids. */
    private Set<Integer> populateFromResults(
            Integer semesterId,
            Map<Integer, DayMatrix> data,
            Map<Integer, Activity> activityById,
            Map<Integer, ConstraintBundle> constraintsByActivity,
            Map<Integer, List<Room>> childrenByParent) {

        Set<Integer> scheduled = new HashSet<>();
        for (Result res : resultRepository.findBySemester_Id(semesterId)) {
            if (Boolean.FALSE.equals(res.getValid())) continue;
            if (res.getDay() == null || res.getRoom() == null
                    || res.getStartTime() == null || res.getEndTime() == null) continue;

            Activity activity = activityById.get(res.getActivity().getId());
            if (activity == null) continue;               // result for another jurusan/semester
            ConstraintBundle cb = constraintsByActivity
                    .getOrDefault(activity.getId(), ConstraintBundle.empty());

            int day   = Integer.parseInt(res.getDay());
            int start = res.getStartTime().getHour();
            int end   = res.getEndTime().getHour();
            DayMatrix m = data.get(day);
            if (m == null) continue;

            boolean wajib = isWajib(activity);
            String bedaKey = bedaKey(activity.getCourse());
            String samaKey = bedaKey + "," + activity.getCourseClass();
            String courseKey = activity.getCourse().getCode() + activity.getCourseClass();

            for (int h = start; h < end && h < MAX_HOUR; h++) {
                if (h < MIN_HOUR) continue;
                if (wajib) {
                    m.bentrokWajib.get(h).add(samaKey);
                    m.bentrokWajib.get(h).add(bedaKey);
                } else {
                    m.bentrokPilihan.get(h).add(bedaKey);
                }
                m.courseTaken.get(h).add(courseKey);
                for (String nik : cb.lecturerNiks()) m.lecturerNA.get(h).add(nik);

                Room room = res.getRoom();
                blockRoom(m, room.getId(), h);
                if (room.getParentRoom() != null) blockRoom(m, room.getParentRoom().getId(), h);
                for (Room child : childrenByParent.getOrDefault(room.getId(), List.of())) {
                    blockRoom(m, child.getId(), h);
                }
            }
            scheduled.add(activity.getId());
        }
        return scheduled;
    }

    private void blockRoom(DayMatrix m, Integer roomId, int hour) {
        boolean[] arr = m.roomBlocked.get(roomId);
        if (arr != null) arr[hour] = true;
    }

    // ---- validity check (port of isValidCreate) ---------------------------------

    private boolean isValidCreate(Slot slot, Activity activity,
                                  ConstraintBundle cb, Map<Integer, DayMatrix> data) {
        Room room = slot.getRoom();
        Time time = slot.getTime();
        int day = time.getDay();
        int hour = time.getHour();
        DayMatrix m = data.get(day);
        if (m == null) return false;

        // 1. time boundary
        if (hour + activity.getDuration() >= MAX_HOUR) return false;

        // 2. room free for the whole duration
        boolean[] blocked = m.roomBlocked.get(room.getId());
        if (blocked == null) return false;
        for (int i = 0; i < activity.getDuration(); i++) {
            if (blocked[hour + i]) return false;
        }

        // 3. activity room constraint
        if (!cb.roomIds().isEmpty() && !cb.roomIds().contains(room.getId())) return false;

        // 4. activity room-type constraint
        Integer roomTypeId = room.getRoomType() != null ? room.getRoomType().getId() : null;
        if (!cb.roomTypeIds().isEmpty() && !cb.roomTypeIds().contains(roomTypeId)) return false;

        // 5. lecturer not-available / already teaching
        for (String nik : cb.lecturerNiks()) {
            if (m.lecturerNA.get(hour).contains(nik)) return false;
        }

        // 6. room capacity
        if (room.getCapacity() < activity.getQuota()) return false;

        // 7. same course+class, different session already placed this hour
        String courseKey = activity.getCourse().getCode() + activity.getCourseClass();
        if (m.courseTaken.get(hour).contains(courseKey)) return false;

        // 8. curriculum overlap (bentrok)
        if (isWajib(activity)) {
            if (m.bentrokWajib.get(hour).contains(bedaKey(activity.getCourse()) + ","
                    + activity.getCourseClass())) return false;               // wajib vs wajib (samaKey)
            if (m.bentrokPilihan.get(hour).contains(bedaKey(activity.getCourse()))) return false; // wajib vs pilihan
        } else {
            if (m.bentrokWajib.get(hour).contains(bedaKey(activity.getCourse()))) return false;    // pilihan vs wajib
        }
        return true;
    }

    // ---- priority (port of getPriority) -----------------------------------------

    private int computePriority(Slot slot, Activity activity,
                                ConstraintBundle cb, Map<Integer, DayMatrix> data) {
        Time time = slot.getTime();
        int day = time.getDay();
        int hour = time.getHour();
        DayMatrix m = data.get(day);
        int priority = 0;
        for (String nik : cb.lecturerNiks()) {
            if (m.lecturerPriority.get(hour).contains(nik)) priority++;
        }
        Course course = activity.getCourse();
        String jenjang = (course.getJurusan() != null && course.getJurusan().getJenjang() != null)
                ? course.getJurusan().getJenjang().name()   // "S1", "D3", ...
                : "";
        priority += course.getPriorityValue(day, hour, jenjang);
        return priority;
    }

    // ---- helpers ----------------------------------------------------------------

    private Map<Integer, ConstraintBundle> loadConstraints() {
        Map<Integer, ConstraintBundle> map = new HashMap<>();
        for (ActivityConstraint c : activityConstraintRepository.findAll()) {
            ConstraintBundle b = map.computeIfAbsent(
                    c.getActivity().getId(), k -> ConstraintBundle.empty());
            switch (c.getType()) {
                case LECTURER  -> b.lecturerNiks().add(c.getValue());
                case ROOM      -> b.roomIds().add(Integer.valueOf(c.getValue()));
                case ROOM_TYPE -> b.roomTypeIds().add(Integer.valueOf(c.getValue()));
            }
        }
        return map;
    }

    private Map<Integer, List<Room>> indexChildren(List<Room> rooms) {
        Map<Integer, List<Room>> byParent = new HashMap<>();
        for (Room r : rooms) {
            if (r.getParentRoom() != null) {
                byParent.computeIfAbsent(r.getParentRoom().getId(), k -> new ArrayList<>()).add(r);
            }
        }
        return byParent;
    }

    private boolean isWajib(Activity a) {
        return a.getCourse() != null && a.getCourse().getType() == CourseType.Wajib;
    }

    private String bedaKey(Course course) {
        return course.getTingkat() + "," + course.getKonsentrasi();
    }
}
```

> **Performance:** `slots` ≈ `rooms × times` (potentially thousands) and `activities` can be hundreds → the inner loop is O(activities × slots). Laravel disables memory/time limits (`ini_set('memory_limit','-1')`). In Spring this runs inside one `@Transactional`; `saveAll` batches the inserts. If profiling shows this is too slow, add `spring.jpa.properties.hibernate.jdbc.batch_size=100` and a JPQL `SELECT a.id ...` scheduled-set query instead of loading full `Result` graphs. Correctness first — optimise in Phase 10.

---

## 7. `SlotActivityService` (orchestration) + Controller

### 7.1 `schedule/slot/act/SlotActivityService.java`

Mirrors `SlotActivityController@revalidate` / `@resetAll`.

```java
package com.timetablingapp.schedule.slot.act;

import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.schedule.validate.ValidateLockService;
import com.timetablingapp.semester.Semester;
import com.timetablingapp.semester.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlotActivityService {

    private final SlotValidationService slotValidationService;
    private final SlotActivityRepository slotActivityRepository;
    private final ValidateLockService validateLockService;
    private final SemesterRepository semesterRepository;

    /** POST /revalidate — recompute all valid slot-activity pairs, then clear the dirty flag. */
    @Transactional
    public int revalidate() {
        Integer semesterId = currentSemester().getId();
        int written = slotValidationService.revalidate(semesterId);
        validateLockService.open();     // slot_acts now fresh
        return written;
    }

    /** POST /reset — hard-delete all slot_acts for the current semester. */
    @Transactional
    public int reset() {
        Integer semesterId = currentSemester().getId();
        return slotActivityRepository.deleteBySemesterId(semesterId);
    }

    /** GET /status — is slot_acts stale? */
    @Transactional(readOnly = true)
    public boolean isStale() {
        return validateLockService.isStale();
    }

    private Semester currentSemester() {
        return semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current semester is set"));
    }
}
```

### 7.2 `schedule/slot/act/SlotActivityController.java`

The Laravel routes are `GET /activity/revalidate` and `GET /activity/reset` (both `isAdmin`). The roadmap standardises these to `POST /api/slot-activities/{revalidate,reset}`; we follow the roadmap and add a `GET /status` for the dirty-flag banner the old `index` page exposed as `"vlock"`.

```java
package com.timetablingapp.schedule.slot.act;

import com.timetablingapp.common.dto.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/slot-activities")
@RequiredArgsConstructor
public class SlotActivityController {

    private final SlotActivityService service;

    /** POST /api/slot-activities/revalidate — recompute valid (activity, slot) pairs. */
    @PostMapping("/revalidate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> revalidate() {
        int written = service.revalidate();
        return ResponseEntity.ok(MessageResponse.success(
                "Revalidation complete — " + written + " slot-activity pairs written"));
    }

    /** POST /api/slot-activities/reset — clear slot_acts for the current semester. */
    @PostMapping("/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> reset() {
        int deleted = service.reset();
        return ResponseEntity.ok(MessageResponse.success(
                "Reset complete — " + deleted + " slot-activity rows removed"));
    }

    /** GET /api/slot-activities/status — { stale: true|false } (was Laravel "vlock"). */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok(Map.of("stale", service.isStale()));
    }
}
```

### 7.3 Endpoint summary

| Endpoint | Method | Auth | Laravel origin |
|----------|--------|------|----------------|
| `/api/slot-activities/revalidate` | POST | ADMIN | `SlotActivityController@revalidate` (`GET /activity/revalidate`) |
| `/api/slot-activities/reset` | POST | ADMIN | `SlotActivityController@resetAll` (`GET /activity/reset`) |
| `/api/slot-activities/status` | GET | ADMIN | `ActivityController@index` → `"vlock"` = `vlock->check()` |

No `SecurityConfig` change is required: `/api/slot-activities/**` falls under `anyRequest().authenticated()` and the `@PreAuthorize("hasRole('ADMIN')")` annotations enforce the admin gate (method security is already enabled from Phase 1).

---

## 8. Wiring the dirty flag into existing services (EDITs)

Every mutating CRUD service must mark `slot_acts` stale after a successful write — this is what makes the `/status` banner meaningful and mirrors the pervasive `$this->vlock->lock()` calls across the Laravel controllers.

### 8.1 Pattern

Each target service is already `@RequiredArgsConstructor`. Add the dependency field and one call per mutation:

```java
// e.g. result/ResultService.java
private final ValidateLockService validateLockService;   // add to the field list

@Override
@Transactional
public ResultResponse create(ResultRequest request) {
    Result r = new Result();
    apply(r, request, true);
    ResultResponse saved = ResultResponse.fromEntity(resultRepository.save(r));
    validateLockService.lock();        // <-- replaces "// TODO Phase 7: validateLockRepository.lock();"
    return saved;
}
```

Apply the same one-liner in `update`, `delete`, and `deleteBySemester` (the two existing TODO markers at [ResultService.java:69](../src/main/java/com/timetablingapp/result/ResultService.java#L69) and [ResultService.java:80](../src/main/java/com/timetablingapp/result/ResultService.java#L80)).

### 8.2 Where to call `lock()` (matches the Laravel `vlock->lock()` sites)

| Service | Methods to instrument | Laravel reference |
|---------|----------------------|-------------------|
| `ResultService` | create, update, delete, deleteBySemester | `ResultController` line 163 |
| `ActivityService` | create, update, delete | `ActivityController` lines 175, 347, 565, 616 |
| `ActivityConstraintService` | create, delete | (part of activity store/update) |
| `CourseService` | create, update, delete | `CourseController` lines 230, 275 |
| `LecturerService` | create, update, delete | `LecturerController` lines 210, 312, 355 |
| `LecturerTimeNAService` | create, update, delete | `LecturerController` (time endpoints) |
| `RoomService` | create, update, delete | `RoomController` lines 113, 232, 353 |
| `RoomAvailableService` | create, update, delete | `RoomController` (availability) |
| `SemesterService` | setCurrent, duplicate | `SemesterController` line 188 |

> Keep the `lock()` call **inside** the same `@Transactional` method, after the entity save, so it commits atomically with the mutation. Since `ValidateLockService.lock()` is itself `@Transactional`, it will join the caller's transaction (Spring's default `REQUIRED` propagation).

> **Cross-package dependency check:** all these packages will now import `com.timetablingapp.schedule.validate.ValidateLockService`. That is a one-directional dependency (feature packages → `schedule/validate`) and introduces no cycle, because `ValidateLockService` depends only on its own repository.

---

## 9. Verification criteria

Functional (from roadmap, expanded):

- [ ] `ddl-auto=validate` succeeds against `times`, `slots`, `slot_acts`, `validate_lock` — app boots.
- [ ] `POST /api/slot-activities/revalidate` populates `slot_acts` for all unscheduled activities of the current semester and returns the written count.
- [ ] Activities already present in a `valid` result keep their existing `slot_acts` (are **not** cleared).
- [ ] Every written `slot_act` satisfies all eight `isValidCreate` rules (spot-check against a hand-worked example: capacity ≥ quota, room free for full duration, no lecturer clash, no bentrok).
- [ ] `priority` values match `getPriority` (lecturer-priority count + `Course.getPriorityValue`).
- [ ] `POST /api/slot-activities/reset` hard-deletes only the current semester's `slot_acts`.
- [ ] After any create/update/delete on activity/course/lecturer/room/result/semester, `GET /api/slot-activities/status` returns `{ "stale": true }`.
- [ ] After `revalidate`, `GET /api/slot-activities/status` returns `{ "stale": false }`.
- [ ] Non-admin token → 403 on all three endpoints; no token → 401.

Parity check (strongly recommended):

- [ ] Point the Spring app and the Laravel app at the **same** MySQL snapshot, run `revalidate` on each into separate `slot_acts` copies, and diff the resulting `(activity_id, slot_id, priority)` sets — they should be identical.

---

## 10. Risks & notes

1. **`slot_acts` is hard-delete.** Do not add `@SQLDelete`/`deleted_at` to `SlotActivity` — the table has no such column and `validate` would fail. Bulk `@Modifying DELETE` is correct and fast here.
2. **`validate_lock.id` is `BIGINT` (`Long`)** while the slot tables are `INT` (`Integer`). Getting this wrong fails schema validation.
3. **Room availability semantics.** We port the `validateSlots` "blocked by default" rule, not the newer `isAvail` OR-rule used by `generateValidateSlots` (that variant belongs to the Phase 8 GA path). If a room is expected to be free all day but has no `room_availables` row, it will be treated as fully blocked — this is the legacy behaviour and is intentional for parity.
4. **Long-running request.** `revalidate` is synchronous like Laravel. For large datasets consider `spring.transaction.default-timeout` and JDBC batch inserts (Phase 10). Do not make it async in this phase — the frontend expects a synchronous 200 before flipping the banner.
5. **`times`/`slots` are seed data.** This phase assumes they are already populated in the shared DB (Laravel `SlotTableSeeder`). No seeding endpoint is in scope; if a fresh DB is ever needed, port `SlotTableSeeder` in Phase 10.
6. **Enum parity gotchas:** `CourseType.Wajib`/`Pilihan` and `ConstraintType`/`LecturerTimeType` use DB-string values via converters — the service relies on those, never on Java enum `name()`, except `Jenjang.name()` which deliberately equals the DB string (`"S1"`).

---

*End of Phase 7 plan — ready for execution after review.*
