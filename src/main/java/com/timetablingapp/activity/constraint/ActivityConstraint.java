package com.timetablingapp.activity.constraint;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "activity_constraints")
@SQLDelete(sql = "UPDATE activity_constraints SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ActivityConstraint extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Convert(converter = ConstraintTypeConverter.class)
    @Column(nullable = false)
    private ConstraintType type;

    @Column(length = 100, nullable = false)
    private String value;          // lecturer NIK, or room id, or room-type id (as string)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;
}
