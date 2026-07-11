package com.timetablingapp.jurusan.konsentrasi;

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
public class KonsentrasiResponse {

    private Integer id;
    private Integer jurusanId;
    private String konsentrasi;

    /**
     * Factory method to convert a Konsentrasi entity to KonsentrasiResponse.
     */
    public static KonsentrasiResponse fromEntity(Konsentrasi entity) {
        return KonsentrasiResponse.builder()
                .id(entity.getId())
                .jurusanId(entity.getJurusan().getId())
                .konsentrasi(entity.getKonsentrasi())
                .build();
    }
}
