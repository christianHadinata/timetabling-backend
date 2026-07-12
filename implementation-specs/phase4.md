# Phase 4 Implementation Plan — Resource Domain: `room/` and `lecturer/`

> **Roadmap ref:** [migration-roadmap.md](./migration-roadmap.md) → Phase 4
> **Depends on:** Phase 1 (`config/`, `common/`) only. Independent of Phases 2/3 at compile time.
> **Target dir:** `new/timetabling-backend/`
> **Legacy ref (read-only):** `timetabling_laravel/`
> **Goal:** Room & lecturer management with room types, room availability windows, and lecturer not-available/priority time slots. `ddl-auto=validate` must pass against the existing MySQL tables.

---

## 0. Summary of Work

| Action | Count | Notes |
|--------|-------|-------|
| **Files to ADD** | **32** | 4 sub-packages under `room/` + `lecturer/`, plus 1 JPA converter |
| **Files to EDIT** | **0** | `SecurityConfig` already uses `anyRequest().authenticated()`; no central route registry exists |
| **Files to DELETE** | **0** | Greenfield additions |

> The roadmap estimated ~25 files; the real count is **32** because each of the 4 leaf resources (room, roomType, roomAvailable, lecturer, lecturerTimeNA) is a full 6-file CRUD stack, plus the `LecturerTimeType` enum and a `LecturerTimeTypeConverter`.

---

## 1. ⚠️ Schema Reality Check (read before coding)

I inspected the actual Laravel migrations. **Three details are not in the roadmap and will break `ddl-auto=validate` or `INSERT` if missed:**

### 1a. `lecturers.home_base` is `NOT NULL`
`database/migrations/2020_08_19_031318_create_lecturers_table.php`:
```php
$table->integer('home_base')->unsigned()->nullable(false);  // NOT NULL
```
→ `Lecturer` entity **must** map `homeBase` and `LecturerRequest` must accept it (required), otherwise every INSERT fails. The roadmap's `Lecturer` field list (nik/name/alias) is incomplete.

### 1b. `lecturer_time_n_as.type` is `VARCHAR(100)` storing `"Not-Available"` / `"Priority"`
```php
$table->string('type',100);   // values: 'Not-Available', 'Priority'
```
A naive `@Enumerated(EnumType.STRING)` would persist `"NOT_AVAILABLE"`/`"PRIORITY"` and **corrupt the data** / break the GA later. → We need a **JPA `AttributeConverter`** mapping the enum to the exact legacy strings. (File: `LecturerTimeTypeConverter.java`.)

### 1c. `rooms.virtual` column exists (nullable `VARCHAR(255)`)
```php
$table->string('virtual',255)->nullable();
```
Not required for validate (extra unmapped columns are allowed), but map it for completeness/round-tripping.

### Full column reference

**`room_types`**
| Column | Type | Notes |
|--------|------|-------|
| id | INT PK auto | |
| name | TEXT | required |
| created_at / updated_at / deleted_at | timestamp | soft delete |

**`rooms`**
| Column | Type | Notes |
|--------|------|-------|
| id | INT PK auto | |
| room_code | VARCHAR(20) | required |
| name | TEXT | |
| unit_owner | TEXT | |
| location | TEXT | required |
| building | TEXT | required |
| floor | VARCHAR(20) | required |
| capacity | INT | required |
| parent_room_id | INT unsigned NULL | FK → rooms.id (self) |
| room_type_id | INT unsigned | FK → room_types.id, required |
| virtual | VARCHAR(255) NULL | |
| created_at / updated_at / deleted_at | | soft delete |

**`room_availables`**
| Column | Type | Notes |
|--------|------|-------|
| id | INT PK auto | |
| room_id | INT unsigned | FK → rooms.id |
| day | INT | 1=Mon … 6=Sat |
| start_time | TIME | e.g. `07:00:00` |
| end_time | TIME | |
| created_at / updated_at / deleted_at | | soft delete |

**`lecturers`**
| Column | Type | Notes |
|--------|------|-------|
| id | INT PK auto | |
| nik | VARCHAR(100) indexed | logical unique |
| name | TEXT | required |
| home_base | INT unsigned **NOT NULL** | ⚠️ see 1a |
| alias | TEXT NULL | |
| created_at / updated_at / deleted_at | | soft delete |

**`lecturer_time_n_as`**
| Column | Type | Notes |
|--------|------|-------|
| id | INT PK auto | |
| lecturer_id | INT unsigned | FK → lecturers.id |
| day | INT | 1..6 |
| start_time | TIME | |
| end_time | TIME | |
| type | VARCHAR(100) | ⚠️ `"Not-Available"` / `"Priority"` — see 1b |
| created_at / updated_at / deleted_at | | soft delete |

---

## 2. Target File Tree (32 files to ADD)

