package edu.lms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private static final String SELF_REGISTRATION_ROLE = "student";

    private final ProfileRepository repository;
    private final KeycloakAdminClient keycloak;

    public RegistrationService(ProfileRepository repository, KeycloakAdminClient keycloak) {
        this.repository = repository;
        this.keycloak = keycloak;
    }

    @Transactional
    public Profile register(RegistrationRequest req) {
        String keycloakId = keycloak.createUser(
                req.username(),
                req.email(),
                req.firstName(),
                req.lastName(),
                req.password(),
                SELF_REGISTRATION_ROLE);

        try {
            Profile p = new Profile();
            p.setUsername(req.username());
            p.setEmail(req.email());
            p.setFirstName(req.firstName());
            p.setLastName(req.lastName());
            p.setFullName(req.firstName() + " " + req.lastName());
            p.setRole(SELF_REGISTRATION_ROLE);
            p.setGradeLevel(req.gradeLevel());
            p.setPreferences(defaultPreferences());

            Profile saved = repository.save(p);
            log.info("registered {} (keycloak id {})", saved.getUsername(), keycloakId);
            return saved;

        } catch (RuntimeException e) {

            log.warn("profile insert failed for {}, removing the Keycloak account", req.username(), e);
            keycloak.deleteUser(keycloakId);
            throw e;
        }
    }

    public void sendReminder(String usernameOrEmail) {
        boolean sent = keycloak.sendPasswordResetEmail(usernameOrEmail);
        if (!sent) {
            log.info("password reminder for unknown account '{}' - nothing sent", usernameOrEmail);
        }
    }

    private Preferences defaultPreferences() {
        Preferences prefs = new Preferences();
        prefs.setTimezone("Pacific Time (PT)");
        prefs.setNotifications("Email & Push");
        return prefs;
    }
}
