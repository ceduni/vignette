package org.titiplex.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.titiplex.api.dto.AdminOverviewDto;
import org.titiplex.api.dto.AdminUserRowDto;
import org.titiplex.api.dto.GlottologImportPreviewDto;
import org.titiplex.api.dto.ScenarioDto;
import org.titiplex.persistence.model.Role;
import org.titiplex.persistence.model.User;
import org.titiplex.service.GlottologAdminService;
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

    @Mock
    private GlottologAdminService glottologAdminService;

    @InjectMocks
    private AdminApiController controller;

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
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
                null, false, null
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
    void previewGlottolog_returnsDatabaseCount_forAdmin() {
        when(glottologAdminService.preview()).thenReturn(new GlottologImportPreviewDto(9000L));

        GlottologImportPreviewDto preview = controller.previewGlottolog(adminAuth());

        assertEquals(9000L, preview.databaseCount());
    }

    @Test
    void requestGlottologUpdate_depositsManualRequestForPythonWorker() {
        when(glottologAdminService.createManualUpdateRequest("admin"))
                .thenReturn(new org.titiplex.api.dto.GlottologUpdateRequestDto(
                        7L, "admin", Instant.now(), "PENDING",
                        null, null, null, null, null, null, null
                ));

        var result = controller.requestGlottologUpdate(adminAuth());

        assertEquals(7L, result.id());
    }
}