```
new/timetabling-backend/src/main/java/com/timetablingapp/
├── room/
│   ├── Room.java                          # @Entity (soft delete, self-ref parent, FK roomType)
│   ├── RoomRepository.java
│   ├── RoomController.java                # CRUD /api/rooms
│   ├── RoomService.java
│   ├── RoomRequest.java
│   ├── RoomResponse.java
│   ├── type/
│   │   ├── RoomType.java                   # @Entity (soft delete)
│   │   ├── RoomTypeRepository.java
│   │   ├── RoomTypeController.java         # CRUD /api/room-types
│   │   ├── RoomTypeService.java
│   │   ├── RoomTypeRequest.java
│   │   └── RoomTypeResponse.java
│   └── available/
│       ├── RoomAvailable.java              # @Entity (soft delete, FK room)
│       ├── RoomAvailableRepository.java
│       ├── RoomAvailableController.java    # CRUD /api/room-availables?roomId=
│       ├── RoomAvailableService.java
│       ├── RoomAvailableRequest.java
│       └── RoomAvailableResponse.java
│
└── lecturer/
    ├── Lecturer.java                       # @Entity (soft delete, incl. homeBase)
    ├── LecturerRepository.java
    ├── LecturerController.java             # CRUD /api/lecturers
    ├── LecturerService.java
    ├── LecturerRequest.java
    ├── LecturerResponse.java               # aggregates notAvailable[] + priority[]
    └── time/
        ├── LecturerTimeNA.java             # @Entity (soft delete, FK lecturer)
        ├── LecturerTimeType.java           # enum NOT_AVAILABLE / PRIORITY
        ├── LecturerTimeTypeConverter.java  # ⚠️ maps enum ↔ "Not-Available"/"Priority"
        ├── LecturerTimeNARepository.java
        ├── LecturerTimeNAController.java    # CRUD /api/lecturer-times?lecturerId=
        ├── LecturerTimeNAService.java
        ├── LecturerTimeRequest.java
        └── LecturerTimeResponse.java
```

---

## 3. Conventions carried over from Phase 3

All Phase 4 code follows the established patterns (verified in `jurusan/`, `course/`):

- Soft-delete entities extend `BaseSoftDeleteEntity`; annotated with `@SQLDelete(...)` + `@SQLRestriction("deleted_at IS NULL")`.
- Services implement `BaseCrudService<RES, REQ, ID>` and throw `ResourceNotFoundException` / `DuplicateResourceException` / `BadRequestException`.
- Controllers are thin: `@RestController` + `@RequestMapping` + constructor injection via `@RequiredArgsConstructor`.
- Response DTOs expose a static `fromEntity(...)` factory; requests carry Jakarta validation annotations.
- IDs are `Integer` (matches Laravel `increments('id')` and Phase 2/3 entities). Write requests wrapped in `@Transactional`.
- Mutations that would affect the slot-validation cache call a **deferred hook** (`// TODO Phase 7: validateLockRepository.lock()`), mirroring how `CourseService.delete` left a `// TODO Phase 5` guard.

---

## 4. Package `room/type/` — RoomType (simplest, build first)

### 4.1 ADD `room/type/RoomType.java`
```java
package com.timetablingapp.room.type;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "room_types")
@SQLDelete(sql = "UPDATE room_types SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RoomType extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;
}
```

### 4.2 ADD `room/type/RoomTypeRepository.java`
```java
package com.timetablingapp.room.type;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, Integer> {
}
```

### 4.3 ADD `room/type/RoomTypeRequest.java`
```java
package com.timetablingapp.room.type;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RoomTypeRequest {
    @NotBlank(message = "Room type name is required")
    private String name;
}
```

### 4.4 ADD `room/type/RoomTypeResponse.java`
```java
package com.timetablingapp.room.type;

import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RoomTypeResponse {
    private Integer id;
    private String name;

    public static RoomTypeResponse fromEntity(RoomType rt) {
        return RoomTypeResponse.builder()
                .id(rt.getId())
                .name(rt.getName())
                .build();
    }
}
```

### 4.5 ADD `room/type/RoomTypeService.java`
```java
package com.timetablingapp.room.type;

import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.room.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomTypeService implements BaseCrudService<RoomTypeResponse, RoomTypeRequest, Integer> {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;   // for delete guard

    @Override
    public List<RoomTypeResponse> findAll() {
        return roomTypeRepository.findAll().stream().map(RoomTypeResponse::fromEntity).toList();
    }

    @Override
    public RoomTypeResponse findById(Integer id) {
        return RoomTypeResponse.fromEntity(getOrThrow(id));
    }

    @Override
    @Transactional
    public RoomTypeResponse create(RoomTypeRequest req) {
        RoomType rt = new RoomType();
        rt.setName(req.getName());
        return RoomTypeResponse.fromEntity(roomTypeRepository.save(rt));
    }

    @Override
    @Transactional
    public RoomTypeResponse update(Integer id, RoomTypeRequest req) {
        RoomType rt = getOrThrow(id);
        rt.setName(req.getName());
        return RoomTypeResponse.fromEntity(roomTypeRepository.save(rt));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        RoomType rt = getOrThrow(id);
        // Laravel RoomTypeController@destroy: block deletion if any room uses this type
        if (roomRepository.existsByRoomType_Id(id)) {
            throw new BadRequestException(
                "Cannot delete room type: it is still used by one or more rooms.");
        }
        roomTypeRepository.delete(rt);
    }

    private RoomType getOrThrow(Integer id) {
        return roomTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", "id", id));
    }
}
```

