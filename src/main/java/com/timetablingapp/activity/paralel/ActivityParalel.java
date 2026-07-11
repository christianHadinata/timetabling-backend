package com.timetablingapp.activity.paralel;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "activity_paralels")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityParalel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_act", nullable = false)
    private Integer activityId;

    @Column(name = "with_id_act", nullable = false)
    private Integer withActivityId;
}
