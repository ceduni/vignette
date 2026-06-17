package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.titiplex.api.dto.ScenarioDto;
import org.titiplex.persistence.model.Language;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.ScenarioRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
        when(scenarioRepository.findById(11L)).thenReturn(Optional.empty());

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
}