### 4.6 ADD `room/type/RoomTypeController.java`
```java
package com.timetablingapp.room.type;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/room-types")
@RequiredArgsConstructor
public class RoomTypeController {

    private final RoomTypeService service;

    @GetMapping
    public ResponseEntity<List<RoomTypeResponse>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomTypeResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<RoomTypeResponse> create(@Valid @RequestBody RoomTypeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomTypeResponse> update(@PathVariable Integer id,
                                                   @Valid @RequestBody RoomTypeRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Room type deleted successfully"));
    }
}
```

> **Auth note:** Legacy Laravel exposed these to logged-in users without a role split. The Phase 3 `CourseController` likewise dropped `@PreAuthorize`. Keep room/lecturer endpoints authenticated-only (no `hasRole('ADMIN')`) unless you deliberately tighten it — decide once and stay consistent.

---

## 5. Package `room/available/` — RoomAvailable

### 5.1 ADD `room/available/RoomAvailable.java`
```java
package com.timetablingapp.room.available;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import com.timetablingapp.room.Room;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalTime;

@Entity
@Table(name = "room_availables")
@SQLDelete(sql = "UPDATE room_availables SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RoomAvailable extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private Integer day;               // 1=Mon … 6=Sat

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
```

### 5.2 ADD `room/available/RoomAvailableRepository.java`
```java
package com.timetablingapp.room.available;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomAvailableRepository extends JpaRepository<RoomAvailable, Integer> {

    List<RoomAvailable> findByRoom_IdOrderByDayAsc(Integer roomId);

    Optional<RoomAvailable> findByRoom_IdAndDay(Integer roomId, Integer day);

    void deleteByRoom_Id(Integer roomId);  // soft-delete via @SQLDelete on each row
}
```

### 5.3 ADD `room/available/RoomAvailableRequest.java`
```java
package com.timetablingapp.room.available;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RoomAvailableRequest {

    @NotNull(message = "roomId is required")
    private Integer roomId;

    @NotNull @Min(1) @Max(6)
    private Integer day;

    @NotNull
    @JsonFormat(pattern = "HH:mm")   // accepts "07:00"; also tolerant of "07:00:00"
    private LocalTime startTime;

    @NotNull
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;
}
```

### 5.4 ADD `room/available/RoomAvailableResponse.java`
```java
package com.timetablingapp.room.available;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RoomAvailableResponse {

    private Integer id;
    private Integer roomId;
    private Integer day;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    public static RoomAvailableResponse fromEntity(RoomAvailable ra) {
        return RoomAvailableResponse.builder()
                .id(ra.getId())
                .roomId(ra.getRoom() != null ? ra.getRoom().getId() : null)
                .day(ra.getDay())
                .startTime(ra.getStartTime())
                .endTime(ra.getEndTime())
                .build();
    }
}
```

### 5.5 ADD `room/available/RoomAvailableService.java`
```java
package com.timetablingapp.room.available;

import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.room.Room;
import com.timetablingapp.room.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomAvailableService
        implements BaseCrudService<RoomAvailableResponse, RoomAvailableRequest, Integer> {

    private final RoomAvailableRepository repository;
    private final RoomRepository roomRepository;

    public List<RoomAvailableResponse> findByRoomId(Integer roomId) {
        return repository.findByRoom_IdOrderByDayAsc(roomId).stream()
                .map(RoomAvailableResponse::fromEntity).toList();
    }

    @Override
    public List<RoomAvailableResponse> findAll() {
        return repository.findAll().stream().map(RoomAvailableResponse::fromEntity).toList();
    }

    @Override
    public RoomAvailableResponse findById(Integer id) {
        return RoomAvailableResponse.fromEntity(getOrThrow(id));
    }

    @Override
    @Transactional
    public RoomAvailableResponse create(RoomAvailableRequest req) {
        RoomAvailable ra = new RoomAvailable();
        apply(ra, req);
        return RoomAvailableResponse.fromEntity(repository.save(ra));
    }

    @Override
    @Transactional
    public RoomAvailableResponse update(Integer id, RoomAvailableRequest req) {
        RoomAvailable ra = getOrThrow(id);
        apply(ra, req);
        return RoomAvailableResponse.fromEntity(repository.save(ra));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        repository.delete(getOrThrow(id));
    }

    private void apply(RoomAvailable ra, RoomAvailableRequest req) {
        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", req.getRoomId()));
        ra.setRoom(room);
        ra.setDay(req.getDay());
        ra.setStartTime(req.getStartTime());
        ra.setEndTime(req.getEndTime());
    }

    private RoomAvailable getOrThrow(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoomAvailable", "id", id));
    }
}
```

### 5.6 ADD `room/available/RoomAvailableController.java`
```java
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

    /** GET /api/room-availables            → all
     *  GET /api/room-availables?roomId=5    → filtered by room */
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
    public ResponseEntity<RoomAvailableResponse> create(@Valid @RequestBody RoomAvailableRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomAvailableResponse> update(@PathVariable Integer id,
                                                        @Valid @RequestBody RoomAvailableRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Room availability deleted successfully"));
    }
}
```

---

## 6. Package `room/` — Room (aggregate root)

