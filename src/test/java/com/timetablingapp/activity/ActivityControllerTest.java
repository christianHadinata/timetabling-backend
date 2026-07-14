package com.timetablingapp.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timetablingapp.support.SecurityTestConfig;
import com.timetablingapp.support.WithMockJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityController.class)
@Import(SecurityTestConfig.class)
@ActiveProfiles("test")
class ActivityControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @MockitoBean ActivityService activityService;
    @MockitoBean ActivityExcelService activityExcelService;

    @Test
    void getAll_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/api/activities")).andExpect(status().isUnauthorized());
    }

    @Test
    void getAll_authenticated_returns200() throws Exception {
        when(activityService.findAllByFacultyAndSemester(isNull(), eq(1))).thenReturn(List.of());
        mvc.perform(get("/api/activities").param("semesterId", "1").with(WithMockJwt.jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void create_invalidBody_returns422() throws Exception {
        mvc.perform(post("/api/activities")
                        .with(WithMockJwt.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_validBody_echoesConstraintLists() throws Exception {
        ActivityRequest req = new ActivityRequest();
        req.setCourseCode("MK101");
        req.setCourseClass("A");
        req.setCourseSession(1);
        req.setDuration(2);
        req.setQuota(40);
        req.setActivityTypeId(1);
        req.setLecturerNiks(List.of("123456"));
        req.setRoomIds(List.of(1, 2));
        req.setRoomTypeIds(List.of(1));

        when(activityService.create(any())).thenReturn(
                ActivityResponse.builder()
                        .id(1)
                        .courseCode("MK101")
                        .lecturerNiks(List.of("123456"))
                        .roomIds(List.of(1, 2))
                        .roomTypeIds(List.of(1))
                        .build());

        mvc.perform(post("/api/activities")
                        .with(WithMockJwt.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lecturerNiks[0]").value("123456"))
                .andExpect(jsonPath("$.roomIds[1]").value(2));
    }

    @Test
    void getBySemester_routesToFacultyAndSemesterFilteredMethod() throws Exception {
        when(activityService.findAllByFacultyAndSemester(isNull(), eq(3))).thenReturn(List.of());
        mvc.perform(get("/api/activities/semester/3").with(WithMockJwt.jwt()))
                .andExpect(status().isOk());
    }
}
