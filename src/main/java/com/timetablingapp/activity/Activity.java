package com.timetablingapp.activity;

import com.timetablingapp.activity.type.ActivityType;
import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import com.timetablingapp.course.Course;
import com.timetablingapp.semester.Semester;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "activities")
@SQLDelete(sql = "UPDATE activities SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Activity extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    /** Non-standard FK: activities.course_code references courses.code. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_code", referencedColumnName = "code", nullable = false)
    private Course course;

    @NotBlank
    @Column(name = "course_class", length = 3, nullable = false)
    private String courseClass;

    @NotNull
    @Column(name = "course_session", nullable = false)
    private Integer courseSession;

    @NotNull
    @Column(nullable = false)
    private Integer duration;

    @NotNull
    @Column(nullable = false)
    private Integer quota;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_type_id", nullable = false)
    private ActivityType activityType;

    /**
     * Display name — mirrors Activity::getNameAttribute():
     * "{course.name} {course_code} - {course_class} ({course_session})"
     */
    @Transient
    public String getName() {
        String courseName = course != null ? course.getName() : "";
        String code = course != null ? course.getCode() : "";
        return courseName + " " + code + " - " + courseClass + " (" + courseSession + ")";
    }
}