### 6.1 ADD `room/Room.java`
```java
package com.timetablingapp.room;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import com.timetablingapp.room.type.RoomType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "rooms")
@SQLDelete(sql = "UPDATE rooms SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Room extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(name = "room_code", nullable = false, length = 20)
    private String roomCode;

    @Column(columnDefinition = "TEXT")
    private String name;

    @Column(name = "unit_owner", columnDefinition = "TEXT")
    private String unitOwner;

    @NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    private String location;

    @NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    private String building;

    @NotBlank
    @Column(length = 20, nullable = false)
    private String floor;

    @NotNull
    @Column(nullable = false)
    private Integer capacity;

    /** Self-referential parent. Child rooms are physically inside the parent
     *  and mutually block it during scheduling (used in Phase 7). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_room_id")
    private Room parentRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(length = 255)
    private String virtual;
}
```

### 6.2 ADD `room/RoomRepository.java`
```java
package com.timetablingapp.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Integer> {

    List<Room> findAllByOrderByRoomCodeAsc();

    Optional<Room> findByRoomCode(String roomCode);

    boolean existsByRoomCode(String roomCode);

    /** Children of a given room (used by the delete guard). */
    List<Room> findByParentRoom_Id(Integer parentRoomId);

    boolean existsByParentRoom_Id(Integer parentRoomId);

    /** Used by RoomTypeService delete guard. */
    boolean existsByRoomType_Id(Integer roomTypeId);
}
```

### 6.3 ADD `room/RoomRequest.java`
```java
package com.timetablingapp.room;

import com.timetablingapp.room.available.RoomAvailableRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RoomRequest {

    @NotBlank(message = "Room code is required")
    private String roomCode;

    private String name;
    private String unitOwner;

    @NotBlank(message = "Location is required")
    private String location;

    @NotBlank(message = "Building is required")
    private String building;

    @NotBlank(message = "Floor is required")
    private String floor;

    @NotNull(message = "Capacity is required")
    private Integer capacity;

    /** Nullable. Legacy sends 0 to mean "no parent" — normalized to null in the service. */
    private Integer parentRoomId;

    @NotNull(message = "Room type is required")
    private Integer roomTypeId;

    private String virtual;

    /** Availability windows created/replaced together with the room.
     *  Reuses RoomAvailableRequest.day/startTime/endTime (roomId ignored here). */
    @Valid
    private List<RoomAvailableRequest> availabilities = new ArrayList<>();
}
```

### 6.4 ADD `room/RoomResponse.java`
```java
package com.timetablingapp.room;

import com.timetablingapp.room.available.RoomAvailableResponse;
import com.timetablingapp.room.type.RoomTypeResponse;
import lombok.*;

import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RoomResponse {

    private Integer id;
    private String roomCode;
    private String name;
    private String unitOwner;
    private String location;
    private String building;
    private String floor;
    private Integer capacity;
    private String virtual;

    private Integer parentRoomId;        // mirrors Laravel custom JSON "parent_id"
    private List<Integer> childIds;      // mirrors Laravel custom JSON "child_ids"

    private Integer roomTypeId;
    private RoomTypeResponse roomType;   // nested

    private List<RoomAvailableResponse> availabilities;

    public static RoomResponse fromEntity(Room room,
                                          List<Room> children,
                                          List<RoomAvailableResponse> availabilities) {
        RoomResponse.RoomResponseBuilder b = RoomResponse.builder()
                .id(room.getId())
                .roomCode(room.getRoomCode())
                .name(room.getName())
                .unitOwner(room.getUnitOwner())
                .location(room.getLocation())
                .building(room.getBuilding())
                .floor(room.getFloor())
                .capacity(room.getCapacity())
                .virtual(room.getVirtual())
                .availabilities(availabilities)
                .childIds(children == null ? List.of()
                        : children.stream().map(Room::getId).toList());

        if (room.getParentRoom() != null) {
            b.parentRoomId(room.getParentRoom().getId());
        }
        if (room.getRoomType() != null) {
            b.roomTypeId(room.getRoomType().getId())
             .roomType(RoomTypeResponse.fromEntity(room.getRoomType()));
        }
        return b.build();
    }
}
```

