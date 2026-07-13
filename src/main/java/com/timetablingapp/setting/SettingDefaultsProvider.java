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
import java.util.stream.Stream;

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
            case ROOM_TYPE -> ids(roomTypeRepository.findAll().stream().map(rt -> rt.getId()));
            case ROOM_OWNER -> ids(roomRepository.findAll().stream().map(r -> r.getId()));
            case ACTIVITY_TYPE -> ids(activityTypeRepository.findAll().stream().map(at -> at.getId()));
            case CUSTOM_ACTIVITY -> ids(activityRepository.findAll().stream().map(a -> a.getId()));
            case WAKTU -> IntStream.rangeClosed(7, 23).mapToObj(String::valueOf).toList();   // hours 7..23
            case HARI -> IntStream.rangeClosed(1, 6).mapToObj(String::valueOf).toList();     // Mon..Sat
            case JURUSAN -> ids(jurusanRepository.findAll().stream().map(j -> j.getId()));
        };
    }

    private List<String> ids(Stream<Integer> idStream) {
        return idStream.map(String::valueOf).toList();
    }
}
