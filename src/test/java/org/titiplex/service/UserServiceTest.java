package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.titiplex.persistence.model.Role;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.UserRepository;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RolesService rolesService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void register_throwsWhenUsernameAlreadyTaken() {
        when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> userService.register("alice", "alice@mail.com", "pass"));

        assertEquals("username already used", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_throwsWhenEmailAlreadyUsedWithDifferentCase() {
        when(userRepository.existsByUsernameIgnoreCase("bob")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("alice@mail.com")).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> userService.register("  bob ", " Alice@Mail.COM ", "pass"));

        assertEquals("email already used", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_savesUserWithEncodedPasswordAndRole() {
        Role role = new Role();
        role.setName("ROLE_USER");

        when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("alice@mail.com")).thenReturn(false);
        when(rolesService.getUserRole()).thenReturn(role);
        when(passwordEncoder.encode("secret")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.register(" alice ", "Alice@Mail.com", "secret");

        assertEquals("alice", result.getUsername());
        assertEquals("alice@mail.com", result.getEmail());
        assertEquals("alice", result.getDisplayName());
        assertEquals("encoded", result.getPasswordHash());
        assertTrue(result.getRoles().contains(role));
    }

    @Test
    void getUserById_returnsFallbackUserWhenNotFound() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        User result = userService.getUserById(42L);

        assertEquals("User not found", result.getUsername());
    }

    @Test
    void updateProfile_trimsAndFiltersFields() {
        User user = new User();
        user.setProfilePublic(false);
        when(userRepository.save(user)).thenReturn(user);

        User result = userService.updateProfile(
                user,
                "  Display  ",
                "  Bio  ",
                "  Inst  ",
                "  Research  ",
                true,
                Set.of("  Univ A  ", " ", "Lab B")
        );

        assertEquals("Display", result.getDisplayName());
        assertEquals("Bio", result.getBio());
        assertEquals("Inst", result.getInstitution());
        assertEquals("Research", result.getResearchInterests());
        assertTrue(result.isProfilePublic());
        assertEquals(Set.of("Univ A", "Lab B"), result.getAcademyAffiliations());
    }

    @Test
    void updateRoles_rejectsExplicitAdminModification() {
        assertThrows(IllegalArgumentException.class, () ->
                userService.updateRoles(1L, Set.of("ROLE_USER", "ROLE_ADMIN"))
        );
    }

    @Test
    void updateRoles_preservesAdminWhenTargetUserAlreadyAdmin() {
        Role admin = new Role();
        admin.setName("ROLE_ADMIN");

        Role linguist = new Role();
        linguist.setName("ROLE_LINGUIST");

        User user = new User();
        user.setId(1L);
        user.setRoles(new java.util.HashSet<>(Set.of(admin)));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(rolesService.getRequiredRoleByName("ROLE_LINGUIST")).thenReturn(linguist);
        when(rolesService.getAdminRole()).thenReturn(admin);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.updateRoles(1L, Set.of("ROLE_LINGUIST"));

        assertTrue(updated.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getName())));
        assertTrue(updated.getRoles().stream().anyMatch(r -> "ROLE_LINGUIST".equals(r.getName())));
    }
}