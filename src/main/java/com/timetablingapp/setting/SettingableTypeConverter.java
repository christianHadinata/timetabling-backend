package com.timetablingapp.setting;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class SettingableTypeConverter implements AttributeConverter<SettingableType, String> {

    @Override
    public String convertToDatabaseColumn(SettingableType attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public SettingableType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SettingableType.fromDbValue(dbData);
    }
}
