package edu.lms.service;

/** Thrown when a registration collides with an existing Keycloak account. */
public class UsernameTakenException extends RuntimeException {
    public UsernameTakenException(String message) {
        super(message);
    }
}
