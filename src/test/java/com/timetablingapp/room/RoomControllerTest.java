package com.timetablingapp.room;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
@Import(SecurityTestConfig.class)
@ActiveProfiles("test")
class RoomControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @MockitoBean RoomService service;
    @MockitoBean RoomExcelService roomExcelService;

    @Test
    void getAll_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/api/rooms")).andExpect(status().isUnauthorized());
    }

    @Test
    void getAll_authenticated_returns200() throws Exception {
        when(service.findAll()).thenReturn(List.of());
        mvc.perform(get("/api/rooms").with(WithMockJwt.jwt())).andExpect(status().isOk());
    }

    @Test
    void create_invalidBody_returns422() throws Exception {
        mvc.perform(post("/api/rooms")
                        .with(WithMockJwt.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_withParentRoom_mapsParentId() throws Exception {
        RoomRequest req = new RoomRequest(
                "R101", "Room 101", "FASILKOM", "Kampus A", "Gedung A", "1",
                50, 10, 1, null, List.of());

        when(service.create(any())).thenReturn(
                RoomResponse.builder().id(1).roomCode("R101").parentRoomId(10).build());

        mvc.perform(post("/api/rooms")
                        .with(WithMockJwt.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentRoomId").value(10));
    }
}
