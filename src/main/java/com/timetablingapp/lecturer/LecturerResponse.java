package com.timetablingapp.lecturer;

import com.timetablingapp.lecturer.time.LecturerTimeResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LecturerResponse {

    private Integer id;
    private String nik;
    private String name;
    private Integer homeBase;
    private String alias;

    /** Times grouped by LecturerTimeType (mirrors Laravel getNaAttribute / getPrioAttribute). */
    private List<LecturerTimeResponse> notAvailable;
    private List<LecturerTimeResponse> priority;

    public static LecturerResponse fromEntity(Lecturer l,
                                              List<LecturerTimeResponse> notAvailable,
                                              List<LecturerTimeResponse> priority) {
        return LecturerResponse.builder()
                .id(l.getId())
                .nik(l.getNik())
                .name(l.getName())
                .homeBase(l.getHomeBase())
                .alias(l.getAlias())
                .notAvailable(notAvailable)
                .priority(priority)
                .build();
    }
}
