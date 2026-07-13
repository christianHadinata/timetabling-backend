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

import java.util.ArrayList;
import java.util.List;

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
