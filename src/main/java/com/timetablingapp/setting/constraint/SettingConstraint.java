package com.timetablingapp.setting.constraint;

import com.timetablingapp.common.base.BaseEntity;
import com.timetablingapp.setting.Setting;
import com.timetablingapp.setting.SettingableType;
import com.timetablingapp.setting.SettingableTypeConverter;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "setting_constraints",
       uniqueConstraints = @UniqueConstraint(
           name = "setting_constraint_const",
           columnNames = {"setting_id", "settingable_value", "settingable_type"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SettingConstraint extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "setting_id", nullable = false)
    private Setting setting;

    @Column(name = "settingable_value", nullable = false)
    private String value;

    @Convert(converter = SettingableTypeConverter.class)
    @Column(name = "settingable_type", nullable = false)
    private SettingableType type;
}
