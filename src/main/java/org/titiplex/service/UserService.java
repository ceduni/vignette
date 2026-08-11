package org.titiplex.service;

import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.titiplex.persistence.model.Role;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.UserRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {
    private final UserRepository users;
    private final RolesService roles;
    private final PasswordEncoder encoder;

    public UserService(UserRepository userRepository, RolesService roleRepository, PasswordEncoder passwordEncoder) {
        this.users = userRepository;
        this.roles = roleRepository;
        this.encoder = passwordEncoder;
    }

    public User register(String username, String email, String rawPassword) {
        if (users.existsByUsername(username)) throw new IllegalArgumentException("username taken");
        if (users.existsByEmail(email)) throw new IllegalArgumentException("email taken");

        Role userRole = roles.getUserRole();

        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setDisplayName(username);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.getRoles().add(userRole);

        return users.save(u);
    }

    public User getUserById(Long id) {
        User u = new User();
        u.setUsername("User not found");
        return users.findById(id).orElse(u);
    }

    public User getUserByUsername(String username) {
        return users.findByUsername(username).orElse(null);
    }

    public User getExistingUserById(Long id) {
        return users.findById(id).orElse(null);
    }

    public List<User> listAllUsers() {
        return users.findAllByOrderByUsernameAsc();
    }

    public long countUsers() {
        return users.count();
    }

    public User updateProfile(User user,
                              String displayName,
                              String bio,
                              String institution,
                              String researchInterests,
                              Boolean profilePublic,
                              Set<String> academyAffiliations) {
        user.setDisplayName(displayName == null ? null : displayName.trim());
        user.setBio(bio == null ? null : bio.trim());
        user.setInstitution(institution == null ? null : institution.trim());
        user.setResearchInterests(researchInterests == null ? null : researchInterests.trim());
        if (profilePublic != null) user.setProfilePublic(profilePublic);

        if (academyAffiliations != null) {
            var normalized = academyAffiliations.stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toSet());
            user.setAcademyAffiliations(normalized);
        }

        return users.save(user);
    }

    public boolean existsByUsername(String username) {
        return users.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return users.existsByEmail(email);
    }

    public List<User> searchByUsername(String query, String excludeUsername) {
        if (query == null || query.isBlank()) return List.of();
        return users.findTop10ByUsernameContainingIgnoreCaseOrderByUsernameAsc(query.trim())
                .stream()
                .filter(u -> excludeUsername == null || !u.getUsername().equalsIgnoreCase(excludeUsername))
                .toList();
    }

    public User updateRoles(Long userId, Set<String> roleNames) {
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (roleNames == null) {
            throw new IllegalArgumentException("Roles are required");
        }

        if (roleNames.contains("ROLE_ADMIN")) {
            throw new IllegalArgumentException("ROLE_ADMIN cannot be modified from this endpoint");
        }

        Set<Role> resolvedRoles = roleNames.stream()
                .map(roles::getRequiredRoleByName)
                .collect(Collectors.toSet());

        // Preserve locked/admin role if the target user already has it.
        boolean alreadyAdmin = user.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equals(role.getName()));
        if (alreadyAdmin) {
            resolvedRoles.add(roles.getAdminRole());
        }

        // One non-admin role so the account stays usable.
        boolean hasNonAdminRole = resolvedRoles.stream()
                .anyMatch(role -> !"ROLE_ADMIN".equals(role.getName()));
        if (!hasNonAdminRole) {
            resolvedRoles.add(roles.getUserRole());
        }

        user.setRoles(resolvedRoles);
        return users.save(user);
    }
}
