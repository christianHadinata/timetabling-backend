package com.timetablingapp.course;

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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseController.class)
@Import(SecurityTestConfig.class)
@ActiveProfiles("test")
class CourseControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @MockitoBean CourseService courseService;
    @MockitoBean CourseExcelService courseExcelService;   // controller ctor-injects it

    @Test
    void getAll_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
    }

    @Test
    void getAll_authenticated_returns200() throws Exception {
        when(courseService.findAllByFaculty(any())).thenReturn(List.of());
        mvc.perform(get("/api/courses").with(WithMockJwt.jwt())).andExpect(status().isOk());
    }

    @Test
    void create_invalidBody_returns422() throws Exception {
        mvc.perform(post("/api/courses")
                        .with(WithMockJwt.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))               // missing required fields → @Valid fails
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_validBody_returns201() throws Exception {
        CourseRequest req = new CourseRequest("MK101", "Algoritma", CourseType.Wajib, 1, null, 1);
        when(courseService.create(any())).thenReturn(
                CourseResponse.builder().id(1).code("MK101").name("Algoritma").build());

        mvc.perform(post("/api/courses")
                        .with(WithMockJwt.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }
}
