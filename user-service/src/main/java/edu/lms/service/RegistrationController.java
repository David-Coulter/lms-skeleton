package edu.lms.service;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class RegistrationController {

    private final RegistrationService service;

    public RegistrationController(RegistrationService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public ResponseEntity<Profile> register(@Valid @RequestBody RegistrationRequest req) {
        Profile saved = service.register(req);
        return ResponseEntity
                .created(URI.create("/api/users/profiles/" + saved.getId()))
                .body(saved);
    }

    @PostMapping("/password-reminder")
    public ResponseEntity<Map<String, String>> passwordReminder(
            @Valid @RequestBody PasswordReminderRequest req) {

        service.sendReminder(req.usernameOrEmail());

        return ResponseEntity.accepted().body(Map.of(
                "message", "If that account exists, a reset email has been sent."));
    }

    @ExceptionHandler(UsernameTakenException.class)
    public ResponseEntity<Map<String, String>> handleUsernameTaken(UsernameTakenException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "username or email already registered"));
    }
}
