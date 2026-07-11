package com.timetablingapp.activity.constraint;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ConstraintTypeConverter implements AttributeConverter<ConstraintType, String> {

    @Override
    public String convertToDatabaseColumn(ConstraintType attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public ConstraintType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ConstraintType.fromDbValue(dbData);
    }
}
