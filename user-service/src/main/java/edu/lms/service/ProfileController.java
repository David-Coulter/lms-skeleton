package edu.lms.service;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping("/profiles")
    public List<Profile> list() {
        return service.findAll();
    }

    @GetMapping("/profiles/{id}")
    public ResponseEntity<Profile> get(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/directory")
    public List<Profile> directory() {
        return service.findDirectory();
    }

    @GetMapping("/teachers")
    public List<Profile> teachers() {
        return service.findTeachers();
    }

    @GetMapping("/teachers/{username}")
    public ResponseEntity<Profile> teacher(@PathVariable String username) {
        return service.findTeacher(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/profiles")
    public ResponseEntity<Profile> create(@Valid @RequestBody Profile profile) {
        Profile saved = service.create(profile);
        return ResponseEntity.created(URI.create("/api/users/profiles/" + saved.getId())).body(saved);
    }

    @PutMapping("/profiles/{id}")
    public ResponseEntity<Profile> update(@PathVariable Long id, @Valid @RequestBody Profile profile) {
        return service.update(id, profile)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

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
