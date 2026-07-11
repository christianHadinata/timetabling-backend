package com.timetablingapp.jurusan;

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
public class JurusanRequest {

    @NotBlank(message = "Jurusan name is required")
    private String name;

    private String faculty;

    @NotNull(message = "Jenjang is required")
    private Jenjang jenjang;

    private Integer color;
}