### 6.5 ADD `room/RoomService.java`
```java
package com.timetablingapp.room;

import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.DuplicateResourceException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.room.available.RoomAvailable;
import com.timetablingapp.room.available.RoomAvailableRepository;
import com.timetablingapp.room.available.RoomAvailableRequest;
import com.timetablingapp.room.available.RoomAvailableResponse;
import com.timetablingapp.room.type.RoomType;
import com.timetablingapp.room.type.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService implements BaseCrudService<RoomResponse, RoomRequest, Integer> {

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomAvailableRepository roomAvailableRepository;

    @Override
    public List<RoomResponse> findAll() {
        return roomRepository.findAllByOrderByRoomCodeAsc().stream()
                .map(this::toResponse).toList();
    }

    @Override
    public RoomResponse findById(Integer id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional
    public RoomResponse create(RoomRequest req) {
        if (roomRepository.existsByRoomCode(req.getRoomCode())) {
            throw new DuplicateResourceException("Room", "roomCode", req.getRoomCode());
        }
        Room room = new Room();
        applyScalar(room, req);
        Room saved = roomRepository.save(room);
        replaceAvailabilities(saved, req.getAvailabilities());

        // TODO Phase 7: create Slot rows for every Time, then validateLockRepository.lock();
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RoomResponse update(Integer id, RoomRequest req) {
        Room room = getOrThrow(id);
        if (!room.getRoomCode().equals(req.getRoomCode())
                && roomRepository.existsByRoomCode(req.getRoomCode())) {
            throw new DuplicateResourceException("Room", "roomCode", req.getRoomCode());
        }
        applyScalar(room, req);
        Room saved = roomRepository.save(room);
        replaceAvailabilities(saved, req.getAvailabilities());

        // TODO Phase 7: validateLockRepository.lock();
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Room room = getOrThrow(id);
        // Laravel RoomController@destroy guard #1: no child rooms
        if (roomRepository.existsByParentRoom_Id(id)) {
            throw new BadRequestException(
                "Cannot delete room: it has sub-rooms. Remove them first.");
        }
        // Laravel guard #2 (results) is deferred to Phase 6:
        // TODO Phase 6: if ResultRepository.existsByRoom_Id(id) -> BadRequestException
        // TODO Phase 7: cascade-delete this room's slots + slot_acts

        roomAvailableRepository.deleteByRoom_Id(id);   // soft-delete availabilities
        roomRepository.delete(room);
    }

    // ---- helpers -------------------------------------------------------------

    private void applyScalar(Room room, RoomRequest req) {
        RoomType type = roomTypeRepository.findById(req.getRoomTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", "id", req.getRoomTypeId()));

        room.setRoomCode(req.getRoomCode());
        room.setName(req.getName());
        room.setUnitOwner(req.getUnitOwner());
        room.setLocation(req.getLocation());
        room.setBuilding(req.getBuilding());
        room.setFloor(req.getFloor());
        room.setCapacity(req.getCapacity());
        room.setVirtual(req.getVirtual());
        room.setRoomType(type);

        // Legacy: parent_room_id == 0 means "no parent"
        Integer parentId = req.getParentRoomId();
        if (parentId == null || parentId == 0) {
            room.setParentRoom(null);
        } else {
            Room parent = roomRepository.findById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Room", "id", parentId));
            room.setParentRoom(parent);
        }
    }

    /** Clear existing availability rows and recreate from the request. */
    private void replaceAvailabilities(Room room, List<RoomAvailableRequest> reqs) {
        roomAvailableRepository.deleteByRoom_Id(room.getId());
        if (reqs == null) return;
        for (RoomAvailableRequest r : reqs) {
            RoomAvailable ra = new RoomAvailable();
            ra.setRoom(room);
            ra.setDay(r.getDay());
            ra.setStartTime(r.getStartTime());
            ra.setEndTime(r.getEndTime());
            roomAvailableRepository.save(ra);
        }
    }

    private RoomResponse toResponse(Room room) {
        List<Room> children = roomRepository.findByParentRoom_Id(room.getId());
        List<RoomAvailableResponse> avails =
                roomAvailableRepository.findByRoom_IdOrderByDayAsc(room.getId()).stream()
                        .map(RoomAvailableResponse::fromEntity).toList();
        return RoomResponse.fromEntity(room, children, avails);
    }

    private Room getOrThrow(Integer id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", id));
    }
}
```

### 6.6 ADD `room/RoomController.java`
```java
package com.timetablingapp.room;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService service;

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomResponse> update(@PathVariable Integer id,
                                               @Valid @RequestBody RoomRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Room deleted successfully"));
    }

    // NOTE: Excel import/export (GET /export, POST /import) added in Phase 9.
}
```

---

## 7. Package `lecturer/time/` — LecturerTimeNA

### 7.1 ADD `lecturer/time/LecturerTimeType.java`
```java
package com.timetablingapp.lecturer.time;

/**
 * Legacy `type` column stores the raw strings "Not-Available" / "Priority".
 * Persisted via {@link LecturerTimeTypeConverter} — do NOT use @Enumerated,
 * which would write "NOT_AVAILABLE"/"PRIORITY" and break existing data + the GA.
 */
public enum LecturerTimeType {
    NOT_AVAILABLE("Not-Available"),
    PRIORITY("Priority");

    private final String dbValue;

    LecturerTimeType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static LecturerTimeType fromDbValue(String value) {
        for (LecturerTimeType t : values()) {
            if (t.dbValue.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown lecturer time type: " + value);
    }
}
```

### 7.2 ADD `lecturer/time/LecturerTimeTypeConverter.java`
```java
package com.timetablingapp.lecturer.time;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class LecturerTimeTypeConverter
        implements AttributeConverter<LecturerTimeType, String> {

    @Override
    public String convertToDatabaseColumn(LecturerTimeType attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public LecturerTimeType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LecturerTimeType.fromDbValue(dbData);
    }
}
```

### 7.3 ADD `lecturer/time/LecturerTimeNA.java`
```java
package com.timetablingapp.lecturer.time;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import com.timetablingapp.lecturer.Lecturer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalTime;

@Entity
@Table(name = "lecturer_time_n_as")
@SQLDelete(sql = "UPDATE lecturer_time_n_as SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LecturerTimeNA extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id", nullable = false)
    private Lecturer lecturer;

    @Column(nullable = false)
    private Integer day;               // 1..6

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Convert(converter = LecturerTimeTypeConverter.class)
    @Column(length = 100, nullable = false)
    private LecturerTimeType type;
}
```

