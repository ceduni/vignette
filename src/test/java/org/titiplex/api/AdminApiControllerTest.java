package org.titiplex.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.titiplex.api.dto.AdminOverviewDto;
import org.titiplex.api.dto.AdminUserRowDto;
import org.titiplex.api.dto.ScenarioDto;
import org.titiplex.persistence.model.Role;
import org.titiplex.persistence.model.User;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.UserService;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SuppressWarnings("SequencedCollectionMethodCanBeUsed")
@ExtendWith(MockitoExtension.class)
class AdminApiControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private ScenarioService scenarioService;

    @InjectMocks
    private AdminApiController controller;

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                "user",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    void overview_returnsCounts_forAdmin() {
        when(userService.countUsers()).thenReturn(10L);
        when(scenarioService.countAllScenarios()).thenReturn(7L);
        when(scenarioService.countPublishedScenarios()).thenReturn(4L);
        when(scenarioService.countDraftScenarios()).thenReturn(3L);

        AdminOverviewDto dto = controller.overview(adminAuth());

        assertEquals(10L, dto.userCount());
        assertEquals(7L, dto.scenarioCount());
        assertEquals(4L, dto.publishedScenarioCount());
        assertEquals(3L, dto.draftScenarioCount());
    }

    @Test
    void overview_rejectsNonAdmin() {
        assertThrows(AccessDeniedException.class, () -> controller.overview(userAuth()));
    }

    @Test
    void listUsers_mapsUsers_forAdmin() {
        Role role = new Role();
        role.setName("ROLE_USER");

        User user = new User();
        user.setId(5L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setDisplayName("Alice");
        user.setProfilePublic(true);
        user.setRoles(Set.of(role));

        when(userService.listAllUsers()).thenReturn(List.of(user));

        List<AdminUserRowDto> rows = controller.listUsers(adminAuth());

        assertEquals(1, rows.size());
        assertEquals(5L, rows.get(0).id());
        assertEquals("alice", rows.get(0).username());
        assertTrue(rows.get(0).roles().contains("ROLE_USER"));
        assertTrue(rows.get(0).profilePublic());
    }

    @Test
    void listUsers_rejectsNonAdmin() {
        assertThrows(AccessDeniedException.class, () -> controller.listUsers(userAuth()));
    }

    @Test
    void listScenarios_returnsDtos_forAdmin() {
        ScenarioDto dto = new ScenarioDto(
                12L,
                "Scenario A",
                "Desc",
                "fra",
                "alice",
                Instant.parse("2026-01-01T00:00:00Z"),
                "DRAFT",
                null,
                "PRESET",
                "GRID_3",
                3,
                List.of(),
                null,
                "NONE",
                null,
                null,
                null,
                true
        );

        when(scenarioService.listAllScenarios()).thenReturn(List.of());
        when(scenarioService.toDto(org.mockito.ArgumentMatchers.any())).thenReturn(dto);

        // simulate one scenario object
        var scenario = new org.titiplex.persistence.model.Scenario();
        when(scenarioService.listAllScenarios()).thenReturn(List.of(scenario));

        List<ScenarioDto> rows = controller.listScenarios(adminAuth());

        assertEquals(1, rows.size());
        assertEquals(12L, rows.get(0).id());
        assertEquals("Scenario A", rows.get(0).title());
    }

    @Test
    void listScenarios_rejectsNonAdmin() {
        assertThrows(AccessDeniedException.class, () -> controller.listScenarios(userAuth()));
    }
}