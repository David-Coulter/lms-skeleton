package edu.lms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Talks to Keycloak's Admin REST API on behalf of user-service.
 *
 * Authenticates as the `user-service` confidential client using the
 * client-credentials grant; that client's service account holds the
 * realm-management roles manage-users and view-users. No end-user token is
 * involved, which is the point: registration happens before anyone has one.
 *
 * Tokens are fetched per call. That is a little wasteful but keeps this class
 * free of refresh/expiry handling, and these calls are rare.
 */
@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    private final RestClient http = RestClient.create();

    @Value("${lms.keycloak.internal-url}") private String baseUrl;   // http://keycloak:8080/auth
    @Value("${lms.keycloak.realm}")        private String realm;     // learninghub
    @Value("${lms.keycloak.client-id}")    private String clientId;  // user-service
    @Value("${lms.keycloak.client-secret}") private String clientSecret;

    // ---------------------------------------------------------------- public API

    /**
     * Creates a Keycloak account with the given realm role and a permanent password.
     *
     * @return the new Keycloak user id
     * @throws UsernameTakenException if the username or email is already in use
     */
    public String createUser(String username, String email, String firstName,
                             String lastName, String password, String role) {
        String token = serviceAccountToken();

        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", false,
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", password,
                        "temporary", false))
        );

        var response = http.post()
                .uri(baseUrl + "/admin/realms/" + realm + "/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                // Suppress the default "throw on 4xx/5xx" so a 409 can be read
                // as a normal outcome below rather than an exception.
                .onStatus(HttpStatusCode::isError, (req, res) -> { })
                .toBodilessEntity();

        if (response.getStatusCode().value() == 409) {
            throw new UsernameTakenException("username or email already registered: " + username);
        }
        if (response.getStatusCode().isError()) {
            throw new IllegalStateException("Keycloak rejected the user creation: " + response.getStatusCode());
        }

        String userId = findUserId(username, token)
                .orElseThrow(() -> new IllegalStateException("user created but not found: " + username));

        assignRealmRole(userId, role, token);
        log.info("created Keycloak user {} ({}) with role {}", username, userId, role);
        return userId;
    }

    /**
     * Asks Keycloak to email the user an "update password" action link.
     *
     * @return false when there is no such user - deliberately not an exception,
     *         so callers can return the same neutral response either way and
     *         avoid leaking which usernames exist.
     */
    public boolean sendPasswordResetEmail(String usernameOrEmail) {
        String token = serviceAccountToken();

        var userId = findUserId(usernameOrEmail, token);
        if (userId.isEmpty()) {
            log.info("password reminder requested for unknown account '{}' - no email sent", usernameOrEmail);
            return false;
        }

        http.put()
                .uri(baseUrl + "/admin/realms/" + realm + "/users/" + userId.get()
                        + "/execute-actions-email?lifespan=3600")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of("UPDATE_PASSWORD"))
                .retrieve()
                .toBodilessEntity();

        log.info("password reset email sent for {}", usernameOrEmail);
        return true;
    }

    /** Removes an account. Used to roll back a registration whose profile insert failed. */
    public void deleteUser(String userId) {
        http.delete()
                .uri(baseUrl + "/admin/realms/" + realm + "/users/" + userId)
                .header("Authorization", "Bearer " + serviceAccountToken())
                .retrieve()
                .toBodilessEntity();
        log.info("deleted Keycloak user {}", userId);
    }

    // ---------------------------------------------------------------- internals

    private String serviceAccountToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<?, ?> token = http.post()
                .uri(baseUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (token == null || token.get("access_token") == null) {
            throw new IllegalStateException("could not obtain a service-account token from Keycloak");
        }
        return token.get("access_token").toString();
    }

    @SuppressWarnings("unchecked")
    private java.util.Optional<String> findUserId(String usernameOrEmail, String token) {
        List<Map<String, Object>> found = http.get()
                .uri(baseUrl + "/admin/realms/" + realm + "/users?exact=true&username=" + usernameOrEmail)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(List.class);

        if (found == null || found.isEmpty()) {
            // not a username - try email
            found = http.get()
                    .uri(baseUrl + "/admin/realms/" + realm + "/users?exact=true&email=" + usernameOrEmail)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(List.class);
        }
        if (found == null || found.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(found.get(0).get("id").toString());
    }

    @SuppressWarnings("unchecked")
    private void assignRealmRole(String userId, String role, String token) {
        Map<String, Object> realmRole = http.get()
                .uri(baseUrl + "/admin/realms/" + realm + "/roles/" + role)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(Map.class);

        if (realmRole == null) {
            throw new IllegalStateException("no such realm role: " + role);
        }

        http.post()
                .uri(baseUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(Map.of("id", realmRole.get("id"), "name", realmRole.get("name"))))
                .retrieve()
                .toBodilessEntity();
    }
}