### 7.4 ADD `lecturer/time/LecturerTimeNARepository.java`
```java
package com.timetablingapp.lecturer.time;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LecturerTimeNARepository extends JpaRepository<LecturerTimeNA, Integer> {

    List<LecturerTimeNA> findByLecturer_IdOrderByDayAsc(Integer lecturerId);

    List<LecturerTimeNA> findByLecturer_IdAndType(Integer lecturerId, LecturerTimeType type);

    void deleteByLecturer_Id(Integer lecturerId);

    boolean existsByLecturer_Id(Integer lecturerId);
}
```

### 7.5 ADD `lecturer/time/LecturerTimeRequest.java`
```java
package com.timetablingapp.lecturer.time;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LecturerTimeRequest {

    @NotNull(message = "lecturerId is required")
    private Integer lecturerId;

    @NotNull @Min(1) @Max(6)
    private Integer day;

    @NotNull
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    @NotNull(message = "type is required (NOT_AVAILABLE or PRIORITY)")
    private LecturerTimeType type;
}
```

### 7.6 ADD `lecturer/time/LecturerTimeResponse.java`
```java
package com.timetablingapp.lecturer.time;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LecturerTimeResponse {

    private Integer id;
    private Integer lecturerId;
    private Integer day;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private LecturerTimeType type;

    public static LecturerTimeResponse fromEntity(LecturerTimeNA e) {
        return LecturerTimeResponse.builder()
                .id(e.getId())
                .lecturerId(e.getLecturer() != null ? e.getLecturer().getId() : null)
                .day(e.getDay())
                .startTime(e.getStartTime())
                .endTime(e.getEndTime())
                .type(e.getType())
                .build();
    }
}
```

### 7.7 ADD `lecturer/time/LecturerTimeNAService.java`
```java
package com.timetablingapp.lecturer.time;

import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.lecturer.Lecturer;
import com.timetablingapp.lecturer.LecturerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LecturerTimeNAService
        implements BaseCrudService<LecturerTimeResponse, LecturerTimeRequest, Integer> {

    private final LecturerTimeNARepository repository;
    private final LecturerRepository lecturerRepository;

    public List<LecturerTimeResponse> findByLecturerId(Integer lecturerId) {
        return repository.findByLecturer_IdOrderByDayAsc(lecturerId).stream()
                .map(LecturerTimeResponse::fromEntity).toList();
    }

    @Override
    public List<LecturerTimeResponse> findAll() {
        return repository.findAll().stream().map(LecturerTimeResponse::fromEntity).toList();
    }

    @Override
    public LecturerTimeResponse findById(Integer id) {
        return LecturerTimeResponse.fromEntity(getOrThrow(id));
    }

    @Override
    @Transactional
    public LecturerTimeResponse create(LecturerTimeRequest req) {
        LecturerTimeNA e = new LecturerTimeNA();
        apply(e, req);
        return LecturerTimeResponse.fromEntity(repository.save(e));
    }

    @Override
    @Transactional
    public LecturerTimeResponse update(Integer id, LecturerTimeRequest req) {
        LecturerTimeNA e = getOrThrow(id);
        apply(e, req);
        return LecturerTimeResponse.fromEntity(repository.save(e));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        repository.delete(getOrThrow(id));
    }

    private void apply(LecturerTimeNA e, LecturerTimeRequest req) {
        Lecturer lecturer = lecturerRepository.findById(req.getLecturerId())
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer", "id", req.getLecturerId()));
        e.setLecturer(lecturer);
        e.setDay(req.getDay());
        e.setStartTime(req.getStartTime());
        e.setEndTime(req.getEndTime());
        e.setType(req.getType());
    }

    private LecturerTimeNA getOrThrow(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LecturerTimeNA", "id", id));
    }
}
```

### 7.8 ADD `lecturer/time/LecturerTimeNAController.java`
```java
package com.timetablingapp.lecturer.time;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lecturer-times")
@RequiredArgsConstructor
public class LecturerTimeNAController {

    private final LecturerTimeNAService service;

    /** GET /api/lecturer-times?lecturerId=3 (or all if omitted) */
    @GetMapping
    public ResponseEntity<List<LecturerTimeResponse>> getAll(
            @RequestParam(required = false) Integer lecturerId) {
        return ResponseEntity.ok(
                lecturerId != null ? service.findByLecturerId(lecturerId) : service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LecturerTimeResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<LecturerTimeResponse> create(@Valid @RequestBody LecturerTimeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LecturerTimeResponse> update(@PathVariable Integer id,
                                                       @Valid @RequestBody LecturerTimeRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Lecturer time deleted successfully"));
    }
}
```

---

## 8. Package `lecturer/` — Lecturer (aggregate root)

