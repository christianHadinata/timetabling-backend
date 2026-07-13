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

    SettingableType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static SettingableType fromDbValue(String v) {
        return Arrays.stream(values())
                .filter(t -> t.dbValue.equals(v))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown settingable_type: " + v));
    }
}
