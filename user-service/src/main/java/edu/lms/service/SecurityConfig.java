package edu.lms.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server security for user-service.
 *
 * Every request except the public ones below must carry a bearer token issued
 * by Keycloak. The token is verified against the realm's JWKS - this service
 * never sees a password and never calls Keycloak to check a token, it just
 * verifies the signature.
 *
 * Method-level rules (@PreAuthorize) live on the controllers; this class only
 * decides what is public and how roles are read out of the token.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // turns on @PreAuthorize
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // No cookies or sessions: every request authenticates itself with its
            // own token, which is what lets us scale horizontally.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // CSRF protects cookie-based sessions. There are none here.
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                // --- public: you cannot have a token yet when you call these ---
                .requestMatchers(HttpMethod.POST, "/api/users/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/users/password-reminder").permitAll()
                // avatars are public by the team's API contract
                .requestMatchers(HttpMethod.GET, "/api/users/avatar/**").permitAll()
                // health and the profile label, so the gateway and Gatling can probe
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                // Spring Boot re-dispatches unhandled exceptions to /error.
                // Without this, every 500 comes back to the caller as a 401,
                // which hides the real failure.
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/profiles/whoami").permitAll()

                // --- everything else needs a valid token ---
                .anyRequest().authenticated()
            )

            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakRoleConverter()))
            );

        return http.build();
    }
}
