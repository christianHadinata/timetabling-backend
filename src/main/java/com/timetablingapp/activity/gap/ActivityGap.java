package com.timetablingapp.activity.gap;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "activity_gaps")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityGap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_act", nullable = false)
    private Integer activityId;

    @Column(name = "with_id_act", nullable = false)
    private Integer withActivityId;

    @Column(name = "min_gap", nullable = false)
    private Integer minGap;
}
