package com.timetablingapp.auth;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityTestConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @MockitoBean AuthService authService;

    @Test
    void login_valid_returns200WithToken() throws Exception {
        when(authService.login(any())).thenReturn(
                AuthResponse.builder().token("jwt-123").tokenType("Bearer").user(null).build());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("a@x.com", "pw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-123"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_blankEmail_returns422() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("", "pw"))))
                .andExpect(status().isUnprocessableEntity());   // @Valid @NotBlank → GlobalExceptionHandler
    }

    @Test
    void me_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void me_authenticated_returns200() throws Exception {
        when(authService.getCurrentUser(anyLong())).thenReturn(
                UserResponse.builder().id(7L).email("admin@x.com").admin(true).build());

        mvc.perform(get("/api/auth/me").with(WithMockJwt.jwt(7L, "admin@x.com", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@x.com"));
    }
}
