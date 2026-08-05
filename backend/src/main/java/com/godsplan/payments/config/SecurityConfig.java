package com.godsplan.payments.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService staffUsers(SecurityProperties properties, PasswordEncoder encoder) {
        if (properties.username() == null || properties.username().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            throw new IllegalStateException("ADMIN_USERNAME and ADMIN_PASSWORD must be configured");
        }
        var staff = User.withUsername(properties.username().trim().toLowerCase())
                .password(encoder.encode(properties.password()))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(staff);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .ignoringRequestMatchers("/api/v1/payments/**", "/api/v1/exchange-rates/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/api/v1/auth/csrf", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/customers/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> writeJson(response,
                                HttpServletResponse.SC_OK, "{\"authenticated\":true}"))
                        .failureHandler((request, response, exception) -> writeJson(response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "{\"code\":\"UNAUTHORIZED\",\"message\":\"Invalid email or password\"}"))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeJson(response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "{\"code\":\"UNAUTHORIZED\",\"message\":\"Staff authentication is required\"}"))
                        .accessDeniedHandler((request, response, exception) -> writeJson(response,
                                HttpServletResponse.SC_FORBIDDEN,
                                "{\"code\":\"ACCESS_DENIED\",\"message\":\"Administrator or staff access is required\"}")));
        return http.build();
    }

    private void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(body);
    }
}

