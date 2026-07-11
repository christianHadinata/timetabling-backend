package com.timetablingapp.course.constraint;

import com.timetablingapp.course.Course;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "course_constraints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CourseConstraint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "with_course_id")
    private Course withCourse;

    @Column(name = "with_semester")
    private Integer withSemester;
}
