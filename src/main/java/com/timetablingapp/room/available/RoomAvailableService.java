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
    public RoomAvailableResponse create(RoomAvailableRequest request) {
        RoomAvailable ra = new RoomAvailable();
        apply(ra, request);
        return RoomAvailableResponse.fromEntity(repository.save(ra));
    }

    @Override
    @Transactional
    public RoomAvailableResponse update(Integer id, RoomAvailableRequest request) {
        RoomAvailable ra = getOrThrow(id);
        apply(ra, request);
        return RoomAvailableResponse.fromEntity(repository.save(ra));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        repository.delete(getOrThrow(id));
    }

    private void apply(RoomAvailable ra, RoomAvailableRequest request) {
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", request.getRoomId()));
        ra.setRoom(room);
        ra.setDay(request.getDay());
        ra.setStartTime(request.getStartTime());
        ra.setEndTime(request.getEndTime());
    }

    private RoomAvailable getOrThrow(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoomAvailable", "id", id));
    }
}
