package com.timetablingapp.result;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import com.timetablingapp.room.Room;
import com.timetablingapp.semester.Semester;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalTime;

@Entity
@Table(name = "results")
@SQLDelete(sql = "UPDATE results SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Result extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    /** Nullable — a result may exist before a room is assigned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(columnDefinition = "TEXT")
    private String day;             // "1".."6", nullable

    @Column(name = "start_time")
    private LocalTime startTime;    // nullable

    @Column(name = "end_time")
    private LocalTime endTime;      // nullable

    /** Laravel: tinyInteger default 1. */
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Boolean valid = true;
}