### 8.1 ADD `lecturer/Lecturer.java`
```java
package com.timetablingapp.lecturer;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "lecturers")
@SQLDelete(sql = "UPDATE lecturers SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Lecturer extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String nik;                 // logical unique (indexed in DB, not a UNIQUE constraint)

    @NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    private String name;

    /** ⚠️ NOT NULL in the schema — must always be set. FK-like reference to a jurusan/faculty id. */
    @NotNull
    @Column(name = "home_base", nullable = false)
    private Integer homeBase;

    @Column(columnDefinition = "TEXT")
    private String alias;
}
```

> **`nik` uniqueness:** the DB has only a plain `index`, not a `UNIQUE` constraint, so we enforce it in the service (`existsByNik`) rather than via `@Column(unique = true)` — adding a unique constraint would change DDL and fail `validate`.

### 8.2 ADD `lecturer/LecturerRepository.java`
```java
package com.timetablingapp.lecturer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LecturerRepository extends JpaRepository<Lecturer, Integer> {

    List<Lecturer> findAllByOrderByNikAsc();

    Optional<Lecturer> findByNik(String nik);

    boolean existsByNik(String nik);
}
```

### 8.3 ADD `lecturer/LecturerRequest.java`
```java
package com.timetablingapp.lecturer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LecturerRequest {

    @NotBlank(message = "NIK is required")
    private String nik;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Home base is required")   // ⚠️ NOT NULL column
    private Integer homeBase;

    private String alias;
}
```

### 8.4 ADD `lecturer/LecturerResponse.java`
```java
package com.timetablingapp.lecturer;

import com.timetablingapp.lecturer.time.LecturerTimeResponse;
import lombok.*;

import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LecturerResponse {

    private Integer id;
    private String nik;
    private String name;
    private Integer homeBase;
    private String alias;

    /** Times grouped by LecturerTimeType (mirrors Laravel getNaAttribute / getPrioAttribute). */
    private List<LecturerTimeResponse> notAvailable;
    private List<LecturerTimeResponse> priority;

    public static LecturerResponse fromEntity(Lecturer l,
                                              List<LecturerTimeResponse> notAvailable,
                                              List<LecturerTimeResponse> priority) {
        return LecturerResponse.builder()
                .id(l.getId())
                .nik(l.getNik())
                .name(l.getName())
                .homeBase(l.getHomeBase())
                .alias(l.getAlias())
                .notAvailable(notAvailable)
                .priority(priority)
                .build();
    }
}
```

### 8.5 ADD `lecturer/LecturerService.java`
```java
package com.timetablingapp.lecturer;

import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.DuplicateResourceException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.lecturer.time.LecturerTimeNARepository;
import com.timetablingapp.lecturer.time.LecturerTimeResponse;
import com.timetablingapp.lecturer.time.LecturerTimeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LecturerService implements BaseCrudService<LecturerResponse, LecturerRequest, Integer> {

    private final LecturerRepository lecturerRepository;
    private final LecturerTimeNARepository timeRepository;

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
    public LecturerResponse create(LecturerRequest req) {
        if (lecturerRepository.existsByNik(req.getNik())) {
            throw new DuplicateResourceException("Lecturer", "nik", req.getNik());
        }
        Lecturer l = new Lecturer();
        apply(l, req);
        Lecturer saved = lecturerRepository.save(l);
        // TODO Phase 7: validateLockRepository.lock();
        return toResponse(saved);
    }

    @Override
    @Transactional
    public LecturerResponse update(Integer id, LecturerRequest req) {
        Lecturer l = getOrThrow(id);
        if (!l.getNik().equals(req.getNik()) && lecturerRepository.existsByNik(req.getNik())) {
            throw new DuplicateResourceException("Lecturer", "nik", req.getNik());
        }
        apply(l, req);
        Lecturer saved = lecturerRepository.save(l);
        // TODO Phase 7: validateLockRepository.lock();
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
        // Laravel also blocks when an ActivityConstraint references this lecturer's nik:
        // TODO Phase 5: if activityConstraintRepository.existsByTypeAndValue("Lecturer", l.getNik())
        //               -> throw BadRequestException(...)
        lecturerRepository.delete(l);
    }

    // ---- helpers -------------------------------------------------------------

    private void apply(Lecturer l, LecturerRequest req) {
        l.setNik(req.getNik());
        l.setName(req.getName());
        l.setHomeBase(req.getHomeBase());
        l.setAlias(req.getAlias());
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
```
> Add `import com.timetablingapp.common.exception.BadRequestException;` at the top (kept out of the snippet header for brevity).

### 8.6 ADD `lecturer/LecturerController.java`
```java
package com.timetablingapp.lecturer;

import com.timetablingapp.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lecturers")
@RequiredArgsConstructor
public class LecturerController {

    private final LecturerService service;

    @GetMapping
    public ResponseEntity<List<LecturerResponse>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LecturerResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<LecturerResponse> create(@Valid @RequestBody LecturerRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LecturerResponse> update(@PathVariable Integer id,
                                                   @Valid @RequestBody LecturerRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(MessageResponse.success("Lecturer deleted successfully"));
    }

    // NOTE: Excel endpoints (export, export-time, import, import-time) added in Phase 9.
}
```

---

## 9. Endpoint Summary

