package edu.lms.service;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

/**
 * Throwaway entity, here only to prove the DB connection works.
 * Replace with your real entities once services are assigned.
 *
 * Note the ownerId field: cross-service references are plain IDs,
 * never JPA relationships to another service's tables.
 */
@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    private String description;

    private Long ownerId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
}
