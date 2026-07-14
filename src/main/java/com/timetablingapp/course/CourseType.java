package com.timetablingapp.course;

/**
 * Course type enum.
 * Values match the database strings exactly.
 * Laravel default: "Pilihan"
 */
public enum CourseType {
    Wajib,
    Pilihan;

    /**
     * Map a spreadsheet label ("Wajib"/"Pilihan", any case) to the enum.
     * Defaults to Pilihan when blank/unknown, matching the Laravel default.
     */
    public static CourseType fromLabel(String label) {
        if (label != null) {
            String v = label.trim();
            for (CourseType t : values()) {
                if (t.name().equalsIgnoreCase(v)) return t;
            }
        }
        return Pilihan;
    }
}