| Method | Path | Body / Query | Auth |
|--------|------|--------------|------|
| GET | `/api/room-types` | — | authenticated |
| GET | `/api/room-types/{id}` | — | authenticated |
| POST | `/api/room-types` | `RoomTypeRequest` | authenticated |
| PUT | `/api/room-types/{id}` | `RoomTypeRequest` | authenticated |
| DELETE | `/api/room-types/{id}` | — | authenticated |
| GET | `/api/rooms` | — | authenticated |
| GET | `/api/rooms/{id}` | — | authenticated |
| POST | `/api/rooms` | `RoomRequest` (+ availabilities) | authenticated |
| PUT | `/api/rooms/{id}` | `RoomRequest` | authenticated |
| DELETE | `/api/rooms/{id}` | — | authenticated |
| GET | `/api/room-availables` | `?roomId=` | authenticated |
| GET | `/api/room-availables/{id}` | — | authenticated |
| POST | `/api/room-availables` | `RoomAvailableRequest` | authenticated |
| PUT | `/api/room-availables/{id}` | `RoomAvailableRequest` | authenticated |
| DELETE | `/api/room-availables/{id}` | — | authenticated |
| GET | `/api/lecturers` | — | authenticated |
| GET | `/api/lecturers/{id}` | — | authenticated |
| POST | `/api/lecturers` | `LecturerRequest` | authenticated |
| PUT | `/api/lecturers/{id}` | `LecturerRequest` | authenticated |
| DELETE | `/api/lecturers/{id}` | — | authenticated |
| GET | `/api/lecturer-times` | `?lecturerId=` | authenticated |
| GET | `/api/lecturer-times/{id}` | — | authenticated |
| POST | `/api/lecturer-times` | `LecturerTimeRequest` | authenticated |
| PUT | `/api/lecturer-times/{id}` | `LecturerTimeRequest` | authenticated |
| DELETE | `/api/lecturer-times/{id}` | — | authenticated |

All are covered by the existing `SecurityConfig` (`anyRequest().authenticated()`), so **`SecurityConfig.java` needs no edit.**

---

## 10. Build Order (dependency-safe)

Build/compile bottom-up to avoid dangling references:

1. `room/type/*` (no dependencies)
2. `lecturer/time/LecturerTimeType` + `LecturerTimeTypeConverter` (no dependencies)
3. `lecturer/Lecturer` (needed by `LecturerTimeNA`)
4. `lecturer/time/*` (rest — depends on `Lecturer`)
5. `lecturer/*` (rest — depends on `LecturerTimeNA`)
6. `room/Room` (depends on `RoomType`)
7. `room/available/*` (depends on `Room`)
8. `room/*` (rest — depends on `RoomType` + `RoomAvailable`)

> Note the intentional two-way package reference: `RoomTypeService` references `RoomRepository` (delete guard) and `RoomService` references `RoomTypeRepository`. Spring resolves this fine at runtime; just make sure both classes exist before the first `./gradlew build`.

---

## 11. Verification Checklist

Run after implementation:

```bash
cd new/timetabling-backend
./gradlew clean build          # compiles + schema-validates on context load if a DB is wired
./gradlew bootRun              # smoke test
```

- [ ] `ddl-auto=validate` passes against `room_types`, `rooms`, `room_availables`, `lecturers`, `lecturer_time_n_as` (this catches the `home_base` / `virtual` / `type` mapping mistakes early).
- [ ] RoomType CRUD works; deleting a type still used by a room returns **400** with a clear message.
- [ ] Room CRUD works; `parentRoomId=0` is stored as `NULL`; child `childIds` populated in the response; `availabilities` persisted and echoed back.
- [ ] Deleting a room that has sub-rooms returns **400**.
- [ ] RoomAvailable CRUD works and `?roomId=` filter returns only that room's windows, ordered by `day`.
- [ ] Lecturer CRUD works; duplicate `nik` returns **409** (`DuplicateResourceException`); `homeBase` required (**400** if missing).
- [ ] `GET /api/lecturers/{id}` returns `notAvailable[]` and `priority[]` correctly split by type.
- [ ] LecturerTimeNA CRUD works; the `type` column in the DB reads exactly `"Not-Available"` / `"Priority"` (verify with a raw `SELECT type FROM lecturer_time_n_as`), **not** `"NOT_AVAILABLE"`.
- [ ] Deleting a lecturer with existing time entries returns **400**.

---

## 12. Deferred Hooks (do NOT implement in Phase 4)

These legacy behaviors touch tables owned by later phases. Leave them as the `// TODO` comments shown above so Phase 5/6/7 can wire them in:

| Hook | Legacy source | Owning phase |
|------|---------------|--------------|
| `validateLockRepository.lock()` after room/lecturer mutation | `RoomController`, `LecturerController` | Phase 7 |
| Create `Slot` rows per `Time` on room create | `RoomController@store/insert` | Phase 7 |
| Cascade-delete `slots` + `slot_acts` on room delete | `RoomController@destroy` | Phase 7 |
| Block room delete if a `Result` uses it | `RoomController@destroy` | Phase 6 |
| Block lecturer delete if an `ActivityConstraint` references its `nik` | `LecturerController@destroy` | Phase 5 |
| Excel import/export endpoints | `RoomExcel`, `LecturerExcel`, `LecturerExcelTime` | Phase 9 |

---

*End of Phase 4 plan. Ready to execute on approval.*
