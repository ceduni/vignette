package org.titiplex.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.titiplex.api.dto.*;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioHistoryAction;
import org.titiplex.persistence.model.ScenarioHistoryEntry;
import org.titiplex.persistence.model.User;
import org.titiplex.service.LanguageService;
import org.titiplex.service.ScenarioHistoryService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.UserService;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"SameParameterValue", "SequencedCollectionMethodCanBeUsed"})
@ExtendWith(MockitoExtension.class)
class ScenarioApiControllerTest {

    @Mock
    private ScenarioService scenarioService;

    @Mock
    private UserService userService;

    @Mock
    private LanguageService languageService;

    @Mock
    private ScenarioHistoryService scenarioHistoryService;

    @InjectMocks
    private ScenarioApiController controller;

    @Test
    void create_usesAuthenticatedUserAndTrimsTitle() {
        Authentication auth = auth("alice", "ROLE_USER");

        User user = new User();
        user.setId(12L);
        user.setUsername("alice");

        Scenario created = new Scenario();
        created.setId(44L);

        when(languageService.existsById("chuj")).thenReturn(true);
        when(userService.getUserByUsername("alice")).thenReturn(user);
        when(scenarioService.existsByTitleAndAuthorNameAndLanguageId("  Story  ", "alice", "chuj"))
                .thenReturn(false);
        when(scenarioService.createScenario("Story", "A desc", 12L, "chuj", List.of()))
                .thenReturn(created);

        CreateScenarioResponse result = controller.create(
                new CreateScenarioRequest("  Story  ", "A desc", "chuj", List.of()),
                auth
        );

        assertEquals(44L, result.id());
        verify(scenarioService).createScenario("Story", "A desc", 12L, "chuj", List.of());
    }

    @Test
    void getOne_usesVisibleScenarioAndMapsToDto() {
        Authentication auth = auth("alice", "ROLE_USER");

        Scenario scenario = new Scenario();
        scenario.setId(9L);

        ScenarioDto dto = new ScenarioDto(
                9L,
                "Story",
                "Desc",
                "chuj",
                "alice",
                Instant.parse("2026-03-20T10:15:30Z"),
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
                null, false
        );

        when(scenarioService.getVisibleScenario(9L, auth)).thenReturn(scenario);
        when(scenarioService.toDto(scenario, "alice")).thenReturn(dto);

        ScenarioDto result = controller.getOne(9L, auth);

        assertEquals(9L, result.id());
        assertEquals("Story", result.title());
        assertEquals("alice", result.authorUsername());
    }

    @Test
    void history_checksVisibilityAndMapsEntriesMostRecentFirst() {
        Authentication auth = auth("alice", "ROLE_USER");

        Scenario scenario = new Scenario();
        scenario.setId(9L);

        ScenarioHistoryEntry newer = new ScenarioHistoryEntry();
        newer.setId(2L);
        newer.setActorUsername("bob");
        newer.setAction(ScenarioHistoryAction.THUMBNAIL_ADDED);
        newer.setSummary("Added thumbnail \"Intro\"");
        newer.setCreatedAt(Instant.parse("2026-03-21T10:00:00Z"));

        ScenarioHistoryEntry older = new ScenarioHistoryEntry();
        older.setId(1L);
        older.setActorUsername("alice");
        older.setAction(ScenarioHistoryAction.SCENARIO_CREATED);
        older.setSummary("Created the scenario");
        older.setCreatedAt(Instant.parse("2026-03-20T10:00:00Z"));

        when(scenarioService.getVisibleScenario(9L, auth)).thenReturn(scenario);
        when(scenarioHistoryService.list(9L)).thenReturn(List.of(newer, older));

        List<ScenarioHistoryEntryDto> result = controller.history(9L, auth);

        assertEquals(2, result.size());
        assertEquals("bob", result.get(0).actorUsername());
        assertEquals("THUMBNAIL_ADDED", result.get(0).action());
        assertEquals("Added thumbnail \"Intro\"", result.get(0).summary());
        assertEquals("alice", result.get(1).actorUsername());
        assertEquals("SCENARIO_CREATED", result.get(1).action());
    }

