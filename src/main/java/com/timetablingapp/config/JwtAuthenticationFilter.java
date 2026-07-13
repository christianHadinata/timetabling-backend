package com.timetablingapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final boolean hasBearer = authHeader != null && authHeader.startsWith("Bearer ");

        // Browser EventSource can't set an Authorization header, so /api/sse/** may
        // instead carry the JWT as a query param. Scoped narrowly to that one path.
        final String queryToken = (!hasBearer && request.getRequestURI().startsWith("/api/sse/"))
                ? request.getParameter("token") : null;

        if (!hasBearer && queryToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = hasBearer ? authHeader.substring(7) : queryToken;

        try {
            if (jwtService.isTokenValid(jwt)) {
                String email = jwtService.extractEmail(jwt);
                String faculty = jwtService.extractFaculty(jwt);
                Long userId = jwtService.extractUserId(jwt);

                // Determine role: admin if faculty is null or empty
                String role = (faculty == null || faculty.isBlank())
                        ? "ROLE_ADMIN"
                        : "ROLE_FACULTY_USER";

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority(role)
                );

                // Build auth token with userId stored as principal details
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                email,    // principal
                                userId,   // credentials (store userId for easy access)
                                authorities
                        );

                authToken.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            // Token is invalid — do not set authentication, let Spring Security handle 401
            logger.debug("JWT validation failed: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
