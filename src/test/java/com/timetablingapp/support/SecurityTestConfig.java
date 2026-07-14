package com.timetablingapp.support;

import com.timetablingapp.config.CorsConfig;
import com.timetablingapp.config.JwtAuthenticationFilter;
import com.timetablingapp.config.JwtService;
import com.timetablingapp.config.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Pulls the real security filter chain (+ its JWT collaborators + CORS) into @WebMvcTest slices,
 * so 401/403 behavior is exercised exactly as in production rather than being auto-permitted.
 */
@TestConfiguration
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, CorsConfig.class })
public class SecurityTestConfig {
}
