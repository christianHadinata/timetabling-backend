package com.timetablingapp.course;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import com.timetablingapp.jurusan.Jurusan;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "courses")
@SQLDelete(sql = "UPDATE courses SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Course extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(nullable = false, length = 12, unique = true)
    private String code;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column
    private CourseType type;

    @Column
    private Integer tingkat;

    @Column
    private String konsentrasi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jurusan_id")
    private Jurusan jurusan;

    /**
     * Computes the display color as an HSL string based on jurusan's hue and course tingkat.
     *
     * Mirrors Laravel: Course::getColorAttribute()
     *
     * Logic:
     * - t = floor(tingkat / 2)
     * - t=0 → hsl(color,100%,95%)  (lightest)
     * - t=1 → hsl(color,100%,80%)
     * - t=2 → hsl(color,100%,65%)
     * - t=3 → hsl(color,100%,50%)  (darkest)
     */
    @Transient
    public String getColor() {
        if (jurusan == null || jurusan.getColor() == null || tingkat == null) {
            return "";
        }

        int hue = jurusan.getColor();
        int t = (int) Math.floor(tingkat / 2.0);

        return switch (t) {
            case 0 -> "hsl(" + hue + ",100%,95%)";
            case 1 -> "hsl(" + hue + ",100%,80%)";
            case 2 -> "hsl(" + hue + ",100%,65%)";
            case 3 -> "hsl(" + hue + ",100%,50%)";
            default -> "";
        };
    }

    /**
     * Computes a priority value based on jenjang and time slot.
     * Used by the genetic algorithm for slot prioritization.
     *
     * Mirrors Laravel: Course::getPriorityValue($day, $hour, $jenjang)
     *
     * S1: priority if Mon-Fri (1-5) and 7:00-17:00
     * Others: priority if Fri+ (>=5) and 7:00-21:00
     */
    @Transient
    public int getPriorityValue(int day, int hour, String jenjang) {
        if ("S1".equals(jenjang)) {
            if (day >= 1 && day <= 5 && hour >= 7 && hour <= 17) {
                return 1;
            }
        } else {
            if (day >= 5 && hour >= 7 && hour <= 21) {
                return 1;
            }
        }
        return 0;
    }
}
