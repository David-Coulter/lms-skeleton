package edu.lms.service;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Profile CRUD and the directory / teacher lookups.
 *
 * Authorization is declared per endpoint below. The rules follow the team's
 * API contract: admins administer profiles, staff look people up in the
 * directory, and everyone can see who teaches.
 *
 * These run on the authorities produced by KeycloakRoleConverter, so
 * hasRole('ADMIN') matches the Keycloak realm role "admin".
 */
@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    // Admin surface: the full profile list is account administration.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/profiles")
    public List<Profile> list() {
        return service.findAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/profiles/{id}")
    public ResponseEntity<Profile> get(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Staff look people up; students do not browse other students.
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/directory")
    public List<Profile> directory() {
        return service.findDirectory();
    }

    // Every signed-in user can see who teaches.
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/teachers")
    public List<Profile> teachers() {
        return service.findTeachers();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/teachers/{username}")
    public ResponseEntity<Profile> teacher(@PathVariable String username) {
        return service.findTeacher(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/profiles")
    public ResponseEntity<Profile> create(@Valid @RequestBody Profile profile) {
        Profile saved = service.create(profile);
        return ResponseEntity.created(URI.create("/api/users/profiles/" + saved.getId())).body(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/profiles/{id}")
    public ResponseEntity<Profile> update(@PathVariable Long id, @Valid @RequestBody Profile profile) {
        return service.update(id, profile)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/profiles/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/profiles/whoami")
    public String whoami(@org.springframework.beans.factory.annotation.Value("${lms.environment-label}") String label) {
        return label;
    }


}
