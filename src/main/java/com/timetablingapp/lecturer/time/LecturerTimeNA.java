package com.timetablingapp.lecturer.time;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import com.timetablingapp.lecturer.Lecturer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalTime;

@Entity
@Table(name = "lecturer_time_n_as")
@SQLDelete(sql = "UPDATE lecturer_time_n_as SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LecturerTimeNA extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id", nullable = false)
    private Lecturer lecturer;

    @Column(nullable = false)
    private Integer day;               // 1..6

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Convert(converter = LecturerTimeTypeConverter.class)
    @Column(length = 100, nullable = false)
    private LecturerTimeType type;
}
