package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.titiplex.api.dto.ScenarioDto;
import org.titiplex.persistence.model.CollaborationStatus;
import org.titiplex.persistence.model.CollaboratorRole;
import org.titiplex.persistence.model.Language;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioVisibilityStatus;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.ScenarioCollaboratorRepository;
import org.titiplex.persistence.repo.ScenarioRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("SequencedCollectionMethodCanBeUsed")
@ExtendWith(MockitoExtension.class)
class ScenarioServiceTest {

    @Mock
    private ScenarioRepository scenarioRepository;
    @Mock
    private UserService userService;
    @Mock
    private LanguageService languageService;
    @Mock
    private ScenarioTagService scenarioTagService;

    @InjectMocks
    private ScenarioService scenarioService;

    @Test
    void createScenario_buildsScenarioWithAuthorAndLanguage() {
        User user = new User();
        user.setId(9L);
        user.setUsername("alice");
        Language language = new Language();
        language.setId("fra");

        when(userService.getUserById(9L)).thenReturn(user);
        when(languageService.getLanguage("fra")).thenReturn(language);
        when(scenarioTagService.resolveTags(List.of())).thenReturn(List.of());
        when(scenarioRepository.save(any(Scenario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Scenario created = scenarioService.createScenario("Titre", "Desc", 9L, "fra", List.of());

        assertEquals("Titre", created.getTitle());
        assertEquals("Desc", created.getDescription());
        assertEquals(9L, created.getAuthor_id());
        assertEquals("fra", created.getLanguage_id());
        assertEquals(user, created.getAuthor());
        assertEquals(language, created.getLanguage());
    }

    @Test
    void getRequiredScenario_throwsWhenMissing() {
        when(scenarioRepository.findByIdWithTags(11L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> scenarioService.getRequiredScenario(11L));
    }

    @Test
    void listScenarios_returnsRepositoryList() {
        Scenario scenario = new Scenario();
        scenario.setTitle("Latest");
        when(scenarioRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(scenario));

        List<Scenario> result = scenarioService.listAllScenarios();

        assertEquals(1, result.size());
        assertEquals("Latest", result.get(0).getTitle());
    }

    @Test
    void toDto_mapsEntityFields() {
        User author = new User();
        author.setId(7L);
        author.setUsername("alice");

        when(userService.getUserById(7L)).thenReturn(author);
        when(scenarioTagService.toNames(anyCollection())).thenReturn(List.of());

        Scenario scenario = new Scenario();
        scenario.setId(3L);
        scenario.setTitle("My scenario");
        scenario.setDescription("description");
        scenario.setLanguage_id("fra");
        scenario.setAuthor_id(7L);
        scenario.setAuthor(author);
        scenario.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));

        ScenarioDto dto = scenarioService.toDto(scenario);

        assertEquals(3L, dto.id());
        assertEquals("My scenario", dto.title());
        assertEquals("alice", dto.authorUsername());
    }

    @Test
    void listPublishedScenariosByFamilyId_usesDefaultLimit() {
        Scenario scenario = publishedScenario(8L, "Family hit");
        when(scenarioRepository.findPublishedByFamilyIdOrderByCreatedAtDesc(eq("indo1319"), any(Pageable.class)))
                .thenReturn(List.of(scenario));
        when(userService.getUserById(7L)).thenReturn(author());
        when(scenarioTagService.toNames(any())).thenReturn(List.of());

        List<ScenarioDto> result = scenarioService.listPublishedScenariosByFamilyId("indo1319", null);

        assertEquals(1, result.size());
        verify(languageService).assertFamilyExists("indo1319");
        verify(scenarioRepository).findPublishedByFamilyIdOrderByCreatedAtDesc(eq("indo1319"), eq(Pageable.ofSize(15)));
    }

    @Test
    void listPublishedScenariosByCountryIso_returnsEmptyWhenNoLanguages() {
        when(languageService.findLanguageIdsByCountryIsoA3("CAN")).thenReturn(List.of());

        List<ScenarioDto> result = scenarioService.listPublishedScenariosByCountryIso("CAN", 10);

        assertEquals(0, result.size());
    }

    @Test
    void listPublishedScenariosByCountryIso_clampsLimitToFifteen() {
        Scenario scenario = publishedScenario(2L, "Country hit");
        when(languageService.findLanguageIdsByCountryIsoA3("FRA")).thenReturn(List.of("fra", "oci"));
        when(scenarioRepository.findPublishedByLanguageIdInOrderByCreatedAtDesc(eq(List.of("fra", "oci")), any(Pageable.class)))
                .thenReturn(List.of(scenario));
        when(userService.getUserById(7L)).thenReturn(author());
        when(scenarioTagService.toNames(any())).thenReturn(List.of());

        List<ScenarioDto> result = scenarioService.listPublishedScenariosByCountryIso("FRA", 99);

        assertEquals(1, result.size());
        verify(scenarioRepository).findPublishedByLanguageIdInOrderByCreatedAtDesc(
                eq(List.of("fra", "oci")),
                eq(Pageable.ofSize(15))
        );
    }

    private Scenario publishedScenario(long id, String title) {
        Scenario scenario = new Scenario();
        scenario.setId(id);
        scenario.setTitle(title);
        scenario.setDescription("description");
        scenario.setLanguage_id("fra");
        scenario.setAuthor_id(7L);
        scenario.setAuthor(author());
        scenario.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        scenario.setVisibilityStatus(ScenarioVisibilityStatus.PUBLISHED);
        scenario.setTags(Set.of());
        return scenario;
    }

    private User author() {
        User author = new User();
        author.setId(7L);
        author.setUsername("alice");
        return author;
    }
}