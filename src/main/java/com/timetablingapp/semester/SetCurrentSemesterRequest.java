package com.timetablingapp.semester;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SetCurrentSemesterRequest {

    @NotNull(message = "Semester ID is required")
    private Integer id;
}
