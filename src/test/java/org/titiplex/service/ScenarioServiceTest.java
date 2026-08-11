package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.titiplex.api.dto.ScenarioDto;
import org.titiplex.persistence.model.CollaborationStatus;
import org.titiplex.persistence.model.CollaboratorRole;
import org.titiplex.persistence.model.Language;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioCollaborator;
import org.titiplex.persistence.model.ScenarioVisibilityStatus;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.AudioRepository;
import org.titiplex.persistence.repo.ScenarioCollaboratorRepository;
import org.titiplex.persistence.repo.ScenarioRepository;
import org.titiplex.persistence.repo.ThumbnailRepository;

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
    @Mock
    private NotificationService notificationService;
    @Mock
    private ThumbnailRepository thumbnailRepo;
    @Mock
    private AudioRepository audioRepo;
    @Mock
    private ScenarioCollaboratorRepository collaboratorRepo;
    @Mock
    private ScenarioHistoryService scenarioHistoryService;

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
        assertEquals(false, dto.canEdit());
    }

    @Test
    void toDto_withViewer_canEditTrueForAuthor() {
        User author = author();
        when(userService.getUserById(7L)).thenReturn(author);
        when(scenarioTagService.toNames(anyCollection())).thenReturn(List.of());
        when(scenarioRepository.existsByIdAndAuthorUsername(3L, "alice")).thenReturn(true);

        Scenario scenario = new Scenario();
        scenario.setId(3L);
        scenario.setTitle("My scenario");
        scenario.setLanguage_id("fra");
        scenario.setAuthor_id(7L);
        scenario.setAuthor(author);

        ScenarioDto dto = scenarioService.toDto(scenario, "alice");

        assertEquals(true, dto.canEdit());
    }

    @Test
    void toDto_withViewer_canEditTrueForAcceptedEditorCollaborator() {
        User author = author();
        User viewer = new User();
        viewer.setId(42L);
        viewer.setUsername("bob");

        when(userService.getUserById(7L)).thenReturn(author);
        when(userService.getUserByUsername("bob")).thenReturn(viewer);
        when(scenarioTagService.toNames(anyCollection())).thenReturn(List.of());
        when(scenarioRepository.existsByIdAndAuthorUsername(3L, "bob")).thenReturn(false);

        ScenarioCollaborator collab = new ScenarioCollaborator();
        collab.setStatus(CollaborationStatus.ACCEPTED);
        collab.setRole(CollaboratorRole.EDITOR);
        when(collaboratorRepo.findByScenarioIdAndUserId(3L, 42L)).thenReturn(Optional.of(collab));

        Scenario scenario = new Scenario();
        scenario.setId(3L);
        scenario.setTitle("My scenario");
        scenario.setLanguage_id("fra");
        scenario.setAuthor_id(7L);
        scenario.setAuthor(author);

        ScenarioDto dto = scenarioService.toDto(scenario, "bob");

        assertEquals(true, dto.canEdit());
    }

    @Test
    void toDto_withViewer_canEditFalseForNonCollaborator() {
        User author = author();
        User viewer = new User();
        viewer.setId(99L);
        viewer.setUsername("stranger");

        when(userService.getUserById(7L)).thenReturn(author);
        when(userService.getUserByUsername("stranger")).thenReturn(viewer);
        when(scenarioTagService.toNames(anyCollection())).thenReturn(List.of());
        when(scenarioRepository.existsByIdAndAuthorUsername(3L, "stranger")).thenReturn(false);
        when(collaboratorRepo.findByScenarioIdAndUserId(3L, 99L)).thenReturn(Optional.empty());

        Scenario scenario = new Scenario();
        scenario.setId(3L);
        scenario.setTitle("My scenario");
        scenario.setLanguage_id("fra");
        scenario.setAuthor_id(7L);
        scenario.setAuthor(author);

        ScenarioDto dto = scenarioService.toDto(scenario, "stranger");

        assertEquals(false, dto.canEdit());
    }

    @Test
    void toDto_withViewer_canEditTrueForAuthorOncePublished() {
        User author = author();
        when(userService.getUserById(7L)).thenReturn(author);
        when(scenarioTagService.toNames(anyCollection())).thenReturn(List.of());
        when(scenarioRepository.existsByIdAndAuthorUsername(3L, "alice")).thenReturn(true);

        Scenario scenario = new Scenario();
        scenario.setId(3L);
        scenario.setTitle("My scenario");
        scenario.setLanguage_id("fra");
        scenario.setAuthor_id(7L);
        scenario.setAuthor(author);
        scenario.setVisibilityStatus(ScenarioVisibilityStatus.PUBLISHED);

        ScenarioDto dto = scenarioService.toDto(scenario, "alice");

        assertEquals(true, dto.canEdit());
    }

    @Test
    void hasContentEditAccess_trueOncePublishedForAuthor() {
        when(scenarioRepository.existsByIdAndAuthorUsername(3L, "alice")).thenReturn(true);

        assertEquals(true, scenarioService.hasContentEditAccess(3L, "alice"));
    }

    @Test
    void hasContentEditAccess_trueForAuthorWhileDraft() {
        when(scenarioRepository.existsByIdAndAuthorUsername(3L, "alice")).thenReturn(true);

        assertEquals(true, scenarioService.hasContentEditAccess(3L, "alice"));
    }

    @Test
    void hasEditAccess_stillTrueForAuthorOncePublished() {
        when(scenarioRepository.existsByIdAndAuthorUsername(3L, "alice")).thenReturn(true);

        assertEquals(true, scenarioService.hasEditAccess(3L, "alice"));
    }

    @Test
    void assertCanEditScenario_allowsAuthorOncePublished() {
        Authentication auth = authOf("alice", "ROLE_USER");
        when(scenarioRepository.existsByIdAndAuthorUsername(3L, "alice")).thenReturn(true);

        Scenario scenario = new Scenario();
        scenario.setId(3L);
        scenario.setVisibilityStatus(ScenarioVisibilityStatus.PUBLISHED);

        assertDoesNotThrow(() -> scenarioService.assertCanEditScenario(scenario, auth));
    }

    @Test
    void assertCanEditScenario_allowsAdminEvenWhenPublished() {
        Authentication auth = authOf("admin", "ROLE_ADMIN");

        Scenario scenario = new Scenario();
        scenario.setId(3L);
        scenario.setVisibilityStatus(ScenarioVisibilityStatus.PUBLISHED);

        assertDoesNotThrow(() -> scenarioService.assertCanEditScenario(scenario, auth));
    }

    @Test
    void assertCanEditScenario_allowsAuthorWhileDraft() {
        Authentication auth = authOf("alice", "ROLE_USER");

        when(scenarioRepository.existsByIdAndAuthorUsername(3L, "alice")).thenReturn(true);

        Scenario scenario = new Scenario();
        scenario.setId(3L);
        scenario.setVisibilityStatus(ScenarioVisibilityStatus.DRAFT);

        assertDoesNotThrow(() -> scenarioService.assertCanEditScenario(scenario, auth));
    }

    @Test
    void listPublishedScenariosWorkedOnByUsername_throwsWhenUserNotFound() {
        when(userService.getUserByUsername("ghost")).thenReturn(null);

        assertThrows(NoSuchElementException.class,
                () -> scenarioService.listPublishedScenariosWorkedOnByUsername("ghost"));
    }

    @Test
    void listPublishedScenariosWorkedOnByUsername_includesAuthoredAndCollaboratedPublishedOnly() {
        User bob = new User();
        bob.setId(11L);
        bob.setUsername("bob");
        when(userService.getUserByUsername("bob")).thenReturn(bob);
        when(scenarioTagService.toNames(anyCollection())).thenReturn(List.of());
        when(userService.getUserById(11L)).thenReturn(bob);
        when(userService.getUserById(7L)).thenReturn(author());

        Scenario authoredPublished = new Scenario();
        authoredPublished.setId(1L);
        authoredPublished.setTitle("Authored + published");
        authoredPublished.setAuthor_id(11L);
        authoredPublished.setAuthor(bob);
        authoredPublished.setCreatedAt(Instant.parse("2025-02-01T00:00:00Z"));
        authoredPublished.setVisibilityStatus(ScenarioVisibilityStatus.PUBLISHED);
        authoredPublished.setTags(Set.of());

        Scenario authoredDraft = new Scenario();
        authoredDraft.setId(2L);
        authoredDraft.setTitle("Authored draft");
        authoredDraft.setAuthor_id(11L);
        authoredDraft.setAuthor(bob);
        authoredDraft.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        authoredDraft.setVisibilityStatus(ScenarioVisibilityStatus.DRAFT);
        authoredDraft.setTags(Set.of());

        Scenario collaboratedPublished = new Scenario();
        collaboratedPublished.setId(3L);
        collaboratedPublished.setTitle("Collaborated + published");
        collaboratedPublished.setAuthor_id(7L);
        collaboratedPublished.setAuthor(author());
        collaboratedPublished.setCreatedAt(Instant.parse("2025-03-01T00:00:00Z"));
        collaboratedPublished.setVisibilityStatus(ScenarioVisibilityStatus.PUBLISHED);
        collaboratedPublished.setTags(Set.of());

        when(scenarioRepository.findAllByAuthorUsernameWithTagsOrderByCreatedAtDesc("bob"))
                .thenReturn(List.of(authoredPublished, authoredDraft));

        ScenarioCollaborator collab = new ScenarioCollaborator();
        collab.setScenarioId(3L);
        when(collaboratorRepo.findByUserIdAndStatus(11L, CollaborationStatus.ACCEPTED))
                .thenReturn(List.of(collab));
        when(scenarioRepository.findAllByIdInWithTags(List.of(3L)))
                .thenReturn(List.of(collaboratedPublished));

        List<ScenarioDto> result = scenarioService.listPublishedScenariosWorkedOnByUsername("bob");

        assertEquals(2, result.size());
        assertEquals("Collaborated + published", result.get(0).title());
        assertEquals("Authored + published", result.get(1).title());
    }

    private Authentication authOf(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "N/A",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()
        );
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

    @Test
    void assertCanDeleteScenario_allowsAuthor() {
        Authentication auth = authOf("alice", "ROLE_USER");

        Scenario scenario = new Scenario();
        scenario.setId(42L);
        scenario.setAuthor_id(1L);

        when(scenarioRepository.existsByIdAndAuthorUsername(42L, "alice")).thenReturn(true);

        assertDoesNotThrow(() -> scenarioService.assertCanDeleteScenario(scenario, auth));
    }

    @Test
    void assertCanDeleteScenario_deniesAcceptedEditorCollaborator() {
        Authentication auth = authOf("bob", "ROLE_USER");

        Scenario scenario = new Scenario();
        scenario.setId(42L);
        scenario.setAuthor_id(1L);

        when(scenarioRepository.existsByIdAndAuthorUsername(42L, "bob")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> scenarioService.assertCanDeleteScenario(scenario, auth));
    }

    @Test
    void assertCanDeleteScenario_deniesUserWithNoRelationToScenario() {
        Authentication auth = authOf("stranger", "ROLE_USER");

        Scenario scenario = new Scenario();
        scenario.setId(42L);
        scenario.setAuthor_id(1L);

        when(scenarioRepository.existsByIdAndAuthorUsername(42L, "stranger")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> scenarioService.assertCanDeleteScenario(scenario, auth));
    }
}