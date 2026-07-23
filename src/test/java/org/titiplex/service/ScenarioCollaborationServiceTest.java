package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.titiplex.persistence.model.CollaborationStatus;
import org.titiplex.persistence.model.CollaboratorRole;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioCollaborator;
import org.titiplex.persistence.model.ScenarioInviteLink;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.ScenarioCollaboratorRepository;
import org.titiplex.persistence.repo.ScenarioInviteLinkRepository;
import org.titiplex.persistence.repo.ScenarioRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScenarioCollaborationServiceTest {

    @Mock
    private ScenarioRepository scenarioRepo;
    @Mock
    private ScenarioCollaboratorRepository collaboratorRepo;
    @Mock
    private ScenarioInviteLinkRepository inviteLinkRepo;
    @Mock
    private UserService userService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ScenarioCollaborationService service;

    // ── Helpers ──────────────────────────────────────────────────────────

    private Authentication authAs(String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(username);
        return auth;
    }

    private User userWithId(String username, Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    private Scenario scenarioOwnedBy(Long id, Long ownerId) {
        Scenario scenario = new Scenario();
        scenario.setId(id);
        scenario.setAuthor_id(ownerId);
        scenario.setTitle("Le sc\u00e9nario");
        return scenario;
    }

    private ScenarioCollaborator collaborator(Long scenarioId, Long userId, CollaboratorRole role, CollaborationStatus status) {
        ScenarioCollaborator c = new ScenarioCollaborator();
        c.setId(100L);
        c.setScenarioId(scenarioId);
        c.setUserId(userId);
        c.setRole(role);
        c.setStatus(status);
        return c;
    }

    // ── getUserRole / canEdit / canView ─────────────────────────────────

    @Test
    void getUserRole_returnsOwner_forScenarioAuthor_withoutHittingRepository() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);

        CollaboratorRole role = service.getUserRole(scenario, 5L);

        assertEquals(CollaboratorRole.OWNER, role);
        verifyNoInteractions(collaboratorRepo);
    }

    @Test
    void getUserRole_returnsRole_whenAccepted() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        when(collaboratorRepo.findByScenarioIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(collaborator(1L, 2L, CollaboratorRole.EDITOR, CollaborationStatus.ACCEPTED)));

        assertEquals(CollaboratorRole.EDITOR, service.getUserRole(scenario, 2L));
    }

    @Test
    void getUserRole_returnsNull_whenPending() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        when(collaboratorRepo.findByScenarioIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(collaborator(1L, 2L, CollaboratorRole.EDITOR, CollaborationStatus.PENDING)));

        assertNull(service.getUserRole(scenario, 2L));
    }

    @Test
    void getUserRole_returnsNull_whenNoRelation() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        when(collaboratorRepo.findByScenarioIdAndUserId(1L, 9L)).thenReturn(Optional.empty());

        assertNull(service.getUserRole(scenario, 9L));
    }

    @Test
    void canEdit_trueForEditor_falseForViewer() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        when(collaboratorRepo.findByScenarioIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(collaborator(1L, 2L, CollaboratorRole.EDITOR, CollaborationStatus.ACCEPTED)));
        when(collaboratorRepo.findByScenarioIdAndUserId(1L, 3L))
                .thenReturn(Optional.of(collaborator(1L, 3L, CollaboratorRole.VIEWER, CollaborationStatus.ACCEPTED)));

        assertTrue(service.canEdit(scenario, 2L));
        assertFalse(service.canEdit(scenario, 3L));
    }

    // ── inviteByUsername ─────────────────────────────────────────────────

    @Test
    void inviteByUsername_createsPendingInvitation_andNotifiesTarget() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("owner");

        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("owner")).thenReturn(userWithId("owner", 5L));
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));
        when(collaboratorRepo.existsByScenarioIdAndUserId(1L, 2L)).thenReturn(false);
        when(collaboratorRepo.save(any(ScenarioCollaborator.class))).thenAnswer(inv -> inv.getArgument(0));

        ScenarioCollaborator result = service.inviteByUsername(1L, "bob", CollaboratorRole.EDITOR, auth);

        assertEquals(CollaborationStatus.PENDING, result.getStatus());
        assertEquals(CollaboratorRole.EDITOR, result.getRole());
        assertEquals(2L, result.getUserId());
        assertEquals(5L, result.getInvitedById());
        verify(notificationService).notifyCollaborationInvite(2L, scenario, 5L, "EDITOR");
    }

    @Test
    void inviteByUsername_throwsAccessDenied_whenCallerIsNotOwner() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("notowner");

        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("notowner")).thenReturn(userWithId("notowner", 99L));

        assertThrows(AccessDeniedException.class,
                () -> service.inviteByUsername(1L, "bob", CollaboratorRole.EDITOR, auth));
        verifyNoInteractions(notificationService);
    }

    @Test
    void inviteByUsername_throwsIllegalArgument_whenRoleIsOwner() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("owner");

        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("owner")).thenReturn(userWithId("owner", 5L));

        assertThrows(IllegalArgumentException.class,
                () -> service.inviteByUsername(1L, "bob", CollaboratorRole.OWNER, auth));
    }

    @Test
    void inviteByUsername_throwsIllegalState_whenAlreadyCollaboratorOrInvited() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("owner");

        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("owner")).thenReturn(userWithId("owner", 5L));
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));
        when(collaboratorRepo.existsByScenarioIdAndUserId(1L, 2L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.inviteByUsername(1L, "bob", CollaboratorRole.EDITOR, auth));
    }

    @Test
    void inviteByUsername_throwsIllegalArgument_whenTargetIsAlreadyOwner() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("owner");

        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("owner")).thenReturn(userWithId("owner", 5L));
        when(userService.getUserByUsername("owner-again")).thenReturn(userWithId("owner-again", 5L));

        assertThrows(IllegalArgumentException.class,
                () -> service.inviteByUsername(1L, "owner-again", CollaboratorRole.EDITOR, auth));
    }

    // ── respondToInvitation ──────────────────────────────────────────────

    @Test
    void respondToInvitation_accept_marksAcceptedAndNotifiesOwner() {
        Authentication auth = authAs("bob");
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));

        ScenarioCollaborator pending = collaborator(1L, 2L, CollaboratorRole.EDITOR, CollaborationStatus.PENDING);
        when(collaboratorRepo.findById(100L)).thenReturn(Optional.of(pending));
        when(collaboratorRepo.save(any(ScenarioCollaborator.class))).thenAnswer(inv -> inv.getArgument(0));

        Scenario scenario = scenarioOwnedBy(1L, 5L);
        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));

        ScenarioCollaborator result = service.respondToInvitation(100L, true, auth);

        assertEquals(CollaborationStatus.ACCEPTED, result.getStatus());
        assertNotNull(result.getRespondedAt());
        verify(notificationService).notifyInviteAccepted(5L, scenario, 2L);
    }

    @Test
    void respondToInvitation_decline_doesNotNotify() {
        Authentication auth = authAs("bob");
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));

        ScenarioCollaborator pending = collaborator(1L, 2L, CollaboratorRole.EDITOR, CollaborationStatus.PENDING);
        when(collaboratorRepo.findById(100L)).thenReturn(Optional.of(pending));
        when(collaboratorRepo.save(any(ScenarioCollaborator.class))).thenAnswer(inv -> inv.getArgument(0));

        ScenarioCollaborator result = service.respondToInvitation(100L, false, auth);

        assertEquals(CollaborationStatus.DECLINED, result.getStatus());
        verifyNoInteractions(notificationService);
        verifyNoInteractions(scenarioRepo);
    }

    @Test
    void respondToInvitation_throwsAccessDenied_whenInvitationBelongsToSomeoneElse() {
        Authentication auth = authAs("eve");
        when(userService.getUserByUsername("eve")).thenReturn(userWithId("eve", 999L));

        ScenarioCollaborator pending = collaborator(1L, 2L, CollaboratorRole.EDITOR, CollaborationStatus.PENDING);
        when(collaboratorRepo.findById(100L)).thenReturn(Optional.of(pending));

        assertThrows(AccessDeniedException.class, () -> service.respondToInvitation(100L, true, auth));
    }

    @Test
    void respondToInvitation_throwsIllegalState_whenAlreadyAnswered() {
        Authentication auth = authAs("bob");
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));

        ScenarioCollaborator alreadyAccepted = collaborator(1L, 2L, CollaboratorRole.EDITOR, CollaborationStatus.ACCEPTED);
        when(collaboratorRepo.findById(100L)).thenReturn(Optional.of(alreadyAccepted));

        assertThrows(IllegalStateException.class, () -> service.respondToInvitation(100L, true, auth));
    }

    @Test
    void respondToInvitation_throwsNoSuchElement_whenInvitationMissing() {
        Authentication auth = authAs("bob");
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));
        when(collaboratorRepo.findById(404L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.respondToInvitation(404L, true, auth));
    }

    // ── removeCollaborator / changeRole ─────────────────────────────────

    @Test
    void removeCollaborator_deletesRow_whenCalledByOwner() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("owner");

        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("owner")).thenReturn(userWithId("owner", 5L));

        service.removeCollaborator(1L, 2L, auth);

        verify(collaboratorRepo).deleteByScenarioIdAndUserId(1L, 2L);
    }

    @Test
    void removeCollaborator_throwsIllegalArgument_whenTargetIsOwner() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("owner");

        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("owner")).thenReturn(userWithId("owner", 5L));

        assertThrows(IllegalArgumentException.class, () -> service.removeCollaborator(1L, 5L, auth));
        verify(collaboratorRepo, never()).deleteByScenarioIdAndUserId(any(), any());
    }

    @Test
    void removeCollaborator_throwsAccessDenied_whenCallerIsNotOwner() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("notowner");

        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("notowner")).thenReturn(userWithId("notowner", 99L));

        assertThrows(AccessDeniedException.class, () -> service.removeCollaborator(1L, 2L, auth));
    }

    @Test
    void changeRole_updatesRole_whenCalledByOwner() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("owner");

        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("owner")).thenReturn(userWithId("owner", 5L));

        ScenarioCollaborator existing = collaborator(1L, 2L, CollaboratorRole.VIEWER, CollaborationStatus.ACCEPTED);
        when(collaboratorRepo.findByScenarioIdAndUserId(1L, 2L)).thenReturn(Optional.of(existing));
        when(collaboratorRepo.save(any(ScenarioCollaborator.class))).thenAnswer(inv -> inv.getArgument(0));

        ScenarioCollaborator result = service.changeRole(1L, 2L, CollaboratorRole.EDITOR, auth);

        assertEquals(CollaboratorRole.EDITOR, result.getRole());
    }

    @Test
    void changeRole_throwsIllegalArgument_whenAssigningOwnerRole() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("owner");

        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("owner")).thenReturn(userWithId("owner", 5L));

        assertThrows(IllegalArgumentException.class,
                () -> service.changeRole(1L, 2L, CollaboratorRole.OWNER, auth));
    }

    @Test
    void changeRole_throwsIllegalArgument_whenTargetIsScenarioOwner() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("owner");

        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("owner")).thenReturn(userWithId("owner", 5L));

        assertThrows(IllegalArgumentException.class,
                () -> service.changeRole(1L, 5L, CollaboratorRole.EDITOR, auth));
    }

    // ── Invite links ─────────────────────────────────────────────────────

    @Test
    void createInviteLink_generatesTokenAndPersistsLink() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("owner");

        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("owner")).thenReturn(userWithId("owner", 5L));
        when(inviteLinkRepo.save(any(ScenarioInviteLink.class))).thenAnswer(inv -> inv.getArgument(0));

        ScenarioInviteLink link = service.createInviteLink(1L, CollaboratorRole.EDITOR, null, auth);

        assertNotNull(link.getToken());
        assertEquals(32, link.getToken().length());
        assertEquals(CollaboratorRole.EDITOR, link.getRole());
        assertEquals(5L, link.getCreatedById());
        assertTrue(link.isActive());
        assertNotNull(link.getExpiresAt());
        assertTrue(link.getExpiresAt().isAfter(Instant.now().plusSeconds(6 * 24 * 3600)));
        assertTrue(link.getExpiresAt().isBefore(Instant.now().plusSeconds(8 * 24 * 3600)));
    }

    @Test
    void createInviteLink_throwsIllegalArgument_whenRoleIsOwner() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("owner");

        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("owner")).thenReturn(userWithId("owner", 5L));

        assertThrows(IllegalArgumentException.class,
                () -> service.createInviteLink(1L, CollaboratorRole.OWNER, null, auth));
    }

    @Test
    void revokeInviteLink_setsActiveFalse() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("owner");

        ScenarioInviteLink link = new ScenarioInviteLink();
        link.setId(7L);
        link.setScenarioId(1L);
        link.setActive(true);

        when(inviteLinkRepo.findById(7L)).thenReturn(Optional.of(link));
        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(userService.getUserByUsername("owner")).thenReturn(userWithId("owner", 5L));

        service.revokeInviteLink(7L, auth);

        ArgumentCaptor<ScenarioInviteLink> captor = ArgumentCaptor.forClass(ScenarioInviteLink.class);
        verify(inviteLinkRepo).save(captor.capture());
        assertFalse(captor.getValue().isActive());
    }

    @Test
    void joinViaLink_createsAcceptedCollaborator_forNewUser() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("bob");

        ScenarioInviteLink link = new ScenarioInviteLink();
        link.setId(7L);
        link.setScenarioId(1L);
        link.setToken("abc123");
        link.setRole(CollaboratorRole.EDITOR);
        link.setCreatedById(5L);
        link.setActive(true);
        link.setUseCount(0);

        when(inviteLinkRepo.findByToken("abc123")).thenReturn(Optional.of(link));
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));
        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(collaboratorRepo.findByScenarioIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(collaboratorRepo.save(any(ScenarioCollaborator.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inviteLinkRepo.save(any(ScenarioInviteLink.class))).thenAnswer(inv -> inv.getArgument(0));

        ScenarioCollaborator result = service.joinViaLink("abc123", auth);

        assertEquals(CollaborationStatus.ACCEPTED, result.getStatus());
        assertEquals(CollaboratorRole.EDITOR, result.getRole());
        assertEquals(2L, result.getUserId());
        verify(notificationService).notifyInviteAccepted(5L, scenario, 2L);
    }

    @Test
    void joinViaLink_throwsIllegalState_whenLinkInactive() {
        Authentication auth = authAs("bob");
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));

        ScenarioInviteLink link = new ScenarioInviteLink();
        link.setToken("abc123");
        link.setActive(false);

        when(inviteLinkRepo.findByToken("abc123")).thenReturn(Optional.of(link));

        assertThrows(IllegalStateException.class, () -> service.joinViaLink("abc123", auth));
    }

    @Test
    void joinViaLink_throwsIllegalState_whenLinkExpired() {
        Authentication auth = authAs("bob");
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));

        ScenarioInviteLink link = new ScenarioInviteLink();
        link.setToken("abc123");
        link.setActive(true);
        link.setExpiresAt(Instant.now().minusSeconds(60));

        when(inviteLinkRepo.findByToken("abc123")).thenReturn(Optional.of(link));

        assertThrows(IllegalStateException.class, () -> service.joinViaLink("abc123", auth));
    }

    @Test
    void joinViaLink_throwsIllegalState_whenMaxUsesReached() {
        Authentication auth = authAs("bob");
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));

        ScenarioInviteLink link = new ScenarioInviteLink();
        link.setToken("abc123");
        link.setActive(true);
        link.setMaxUses(3);
        link.setUseCount(3);

        when(inviteLinkRepo.findByToken("abc123")).thenReturn(Optional.of(link));

        assertThrows(IllegalStateException.class, () -> service.joinViaLink("abc123", auth));
    }

    @Test
    void joinViaLink_throwsIllegalArgument_whenOwnerTriesToJoinOwnScenario() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("owner");

        ScenarioInviteLink link = new ScenarioInviteLink();
        link.setScenarioId(1L);
        link.setToken("abc123");
        link.setActive(true);

        when(inviteLinkRepo.findByToken("abc123")).thenReturn(Optional.of(link));
        when(userService.getUserByUsername("owner")).thenReturn(userWithId("owner", 5L));
        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));

        assertThrows(IllegalArgumentException.class, () -> service.joinViaLink("abc123", auth));
    }

    @Test
    void joinViaLink_isIdempotent_whenAlreadyAcceptedCollaborator() {
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        Authentication auth = authAs("bob");

        ScenarioInviteLink link = new ScenarioInviteLink();
        link.setScenarioId(1L);
        link.setToken("abc123");
        link.setRole(CollaboratorRole.EDITOR);
        link.setActive(true);

        ScenarioCollaborator existing = collaborator(1L, 2L, CollaboratorRole.EDITOR, CollaborationStatus.ACCEPTED);

        when(inviteLinkRepo.findByToken("abc123")).thenReturn(Optional.of(link));
        when(userService.getUserByUsername("bob")).thenReturn(userWithId("bob", 2L));
        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));
        when(collaboratorRepo.findByScenarioIdAndUserId(1L, 2L)).thenReturn(Optional.of(existing));

        ScenarioCollaborator result = service.joinViaLink("abc123", auth);

        assertSame(existing, result);
        verify(collaboratorRepo, never()).save(any());
        verify(inviteLinkRepo, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    // ── Auth guard ───────────────────────────────────────────────────────

    @Test
    void inviteByUsername_throwsInsufficientAuthentication_whenNotLoggedIn() {
        // requireScenario() runs before the auth check, so the scenario lookup must
        // succeed for the InsufficientAuthenticationException to actually be reached.
        Scenario scenario = scenarioOwnedBy(1L, 5L);
        when(scenarioRepo.findById(1L)).thenReturn(Optional.of(scenario));

        Authentication unauthenticated = mock(Authentication.class);
        when(unauthenticated.isAuthenticated()).thenReturn(false);

        assertThrows(InsufficientAuthenticationException.class,
                () -> service.inviteByUsername(1L, "bob", CollaboratorRole.EDITOR, unauthenticated));
    }
}
