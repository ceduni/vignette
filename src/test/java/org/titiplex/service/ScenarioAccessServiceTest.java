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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScenarioAccessServiceTest {

    @Mock
    private ScenarioRepository scenarioRepository;
    @Mock
    private UserService userService;
    @Mock
    private ScenarioCollaboratorRepository collaboratorRepository;
    @Mock
    private NotificationService notificationService;

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

    private ScenarioCollaborator collaborator(long scenarioId, String username, long userId, CollaboratorRole role, CollaborationStatus status) {
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
        return c;
    }

    /** Fork 7 of scenario 6 (written by user 1, "alice"), made by user 2 ("bob") and approved by alice. */
    private Scenario approvedFork(ScenarioVisibilityStatus visibility) {
        Scenario original = scenario(6L, ScenarioVisibilityStatus.PUBLISHED);
        when(scenarioRepository.findById(6L)).thenReturn(Optional.of(original));

        Scenario fork = scenario(7L, visibility);
        fork.setAuthor_id(2L);
        fork.setParentScenarioId(6L);
        fork.setReviewStatus(ReviewStatus.APPROVED);
        fork.setReviewedById(1L);
        fork.setReviewedAt(Instant.now());
        fork.setPublishedAt(visibility == ScenarioVisibilityStatus.PUBLISHED ? Instant.now() : null);
        return fork;
    }

    private void user(String username, long id) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        when(userService.getUserByUsername(username)).thenReturn(user);
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
    void assertCanEditScenario_sendsApprovedForkBackToReview() {
        Scenario fork = approvedFork(ScenarioVisibilityStatus.DRAFT);
        when(scenarioRepository.existsByIdAndAuthorUsername(7L, "bob")).thenReturn(true);
        user("bob", 2L);

        access.assertCanEditScenario(fork, authOf("bob", "ROLE_USER"));

        assertEquals(ReviewStatus.PENDING, fork.getReviewStatus());
        assertNull(fork.getReviewedById());
        assertNull(fork.getReviewedAt());
        verify(scenarioRepository).save(fork);
        verify(notificationService).notifyForkChangedAfterApproval(eq(1L), eq(fork), any(), eq(2L));
    }

    @Test
    void assertCanEditScenario_unpublishesAnApprovedForkEditedAfterPublication() {
        Scenario fork = approvedFork(ScenarioVisibilityStatus.PUBLISHED);
        when(scenarioRepository.existsByIdAndAuthorUsername(7L, "bob")).thenReturn(true);
        user("bob", 2L);

        access.assertCanEditScenario(fork, authOf("bob", "ROLE_USER"));

        assertEquals(ReviewStatus.PENDING, fork.getReviewStatus());
        assertEquals(ScenarioVisibilityStatus.DRAFT, fork.getVisibilityStatus());
        assertNull(fork.getPublishedAt());
    }

    @Test
    void assertCanEditScenario_keepsApprovalWhenTheReviewerEditsTheFork() {
        Scenario fork = approvedFork(ScenarioVisibilityStatus.DRAFT);
        collaborator(7L, "alice", 1L, CollaboratorRole.EDITOR, CollaborationStatus.ACCEPTED);

        access.assertCanEditScenario(fork, authOf("alice", "ROLE_USER"));

        assertEquals(ReviewStatus.APPROVED, fork.getReviewStatus());
        verify(scenarioRepository, never()).save(any());
        verify(notificationService, never()).notifyForkChangedAfterApproval(any(), any(), any(), any());
    }

    @Test
    void assertCanEditScenario_deniedEditLeavesApprovalUntouched() {
        Scenario fork = scenario(7L, ScenarioVisibilityStatus.DRAFT);
        fork.setParentScenarioId(6L);
        fork.setReviewStatus(ReviewStatus.APPROVED);

        assertThrows(AccessDeniedException.class,
                () -> access.assertCanEditScenario(fork, authOf("stranger", "ROLE_USER")));
        assertEquals(ReviewStatus.APPROVED, fork.getReviewStatus());
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

    @Test
    void assertCanViewScenario_invitedUserCanPreviewDraftUntilTheInvitationExpires() {
        collaborator(5L, "bob", 2L, CollaboratorRole.EDITOR, CollaborationStatus.PENDING)
                .setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        assertDoesNotThrow(() -> access.assertCanViewScenario(scenario(5L, ScenarioVisibilityStatus.DRAFT), authOf("bob", "ROLE_USER")));
    }

    @Test
    void assertCanViewScenario_expiredInvitationNoLongerGivesAccess() {
        collaborator(5L, "bob", 2L, CollaboratorRole.EDITOR, CollaborationStatus.PENDING)
                .setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        assertThrows(NoSuchElementException.class,
                () -> access.assertCanViewScenario(scenario(5L, ScenarioVisibilityStatus.DRAFT), authOf("bob", "ROLE_USER")));
    }
}
