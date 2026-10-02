package edu.lms.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProfileService {

    private final ProfileRepository repository;

    public ProfileService(ProfileRepository repository) {
        this.repository = repository;
    }
    public List<Profile> findAll() {
        return repository.findAll();
    }

    public Optional<Profile> findById(Long id) {
        return repository.findById(id);
    }

    public List<Profile> findTeachers() {
        return repository.findByRole("teacher");
    }

    public Optional<Profile> findTeacher(String username) {
        return repository.findByUsernameAndRole(username, "teacher");
    }

    public List<Profile> findDirectory() {
        return repository.findAllByOrderByRoleAscLastNameAscFirstNameAsc();
    }

    public Profile create(Profile profile) {
        profile.setId(null);
        if (profile.getClasses() != null) {
            profile.getClasses().forEach(c -> c.setProfile(profile));
        }
        return repository.save(profile);
    }

    public Optional<Profile> update(Long id, Profile incoming) {
        return repository.findById(id).map(existing -> {
            existing.setUsername(incoming.getUsername());
            existing.setFullName(incoming.getFullName());
            existing.setTitle(incoming.getTitle());
            existing.setFirstName(incoming.getFirstName());
            existing.setLastName(incoming.getLastName());
            existing.setGender(incoming.getGender());
            existing.setRole(incoming.getRole());
            existing.setEmail(incoming.getEmail());
            existing.setPhone(incoming.getPhone());
            existing.setLocation(incoming.getLocation());
            existing.setBio(incoming.getBio());
            existing.setDateOfBirth(incoming.getDateOfBirth());
            existing.setSchool(incoming.getSchool());
            existing.setGradeLevel(incoming.getGradeLevel());
            existing.setAddress(incoming.getAddress());
            existing.setGuardian(incoming.getGuardian());
            existing.setPreferences(incoming.getPreferences());
            existing.setClasses(incoming.getClasses());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }
}
