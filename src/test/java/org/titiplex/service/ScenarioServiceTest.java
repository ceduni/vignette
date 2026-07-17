package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.titiplex.api.dto.ScenarioDto;
import org.titiplex.persistence.model.CollaborationStatus;
import org.titiplex.persistence.model.CollaboratorRole;
import org.titiplex.persistence.model.Language;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioCollaborator;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
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
    @Mock
    private ScenarioCollaboratorRepository collaboratorRepository;

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

    // ── Collaborator permission checks ──────────────────────────────────

    private Authentication authAs(String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(username);
        return auth;
    }

    private Scenario scenarioOwnedBy(Long ownerId) {
        Scenario scenario = new Scenario();
        scenario.setId(42L);
        scenario.setAuthor_id(ownerId);
        return scenario;
    }

    private User userWithId(String username, Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    @Test
    void assertCanEditScenario_allowsAcceptedEditorCollaborator() {
        Scenario scenario = scenarioOwnedBy(1L);
        Authentication auth = authAs("bob");

        when(scenarioRepository.existsByIdAndAuthorUsername(42L, "bob")).thenReturn(false);
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));

        ScenarioCollaborator collaborator = new ScenarioCollaborator();
        collaborator.setRole(CollaboratorRole.EDITOR);
        collaborator.setStatus(CollaborationStatus.ACCEPTED);
        when(collaboratorRepository.findByScenarioIdAndUserId(42L, 2L)).thenReturn(Optional.of(collaborator));

        assertDoesNotThrow(() -> scenarioService.assertCanEditScenario(scenario, auth));
    }

    @Test
    void assertCanEditScenario_deniesAcceptedViewerCollaborator() {
        Scenario scenario = scenarioOwnedBy(1L);
        Authentication auth = authAs("bob");

        when(scenarioRepository.existsByIdAndAuthorUsername(42L, "bob")).thenReturn(false);
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));

        ScenarioCollaborator collaborator = new ScenarioCollaborator();
        collaborator.setRole(CollaboratorRole.VIEWER);
        collaborator.setStatus(CollaborationStatus.ACCEPTED);
        when(collaboratorRepository.findByScenarioIdAndUserId(42L, 2L)).thenReturn(Optional.of(collaborator));

        assertThrows(AccessDeniedException.class, () -> scenarioService.assertCanEditScenario(scenario, auth));
    }

    @Test
    void assertCanEditScenario_deniesPendingEditorCollaborator() {
        Scenario scenario = scenarioOwnedBy(1L);
        Authentication auth = authAs("bob");

        when(scenarioRepository.existsByIdAndAuthorUsername(42L, "bob")).thenReturn(false);
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));

        ScenarioCollaborator collaborator = new ScenarioCollaborator();
        collaborator.setRole(CollaboratorRole.EDITOR);
        collaborator.setStatus(CollaborationStatus.PENDING);
        when(collaboratorRepository.findByScenarioIdAndUserId(42L, 2L)).thenReturn(Optional.of(collaborator));

        assertThrows(AccessDeniedException.class, () -> scenarioService.assertCanEditScenario(scenario, auth));
    }

    @Test
    void assertCanEditScenario_deniesUserWithNoRelationToScenario() {
        Scenario scenario = scenarioOwnedBy(1L);
        Authentication auth = authAs("stranger");

        when(scenarioRepository.existsByIdAndAuthorUsername(42L, "stranger")).thenReturn(false);
        when(userService.getUserByUsername("stranger")).thenReturn(userWithId("stranger", 3L));
        when(collaboratorRepository.findByScenarioIdAndUserId(42L, 3L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> scenarioService.assertCanEditScenario(scenario, auth));
    }

    @Test
    void assertCanViewScenario_allowsAcceptedViewerOnDraftScenario() {
        Scenario scenario = scenarioOwnedBy(1L);
        // DRAFT by default, so only owner/collaborator/admin should see it
        Authentication auth = authAs("bob");

        when(scenarioRepository.existsByIdAndAuthorUsername(42L, "bob")).thenReturn(false);
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));

        ScenarioCollaborator collaborator = new ScenarioCollaborator();
        collaborator.setRole(CollaboratorRole.VIEWER);
        collaborator.setStatus(CollaborationStatus.ACCEPTED);
        when(collaboratorRepository.findByScenarioIdAndUserId(42L, 2L)).thenReturn(Optional.of(collaborator));

        assertDoesNotThrow(() -> scenarioService.assertCanViewScenario(scenario, auth));
    }

    @Test
    void assertCanViewScenario_deniesNonCollaboratorOnDraftScenario() {
        Scenario scenario = scenarioOwnedBy(1L);
        Authentication auth = authAs("stranger");

        when(scenarioRepository.existsByIdAndAuthorUsername(42L, "stranger")).thenReturn(false);
        when(userService.getUserByUsername("stranger")).thenReturn(userWithId("stranger", 3L));
        when(collaboratorRepository.findByScenarioIdAndUserId(42L, 3L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> scenarioService.assertCanViewScenario(scenario, auth));
    }
}