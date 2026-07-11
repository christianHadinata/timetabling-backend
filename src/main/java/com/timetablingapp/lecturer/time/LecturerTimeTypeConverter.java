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
