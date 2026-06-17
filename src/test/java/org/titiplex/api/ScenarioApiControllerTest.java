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
import org.titiplex.persistence.model.User;
import org.titiplex.service.LanguageService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.UserService;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                null
        );

        when(scenarioService.getVisibleScenario(9L, auth)).thenReturn(scenario);
        when(scenarioService.toDto(scenario)).thenReturn(dto);

        ScenarioDto result = controller.getOne(9L, auth);

        assertEquals(9L, result.id());
        assertEquals("Story", result.title());
        assertEquals("alice", result.authorUsername());
    }

    @Test
    void listAll_mapsVisibleScenariosToDtos() {
        Authentication auth = auth("bob", "ROLE_USER");

        Scenario s1 = new Scenario();
        s1.setId(1L);

        Scenario s2 = new Scenario();
        s2.setId(2L);

        ScenarioDto dto1 = new ScenarioDto(
                1L, "First", "D1", "chuj", "bob",
                Instant.parse("2026-03-20T10:15:30Z"),
                "DRAFT", null, "PRESET", "GRID_3", 3,
                List.of(), null
        );
        ScenarioDto dto2 = new ScenarioDto(
                2L, "Second", "D2", "kiche", "bob",
                Instant.parse("2026-03-21T10:15:30Z"),
                "PUBLISHED", Instant.parse("2026-03-22T10:15:30Z"),
                "CUSTOM", "MANGA", 4,
                List.of(),
                null
        );

        when(scenarioService.listVisibleScenarios(auth)).thenReturn(List.of(s1, s2));
        when(scenarioService.toDto(s1)).thenReturn(dto1);
        when(scenarioService.toDto(s2)).thenReturn(dto2);

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
                null
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
                null
        );

        when(scenarioService.publishScenario(21L, auth)).thenReturn(published);
        when(scenarioService.toDto(published)).thenReturn(dto);

        ScenarioDto result = controller.publish(21L, auth);

        assertEquals(21L, result.id());
        assertEquals("PUBLISHED", result.visibilityStatus());
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

        Scenario scenario = new Scenario();
        scenario.setId(1L);
        scenario.setTitle("Mine");

        when(scenarioService.listMyScenarios(auth)).thenReturn(List.of(scenario));
        when(scenarioService.toDto(scenario)).thenReturn(new ScenarioDto(
                1L, "Mine", null, "fra", "alice", null, "DRAFT", null, "PRESET", "GRID_3", 3, List.of(), null
        ));

        List<ScenarioDto> result = controller.listMine(auth);

        assertEquals(1, result.size());
        assertEquals("Mine", result.get(0).title());
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
                5L, "New title", "New description", "fra", "alice", null, "DRAFT", null, "PRESET", "GRID_3", 3, List.of(), null
        ));

        ScenarioDto result = controller.updateMetadata(5L, req, auth);

        assertEquals(5L, result.id());
        assertEquals("New title", result.title());
    }
}