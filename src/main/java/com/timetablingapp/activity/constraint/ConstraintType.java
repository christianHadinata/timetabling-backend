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
