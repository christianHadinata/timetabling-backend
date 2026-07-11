package com.timetablingapp.course;

import com.timetablingapp.jurusan.JurusanResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseInfoResponse {

    private Integer id;
    private String code;
    private String name;
    private CourseType type;
    private Integer tingkat;
    private String konsentrasi;
    private JurusanResponse jurusan;
    private String color;

    // TODO: Phase 5 — Add activity summary fields:
    // private List<ActivitySummary> activities;

    /**
     * Factory method from Course entity.
     */
    public static CourseInfoResponse fromEntity(Course course) {
        CourseInfoResponse.CourseInfoResponseBuilder builder = CourseInfoResponse.builder()
                .id(course.getId())
                .code(course.getCode())
                .name(course.getName())
                .type(course.getType())
                .tingkat(course.getTingkat())
                .konsentrasi(course.getKonsentrasi())
                .color(course.getColor());

        if (course.getJurusan() != null) {
            builder.jurusan(JurusanResponse.fromEntity(course.getJurusan()));
        }

        return builder.build();
    }
}
