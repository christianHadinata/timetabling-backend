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
    public RoomTypeResponse create(RoomTypeRequest request) {
        RoomType roomType = new RoomType();
        roomType.setName(request.getName());
        return RoomTypeResponse.fromEntity(roomTypeRepository.save(roomType));
    }

    @Override
    @Transactional
    public RoomTypeResponse update(Integer id, RoomTypeRequest request) {
        RoomType roomType = getOrThrow(id);
        roomType.setName(request.getName());
        return RoomTypeResponse.fromEntity(roomTypeRepository.save(roomType));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        RoomType roomType = getOrThrow(id);
        // Laravel RoomTypeController@destroy: block deletion if any room uses this type
        if (roomRepository.existsByRoomType_Id(id)) {
            throw new BadRequestException(
                "Cannot delete room type: it is still used by one or more rooms.");
        }
        roomTypeRepository.delete(roomType);
    }

    private RoomType getOrThrow(Integer id) {
        return roomTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", "id", id));
    }
}
