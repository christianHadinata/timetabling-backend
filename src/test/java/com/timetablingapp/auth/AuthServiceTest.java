package com.timetablingapp.auth;

import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.config.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AuthService authService;

    private User adminUser() {
        User user = new User();
        user.setId(1L);
        user.setName("Admin");
        user.setEmail("admin@x.com");
        user.setPassword("$2a$hashed");
        user.setFaculty(null);
        return user;
    }

    @Test
    void login_success_returnsTokenAndUser() {
        User user = adminUser();
        when(userRepository.findByEmail("admin@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$2a$hashed")).thenReturn(true);
        when(jwtService.generateToken(1L, "admin@x.com", "Admin", null)).thenReturn("jwt-123");

        AuthResponse res = authService.login(new LoginRequest("admin@x.com", "pw"));

        assertEquals("jwt-123", res.getToken());
        assertEquals("Bearer", res.getTokenType());
        assertNotNull(res.getUser());
        assertEquals("admin@x.com", res.getUser().getEmail());
    }

    @Test
    void login_wrongPassword_throwsBadRequest() {
        User user = adminUser();
        when(userRepository.findByEmail("admin@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "$2a$hashed")).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> authService.login(new LoginRequest("admin@x.com", "bad")));
        verify(jwtService, never()).generateToken(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void login_unknownUser_throwsBadRequest() {
        when(userRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> authService.login(new LoginRequest("ghost@x.com", "pw")));
        verify(jwtService, never()).generateToken(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void getCurrentUser_found_returnsUserResponse() {
        User user = adminUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse res = authService.getCurrentUser(1L);

        assertEquals("admin@x.com", res.getEmail());
    }

    @Test
    void getCurrentUser_notFound_throwsResourceNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.getCurrentUser(99L));
    }
}
