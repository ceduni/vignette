package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.titiplex.persistence.model.*;
import org.titiplex.persistence.repo.ScenarioCollaboratorRepository;
import org.titiplex.persistence.repo.ScenarioRepository;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScenarioAccessServiceTest {

    @Mock
    private ScenarioRepository scenarioRepository;
    @Mock
    private UserService userService;
    @Mock
    private ScenarioCollaboratorRepository collaboratorRepository;

    @InjectMocks
    private ScenarioAccessService access;

    private static Authentication authOf(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(username, "N/A",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private static Scenario scenario(long id, ScenarioVisibilityStatus visibility) {
        Scenario scenario = new Scenario();
        scenario.setId(id);
        scenario.setAuthor_id(1L);
        scenario.setVisibilityStatus(visibility);
        return scenario;
    }

    private void collaborator(long scenarioId, String username, long userId, CollaboratorRole role, CollaborationStatus status) {
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        when(userService.getUserByUsername(username)).thenReturn(user);
        ScenarioCollaborator c = new ScenarioCollaborator();
        c.setScenarioId(scenarioId);
        c.setUserId(userId);
        c.setRole(role);
        c.setStatus(status);
        when(collaboratorRepository.findByScenarioIdAndUserId(scenarioId, userId)).thenReturn(Optional.of(c));
    }

    // ── edit ───────────────────────────────────────────────────────────

    @Test
    void hasEditAccess_trueForAuthorEvenOncePublished() {
        when(scenarioRepository.existsByIdAndAuthorUsername(3L, "alice")).thenReturn(true);
        assertTrue(access.hasEditAccess(3L, "alice"));
    }

    @Test
    void hasEditAccess_trueForAcceptedEditor_falseForViewer() {
        collaborator(3L, "bob", 2L, CollaboratorRole.EDITOR, CollaborationStatus.ACCEPTED);
        collaborator(3L, "carol", 4L, CollaboratorRole.VIEWER, CollaborationStatus.ACCEPTED);
        assertTrue(access.hasEditAccess(3L, "bob"));
        assertFalse(access.hasEditAccess(3L, "carol"));
    }

    @Test
    void hasEditAccess_falseForPendingEditor() {
        collaborator(3L, "bob", 2L, CollaboratorRole.EDITOR, CollaborationStatus.PENDING);
        assertFalse(access.hasEditAccess(3L, "bob"));
    }

    @Test
    void assertCanEditScenario_allowsAuthorOncePublished() {
        when(scenarioRepository.existsByIdAndAuthorUsername(3L, "alice")).thenReturn(true);
        assertDoesNotThrow(() -> access.assertCanEditScenario(scenario(3L, ScenarioVisibilityStatus.PUBLISHED), authOf("alice", "ROLE_USER")));
    }

    @Test
    void assertCanEditScenario_allowsAdminEvenWhenPublished() {
        assertDoesNotThrow(() -> access.assertCanEditScenario(scenario(3L, ScenarioVisibilityStatus.PUBLISHED), authOf("admin", "ROLE_ADMIN")));
    }

    @Test
    void assertCanEditScenario_deniesStranger() {
        assertThrows(AccessDeniedException.class,
                () -> access.assertCanEditScenario(scenario(3L, ScenarioVisibilityStatus.DRAFT), authOf("stranger", "ROLE_USER")));
    }

    @Test
    void assertCanEditScenarioById_reportsMissingScenarioAsNotFound() {
        when(scenarioRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> access.assertCanEditScenarioById(99L, authOf("alice", "ROLE_USER")));
    }

    // ── delete / publish: author only ──────────────────────────────────

    @Test
    void assertCanDeleteScenario_allowsAuthor() {
        when(scenarioRepository.existsByIdAndAuthorUsername(42L, "alice")).thenReturn(true);
        assertDoesNotThrow(() -> access.assertCanDeleteScenario(scenario(42L, ScenarioVisibilityStatus.DRAFT), authOf("alice", "ROLE_USER")));
    }

    @Test
    void assertCanDeleteScenario_deniesAcceptedEditorCollaborator() {
        when(scenarioRepository.existsByIdAndAuthorUsername(42L, "bob")).thenReturn(false);
        assertThrows(AccessDeniedException.class,
                () -> access.assertCanDeleteScenario(scenario(42L, ScenarioVisibilityStatus.DRAFT), authOf("bob", "ROLE_USER")));
    }

    @Test
    void assertCanPublishScenario_deniesNonAuthor() {
        when(scenarioRepository.existsByIdAndAuthorUsername(42L, "bob")).thenReturn(false);
        assertThrows(AccessDeniedException.class,
                () -> access.assertCanPublishScenario(scenario(42L, ScenarioVisibilityStatus.DRAFT), authOf("bob", "ROLE_USER")));
    }

    // ── view ───────────────────────────────────────────────────────────

    @Test
    void assertCanViewScenario_publishedIsPublic() {
        assertDoesNotThrow(() -> access.assertCanViewScenario(scenario(5L, ScenarioVisibilityStatus.PUBLISHED), null));
    }

    @Test
    void assertCanViewScenario_draftHiddenFromStrangerAsNotFound() {
        when(userService.getUserByUsername("stranger")).thenReturn(null);
        assertThrows(NoSuchElementException.class,
                () -> access.assertCanViewScenario(scenario(5L, ScenarioVisibilityStatus.DRAFT), authOf("stranger", "ROLE_USER")));
    }

    @Test
    void assertCanViewScenario_invitedUserCanPreviewDraft() {
        collaborator(5L, "bob", 2L, CollaboratorRole.EDITOR, CollaborationStatus.PENDING);
        assertDoesNotThrow(() -> access.assertCanViewScenario(scenario(5L, ScenarioVisibilityStatus.DRAFT), authOf("bob", "ROLE_USER")));
    }
}
