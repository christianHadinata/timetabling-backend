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
