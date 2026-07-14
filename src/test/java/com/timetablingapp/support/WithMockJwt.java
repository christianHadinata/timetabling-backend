package com.timetablingapp.support;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

/**
 * Builds a MockMvc {@link RequestPostProcessor} that stamps an Authentication shaped exactly
 * like {@code JwtAuthenticationFilter}'s in production: principal = email, credentials = userId
 * (a {@code Long}), role derived from faculty. Needed because controllers such as
 * AuthController#me() cast {@code auth.getCredentials()} to {@code Long} directly —
 * {@code @WithMockUser}'s default String credentials would blow up that cast instead of
 * exercising the real code path.
 */
public final class WithMockJwt {

    private WithMockJwt() {
    }

    public static RequestPostProcessor jwt() {
        return jwt(1L, "admin@example.com", "");
    }

    /** Blank/empty faculty = admin (mirrors User.isAdmin()). */
    public static RequestPostProcessor jwt(long userId, String email, String faculty) {
        String role = (faculty == null || faculty.isBlank()) ? "ROLE_ADMIN" : "ROLE_FACULTY_USER";
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(email, userId, authorities);
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }
}
