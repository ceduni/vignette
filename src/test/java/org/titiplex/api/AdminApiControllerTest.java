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
import org.titiplex.api.dto.GlottologImportPreviewDto;
import org.titiplex.api.dto.GlottologSyncResultDto;
import org.titiplex.api.dto.GlottologUpdateResultDto;
import org.titiplex.api.dto.ScenarioDto;
import org.titiplex.persistence.model.Role;
import org.titiplex.persistence.model.User;
import org.titiplex.service.GlottologAdminService;
import org.titiplex.service.GlottologUpdateJobService;
import org.titiplex.service.GlottologUpdateService;
import org.titiplex.service.LanguageImportService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.UserService;
import org.titiplex.config.GlottologProperties;

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
    private LanguageImportService languageImportService;

    @Mock
    private GlottologUpdateService glottologUpdateService;

    @Mock
    private GlottologUpdateJobService glottologUpdateJobService;

    @Mock
    private GlottologAdminService glottologAdminService;

    @Mock
    private GlottologProperties glottologProperties;

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
    void listScenarios_rejectsNonAdmin() {
        assertThrows(AccessDeniedException.class, () -> controller.listScenarios(userAuth()));
    }

    @Test
    void previewGlottolog_returnsPreview_forAdmin() throws Exception {
        when(glottologUpdateService.preview())
                .thenReturn(new GlottologImportPreviewDto(27034, 9267, 9000L));

        GlottologImportPreviewDto preview = controller.previewGlottolog(adminAuth());

        assertEquals(27034, preview.sourceRows());
        assertEquals(9267, preview.selectedRows());
        assertEquals(9000L, preview.databaseCount());
    }

    @Test
    void previewGlottolog_rejectsNonAdmin() {
        assertThrows(AccessDeniedException.class, () -> controller.previewGlottolog(userAuth()));
    }

    @Test
    void syncGlottolog_gone_whenLegacyDisabled() {
        when(glottologProperties.isLegacyPipelineEnabled()).thenReturn(false);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.syncGlottolog(adminAuth()));
    }

    @Test
    void syncGlottolog_returnsResult_forAdmin() throws Exception {
        when(glottologProperties.isLegacyPipelineEnabled()).thenReturn(true);
        when(glottologUpdateService.resolveActiveCsvPath()).thenReturn(null);
        when(languageImportService.syncFromClasspath())
                .thenReturn(new GlottologSyncResultDto(27034, 9267, 10, 5, 9252, 9277L, 1200L));

        GlottologSyncResultDto result = controller.syncGlottolog(adminAuth());

        assertEquals(10, result.inserted());
        assertEquals(5, result.updated());
        assertEquals(9252, result.unchanged());
    }

    @Test
    void syncGlottolog_rejectsNonAdmin() {
        assertThrows(AccessDeniedException.class, () -> controller.syncGlottolog(userAuth()));
    }

    @Test
    void updateGlottolog_depositsManualRequest_whenLegacyDisabled() throws Exception {
        when(glottologProperties.isLegacyPipelineEnabled()).thenReturn(false);
        when(glottologAdminService.createManualUpdateRequest("admin"))
                .thenReturn(new org.titiplex.api.dto.GlottologUpdateRequestDto(
                        7L, "admin", Instant.now(), "PENDING",
                        null, null, null, null, null, null, null
                ));

        Object result = controller.updateGlottolog(adminAuth());

        assertInstanceOf(org.titiplex.api.dto.GlottologUpdateRequestDto.class, result);
        assertEquals(7L, ((org.titiplex.api.dto.GlottologUpdateRequestDto) result).id());
    }

    @Test
    void updateGlottolog_runsLegacyPipeline_whenEnabled() throws Exception {
        when(glottologProperties.isLegacyPipelineEnabled()).thenReturn(true);
        GlottologSyncResultDto sync = new GlottologSyncResultDto(13947, 13947, 10, 5, 13932, 13957L, 1200L);
        when(glottologAdminService.runImmediateUpdate("admin"))
                .thenReturn(new GlottologUpdateResultDto("5.3", 95000L, "Wrote 13,947 rows", sync, false));

        Object result = controller.updateGlottolog(adminAuth());

        GlottologUpdateResultDto typed = (GlottologUpdateResultDto) result;
        assertEquals("5.3", typed.glottologVersion());
        assertEquals(95000L, typed.downloadDurationMs());
        assertEquals(10, typed.sync().inserted());
        assertFalse(typed.sourceUnchanged());
    }

    @Test
    void updateGlottolog_rejectsNonAdmin() {
        assertThrows(AccessDeniedException.class, () -> controller.updateGlottolog(userAuth()));
    }
}