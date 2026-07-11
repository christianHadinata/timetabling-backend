package com.timetablingapp.lecturer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LecturerRequest {

    @NotBlank(message = "NIK is required")
    private String nik;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Home base is required")   // NOT NULL column
    private Integer homeBase;

    private String alias;
}