    @Test
    void listAll_mapsVisibleScenariosToDtos() {
        Authentication auth = auth("bob", "ROLE_USER");

        ScenarioDto dto1 = new ScenarioDto(
                1L, "First", "D1", "chuj", "bob",
                Instant.parse("2026-03-20T10:15:30Z"),
                "DRAFT", null, "PRESET", "GRID_3", 3,
                List.of(), null,
                "NONE", null, null, null, false
        );
        ScenarioDto dto2 = new ScenarioDto(
                2L, "Second", "D2", "kiche", "bob",
                Instant.parse("2026-03-21T10:15:30Z"),
                "PUBLISHED", Instant.parse("2026-03-22T10:15:30Z"),
                "CUSTOM", "MANGA", 4,
                List.of(),
                null,
                "NONE", null, null, null, false
        );

        when(scenarioService.listVisibleScenarioDtos(auth)).thenReturn(List.of(dto1, dto2));

        List<ScenarioDto> result = controller.listAll(auth);

        assertEquals(2, result.size());
        assertEquals("First", result.get(0).title());
        assertEquals("Second", result.get(1).title());
    }

    @Test
    void updateStoryboard_delegatesToServiceAndMapsDto() {
        Authentication auth = auth("alice", "ROLE_USER");

        Scenario updated = new Scenario();
        updated.setId(15L);

        ScenarioDto dto = new ScenarioDto(
                15L, "Story", "Desc", "chuj", "alice",
                Instant.parse("2026-03-20T10:15:30Z"),
                "DRAFT", null, "CUSTOM", "MANGA", 4,
                List.of(),
                null,
                "NONE", null, null, null, false
        );

        UpdateScenarioStoryboardRequest request = new UpdateScenarioStoryboardRequest("CUSTOM", "MANGA", 4);

        when(scenarioService.updateStoryboard(15L, request, auth)).thenReturn(updated);
        when(scenarioService.toDto(updated)).thenReturn(dto);

        ScenarioDto result = controller.updateStoryboard(15L, request, auth);

        assertEquals(15L, result.id());
        assertEquals("CUSTOM", result.storyboardLayoutMode());
        assertEquals("MANGA", result.storyboardPreset());
        assertEquals(4, result.storyboardColumns());
    }

    @Test
    void publish_delegatesToServiceAndMapsDto() {
        Authentication auth = auth("alice", "ROLE_USER");

        Scenario published = new Scenario();
        published.setId(21L);

        ScenarioDto dto = new ScenarioDto(
                21L, "Story", "Desc", "chuj", "alice",
                Instant.parse("2026-03-20T10:15:30Z"),
                "PUBLISHED", Instant.parse("2026-03-22T10:15:30Z"),
                "PRESET", "GRID_3", 3,
                List.of(),
                null,
                "NONE", null, null, null, false
        );

        when(scenarioService.publishScenario(21L, auth)).thenReturn(published);
        when(scenarioService.toDto(published)).thenReturn(dto);

        ScenarioDto result = controller.publish(21L, auth);

        assertEquals(21L, result.id());
        assertEquals("PUBLISHED", result.visibilityStatus());
    }

    @Test
    void approveFork_delegatesToServiceWithApproveTrue() {
        Authentication auth = auth("alice", "ROLE_USER");

        Scenario approved = new Scenario();
        approved.setId(30L);

        ScenarioDto dto = new ScenarioDto(
                30L, "Forked story", "Desc", "chuj", "bob",
                Instant.parse("2026-03-20T10:15:30Z"),
                "DRAFT", null, "PRESET", "GRID_3", 3,
                List.of(),
                21L,
                "APPROVED", "alice", Instant.parse("2026-03-25T10:00:00Z"), null, false
        );

        when(scenarioService.reviewFork(eq(30L), eq(true), eq((String) null), eq(auth))).thenReturn(approved);
        when(scenarioService.toDto(approved)).thenReturn(dto);

        ScenarioDto result = controller.approveFork(30L, null, auth);

        assertEquals(30L, result.id());
        assertEquals("APPROVED", result.reviewStatus());
        assertEquals("alice", result.reviewedByUsername());
    }

    @Test
    void rejectFork_delegatesToServiceWithApproveFalseAndComment() {
        Authentication auth = auth("alice", "ROLE_USER");

        Scenario rejected = new Scenario();
        rejected.setId(31L);

        ScenarioDto dto = new ScenarioDto(
                31L, "Forked story", "Desc", "chuj", "bob",
                Instant.parse("2026-03-20T10:15:30Z"),
                "DRAFT", null, "PRESET", "GRID_3", 3,
                List.of(),
                21L,
                "REJECTED", "alice", Instant.parse("2026-03-25T10:00:00Z"), "Not accurate enough", false
        );

        ScenarioApiController.ReviewRequest body = new ScenarioApiController.ReviewRequest("Not accurate enough");

        when(scenarioService.reviewFork(31L, false, "Not accurate enough", auth)).thenReturn(rejected);
        when(scenarioService.toDto(rejected)).thenReturn(dto);

        ScenarioDto result = controller.rejectFork(31L, body, auth);

        assertEquals(31L, result.id());
        assertEquals("REJECTED", result.reviewStatus());
        assertEquals("Not accurate enough", result.reviewComment());
    }

