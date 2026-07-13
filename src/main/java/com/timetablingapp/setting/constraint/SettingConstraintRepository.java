package com.timetablingapp.setting.constraint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettingConstraintRepository extends JpaRepository<SettingConstraint, Integer> {

    List<SettingConstraint> findBySetting_Id(Integer settingId);

    /** Hard delete (no soft delete on this table) — used on update & delete. */
    void deleteBySetting_Id(Integer settingId);
}
