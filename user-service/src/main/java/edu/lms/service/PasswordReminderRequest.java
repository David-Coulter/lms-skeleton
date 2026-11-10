package edu.lms.service;

import jakarta.validation.constraints.NotBlank;

public record PasswordReminderRequest(

        @NotBlank(message = "usernameOrEmail is required")
        String usernameOrEmail
) {}
