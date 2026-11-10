package edu.lms.service;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps Keycloak realm roles onto Spring Security authorities.
 *
 * WHY THIS EXISTS: Keycloak puts realm roles in a nested claim,
 *
 *     "realm_access": { "roles": ["student", "offline_access", ...] }
 *
 * and Spring Security's default converter only reads the flat "scope" claim.
 * Without this, a token from a user with the teacher role arrives with no
 * authorities at all, @PreAuthorize("hasRole('TEACHER')") denies everything,
 * and the failure is silent - a 403 with nothing in the logs explaining why.
 *
 * Spring's hasRole('X') looks for an authority literally named "ROLE_X", so
 * roles are upper-cased and prefixed here.
 *
 * Every service in LearningHub should use this same class so roles behave
 * identically everywhere.
 */
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";

    /** Keycloak adds these to every token; they are not application roles. */
    private static final Set<String> IGNORED = Set.of(
            "offline_access", "uma_authorization", "default-roles-learninghub");

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getClaimAsString("preferred_username"));
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        Collection<GrantedAuthority> scopes = scopeAuthorities(jwt);
        Collection<GrantedAuthority> roles = realmRoles(jwt);
        return Stream.concat(scopes.stream(), roles.stream()).collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
        if (realmAccess == null || !(realmAccess.get(ROLES) instanceof List<?> raw)) {
            return List.of();
        }
        return ((List<String>) raw).stream()
                .filter(r -> !IGNORED.contains(r))
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                .collect(Collectors.toList());
    }

    /** Keep the default scope-based authorities too, so SCOPE_* still works. */
    private Collection<GrantedAuthority> scopeAuthorities(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return List.of(scope.split(" ")).stream()
                .map(s -> (GrantedAuthority) new SimpleGrantedAuthority("SCOPE_" + s))
                .collect(Collectors.toList());
    }
}
