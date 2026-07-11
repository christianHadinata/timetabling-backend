package com.timetablingapp.jurusan;

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
public class JurusanResponse {

    private Integer id;
    private String name;
    private String faculty;
    private Jenjang jenjang;
    private Integer color;

    /**
     * Factory method to convert a Jurusan entity to JurusanResponse.
     */
    public static JurusanResponse fromEntity(Jurusan jurusan) {
        return JurusanResponse.builder()
                .id(jurusan.getId())
                .name(jurusan.getName())
                .faculty(jurusan.getFaculty())
                .jenjang(jurusan.getJenjang())
                .color(jurusan.getColor())
                .build();
    }
}