    @Test
    void delete_delegatesToService() {
        controller.delete(77L);
        verify(scenarioService).deleteScenario(77L);
    }

    private Authentication auth(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "N/A",
                Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
    }
    @Test
    void listMine_returnsCurrentUserScenarios() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "alice",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        ScenarioDto dto = new ScenarioDto(
                1L, "Mine", null, "fra", "alice", null, "DRAFT", null, "PRESET", "GRID_3", 3, List.of(), null,
                "NONE", null, null, null, false
        );

        when(scenarioService.listMyScenarioDtos(auth)).thenReturn(List.of(dto));

        List<ScenarioDto> result = controller.listMine(auth);

        assertEquals(1, result.size());
        assertEquals("Mine", result.get(0).title());
    }

    @Test
    void listSharedWithMe_returnsCollaboratedScenarios() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "bob",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        ScenarioDto dto = new ScenarioDto(
                5L, "Shared", null, "fra", "alice", null, "PUBLISHED", null, "PRESET", "GRID_3", 3, List.of(), null,
                "NONE", null, null, null, true
        );

        when(scenarioService.listSharedWithMeScenarioDtos(auth)).thenReturn(List.of(dto));

        List<ScenarioDto> result = controller.listSharedWithMe(auth);

        assertEquals(1, result.size());
        assertEquals("Shared", result.get(0).title());
        assertEquals("alice", result.get(0).authorUsername());
        assertEquals(true, result.get(0).canEdit());
    }

    @Test
    void listPublishedScenariosWorkedOnByUsername_delegatesToService() {
        ScenarioDto dto = new ScenarioDto(
                6L, "Worked on", null, "fra", "alice", null, "PUBLISHED", null, "PRESET", "GRID_3", 3, List.of(), null,
                "NONE", null, null, null, false
        );

        when(scenarioService.listPublishedScenariosWorkedOnByUsername("alice")).thenReturn(List.of(dto));

        List<ScenarioDto> result = controller.listPublishedScenariosWorkedOnByUsername("alice");

        assertEquals(1, result.size());
        assertEquals("Worked on", result.get(0).title());
    }

    @Test
    void updateMetadata_returnsUpdatedScenario() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "alice",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        UpdateScenarioMetadataRequest req = new UpdateScenarioMetadataRequest("New title", "New description", List.of());

        Scenario updated = new Scenario();
        updated.setId(5L);
        updated.setTitle("New title");

        when(scenarioService.updateScenarioMetadata(5L, req, auth)).thenReturn(updated);
        when(scenarioService.toDto(updated)).thenReturn(new ScenarioDto(
                5L, "New title", "New description", "fra", "alice", null, "DRAFT", null, "PRESET", "GRID_3", 3, List.of(), null,
                "NONE", null, null, null, false
        ));

        ScenarioDto result = controller.updateMetadata(5L, req, auth);

        assertEquals(5L, result.id());
        assertEquals("New title", result.title());
    }

    @Test
    void listByFamily_delegatesToService() {
        when(scenarioService.listPublishedScenariosByFamilyId("indo1319", 10)).thenReturn(List.of(
                new ScenarioDto(
                        1L, "Family story", null, "fra", "alice", null,
                        "PUBLISHED", null, "PRESET", "GRID_3", 3,
                        List.of(), null,
                        "NONE", null, null, null, false
                )
        ));

        List<ScenarioDto> result = controller.listByFamily("indo1319", 10);

        assertEquals(1, result.size());
        verify(scenarioService).listPublishedScenariosByFamilyId("indo1319", 10);
    }

    @Test
    void listByCountry_delegatesToService() {
        when(scenarioService.listPublishedScenariosByCountryIso("CAN", null)).thenReturn(List.of());

        List<ScenarioDto> result = controller.listByCountry("CAN", null);

        assertEquals(0, result.size());
        verify(scenarioService).listPublishedScenariosByCountryIso("CAN", null);
    }
}