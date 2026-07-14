package com.timetablingapp.room;

import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.excel.ImportLog;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.DuplicateResourceException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.room.available.RoomAvailable;
import com.timetablingapp.room.available.RoomAvailableRepository;
import com.timetablingapp.room.available.RoomAvailableRequest;
import com.timetablingapp.room.available.RoomAvailableResponse;
import com.timetablingapp.room.type.RoomType;
import com.timetablingapp.room.type.RoomTypeRepository;
import com.timetablingapp.result.ResultRepository;
import com.timetablingapp.schedule.slot.Slot;
import com.timetablingapp.schedule.slot.SlotRepository;
import com.timetablingapp.schedule.slot.act.SlotActivityRepository;
import com.timetablingapp.schedule.slot.time.Time;
import com.timetablingapp.schedule.slot.time.TimeRepository;
import com.timetablingapp.schedule.validate.ValidateLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService implements BaseCrudService<RoomResponse, RoomRequest, Integer> {

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomAvailableRepository roomAvailableRepository;
    private final ResultRepository resultRepository;
    private final TimeRepository timeRepository;
    private final SlotRepository slotRepository;
    private final SlotActivityRepository slotActivityRepository;
    private final ValidateLockService validateLockService;
    private final RoomExcelService roomExcelService;

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
    public RoomResponse create(RoomRequest request) {
        if (roomRepository.existsByRoomCode(request.getRoomCode())) {
            throw new DuplicateResourceException("Room", "roomCode", request.getRoomCode());
        }
        Room room = new Room();
        applyScalar(room, request);
        Room saved = roomRepository.save(room);
        replaceAvailabilities(saved, request.getAvailabilities());

        // A room gets one slot per Time. Mirrors RoomController@store.
        List<Slot> slots = new ArrayList<>();
        for (Time time : timeRepository.findAll()) {
            Slot slot = new Slot();
            slot.setRoom(saved);
            slot.setTime(time);
            slots.add(slot);
        }
        slotRepository.saveAll(slots);
        validateLockService.lock();
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RoomResponse update(Integer id, RoomRequest request) {
        Room room = getOrThrow(id);
        if (!room.getRoomCode().equals(request.getRoomCode())
                && roomRepository.existsByRoomCode(request.getRoomCode())) {
            throw new DuplicateResourceException("Room", "roomCode", request.getRoomCode());
        }
        applyScalar(room, request);
        Room saved = roomRepository.save(room);
        replaceAvailabilities(saved, request.getAvailabilities());

        validateLockService.lock();
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
        // Laravel RoomController@destroy guard #2: no result uses this room
        if (resultRepository.existsByRoom_Id(id)) {
            throw new BadRequestException(
                "Cannot delete room: it is used by one or more scheduled results.");
        }
        // Cascade-delete this room's slots + slot_acts (both hard-delete, FK order matters).
        slotActivityRepository.deleteBySlotRoomId(id);
        slotRepository.deleteByRoomId(id);

        roomAvailableRepository.deleteByRoom_Id(id);   // soft-delete availabilities
        roomRepository.delete(room);
        validateLockService.lock();
    }

    // ---- Excel import (mirrors RoomController@uploadExcel) --------------------

    /**
     * Two-pass import: parents first (no parent code), then children — so a child's
     * parent_room_id FK resolves. Mirrors Laravel getRoomsFromFile(file,true) then (…,false).
     * Room type is resolved by name (defaulting to the first type); unknown parent → null.
     *
     * Note: unlike UI creation, imported rooms do not get per-Time Slot rows here — this
     * mirrors the legacy uploadExcel path. Slots are (re)built by the validation engine.
     */
    public ImportLog importRooms(MultipartFile file) {
        ImportLog log = new ImportLog("room");
        List<RoomExcelService.RoomRow> rows = roomExcelService.parse(file);

        Map<String, RoomType> typeByName = roomTypeRepository.findAll().stream()
                .collect(Collectors.toMap(RoomType::getName, t -> t, (a, b) -> a));
        RoomType defaultType = roomTypeRepository.findAll().stream().findFirst().orElse(null);

        for (boolean parentPass : new boolean[]{true, false}) {
            for (RoomExcelService.RoomRow row : rows) {
                if (row.isParent() != parentPass) continue;
                String code = row.roomCode();
                if (code == null || code.isBlank()) continue;
                try {
                    Room room = new Room();
                    room.setRoomCode(code);
                    room.setName(row.name());
                    room.setUnitOwner(row.unitOwner());
                    room.setLocation(row.location());
                    room.setBuilding(row.building());
                    room.setFloor(row.floor());
                    room.setCapacity(row.capacity());
                    room.setRoomType(typeByName.getOrDefault(row.roomTypeName(), defaultType));
                    if (!row.isParent()) {
                        roomRepository.findByRoomCode(row.parentCode()).ifPresent(room::setParentRoom);
                    }
                    roomRepository.save(room);
                    log.ok(code);
                } catch (Exception e) {
                    log.fail(code, "Exception: " + e.getMessage());
                }
            }
        }
        validateLockService.lock();
        return log;
    }

    // ---- helpers -------------------------------------------------------------

    private void applyScalar(Room room, RoomRequest request) {
        RoomType type = roomTypeRepository.findById(request.getRoomTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", "id", request.getRoomTypeId()));

        room.setRoomCode(request.getRoomCode());
        room.setName(request.getName());
        room.setUnitOwner(request.getUnitOwner());
        room.setLocation(request.getLocation());
        room.setBuilding(request.getBuilding());
        room.setFloor(request.getFloor());
        room.setCapacity(request.getCapacity());
        room.setVirtual(request.getVirtual());
        room.setRoomType(type);

        // Legacy: parent_room_id == 0 means "no parent"
        Integer parentId = request.getParentRoomId();
        if (parentId == null || parentId == 0) {
            room.setParentRoom(null);
        } else {
            Room parent = roomRepository.findById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Room", "id", parentId));
            room.setParentRoom(parent);
        }
    }

    /** Clear existing availability rows and recreate from the request. */
    private void replaceAvailabilities(Room room, List<RoomAvailableRequest> requests) {
        roomAvailableRepository.deleteByRoom_Id(room.getId());
        if (requests == null) return;
        for (RoomAvailableRequest r : requests) {
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
