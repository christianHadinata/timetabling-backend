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
public class CourseResponse {

    private Integer id;
    private String code;
    private String name;
    private CourseType type;
    private Integer tingkat;
    private String konsentrasi;
    private Integer jurusanId;
    private JurusanResponse jurusan;
    private String color;

    /**
     * Factory method to convert a Course entity to CourseResponse.
     * Includes the computed color and jurusan details.
     */
    public static CourseResponse fromEntity(Course course) {
        CourseResponse.CourseResponseBuilder builder = CourseResponse.builder()
                .id(course.getId())
                .code(course.getCode())
                .name(course.getName())
                .type(course.getType())
                .tingkat(course.getTingkat())
                .konsentrasi(course.getKonsentrasi())
                .color(course.getColor());

        if (course.getJurusan() != null) {
            builder.jurusanId(course.getJurusan().getId())
                   .jurusan(JurusanResponse.fromEntity(course.getJurusan()));
        }

        return builder.build();
    }
}
