package edu.lms.service;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    public List<Profile> findByRole(String role);
    public Optional<Profile> findByUsernameAndRole(String username, String role);
    public List<Profile> findAllByOrderByRoleAscLastNameAscFirstNameAsc();
}
