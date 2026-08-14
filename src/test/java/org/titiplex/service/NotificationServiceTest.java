package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.titiplex.persistence.model.CollaborationStatus;
import org.titiplex.persistence.model.CollaboratorRole;
import org.titiplex.persistence.model.DiscussionMessage;
import org.titiplex.persistence.model.DiscussionTargetType;
import org.titiplex.persistence.model.Notification;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioCollaborator;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.NotificationRepository;
import org.titiplex.persistence.repo.ScenarioCollaboratorRepository;
import org.titiplex.persistence.repo.ScenarioRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repo;
    @Mock
    private UserService userService;
    @Mock
    private LanguageFollowService languageFollowService;
    @Mock
    private LanguageService languageService;
    @Mock
    private ScenarioRepository scenarioRepository;
    @Mock
    private ScenarioCollaboratorRepository scenarioCollaboratorRepository;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private NotificationService notificationService;

    private static User userWithId(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    @Test
    void deleteAllNotifications_deletesForAuthenticatedUser() {
        User user = new User();
        user.setId(42L);
        user.setUsername("bob");

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("bob");
        when(userService.getUserByUsername("bob")).thenReturn(user);

        notificationService.deleteAllNotifications(authentication);

        verify(repo).deleteAllByUserId(42L);
    }

    @Test
    void deleteAllNotifications_throwsWhenNotAuthenticated() {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThrows(InsufficientAuthenticationException.class,
                () -> notificationService.deleteAllNotifications(authentication));

        verifyNoInteractions(repo);
    }

    @Test
    void notifyDiscussionMessage_notifiesParentAuthorOnReply() {
        User alice = userWithId(1L, "alice");
        User bob = userWithId(2L, "bob");
        when(userService.getUserById(1L)).thenReturn(alice);
        when(userService.getUserById(2L)).thenReturn(bob);

        DiscussionMessage parent = new DiscussionMessage();
        parent.setId(50L);
        parent.setAuthorId(2L);
        parent.setTargetType(DiscussionTargetType.LANGUAGE);
        parent.setTargetId("chuj");

        DiscussionMessage reply = new DiscussionMessage();
        reply.setId(100L);
        reply.setAuthorId(1L);
        reply.setTargetType(DiscussionTargetType.LANGUAGE);
        reply.setTargetId("chuj");

        notificationService.notifyDiscussionMessage(reply, parent);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo, times(1)).save(captor.capture());

        Notification notif = captor.getValue();
        assertEquals(bob, notif.getUser());
        assertEquals("COMMENT_REPLY", notif.getType());
        assertEquals("alice replied to your comment", notif.getMessage());
        assertEquals("/languages/chuj", notif.getTargetUrl());
        assertEquals(100L, notif.getReferenceId());
    }

    @Test
    void notifyDiscussionMessage_doesNotNotifyReplierForTheirOwnParentComment() {
        User alice = userWithId(1L, "alice");
        when(userService.getUserById(1L)).thenReturn(alice);

        DiscussionMessage parent = new DiscussionMessage();
        parent.setId(50L);
        parent.setAuthorId(1L);
        parent.setTargetType(DiscussionTargetType.LANGUAGE);
        parent.setTargetId("chuj");

        DiscussionMessage reply = new DiscussionMessage();
        reply.setId(100L);
        reply.setAuthorId(1L);
        reply.setTargetType(DiscussionTargetType.LANGUAGE);
        reply.setTargetId("chuj");

        notificationService.notifyDiscussionMessage(reply, parent);

        verify(repo, never()).save(any());
    }

    @Test
    void notifyDiscussionMessage_notifiesScenarioOwnerAndCollaborators_excludingCommenter() {
        User alice = userWithId(1L, "alice");
        User bob = userWithId(2L, "bob");
        User carol = userWithId(3L, "carol");
        when(userService.getUserById(1L)).thenReturn(alice);
        when(userService.getUserById(2L)).thenReturn(bob);
        when(userService.getUserById(3L)).thenReturn(carol);

        Scenario scenario = new Scenario();
        scenario.setId(12L);
        scenario.setTitle("My scenario");
        scenario.setAuthor_id(2L);

        ScenarioCollaborator collaborator = new ScenarioCollaborator();
        collaborator.setScenarioId(12L);
        collaborator.setUserId(3L);
        collaborator.setRole(CollaboratorRole.EDITOR);
        collaborator.setStatus(CollaborationStatus.ACCEPTED);

        when(scenarioRepository.findById(12L)).thenReturn(Optional.of(scenario));
        when(scenarioCollaboratorRepository.findByScenarioIdAndStatus(12L, CollaborationStatus.ACCEPTED))
                .thenReturn(List.of(collaborator));

        DiscussionMessage message = new DiscussionMessage();
        message.setId(200L);
        message.setAuthorId(1L);
        message.setTargetType(DiscussionTargetType.SCENARIO);
        message.setTargetId("12");

        notificationService.notifyDiscussionMessage(message, null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo, times(2)).save(captor.capture());

        java.util.Set<User> notifiedUsers = new java.util.HashSet<>(
                captor.getAllValues().stream().map(Notification::getUser).toList());
        assertEquals(java.util.Set.of(bob, carol), notifiedUsers);
        for (Notification notif : captor.getAllValues()) {
            assertEquals("NEW_COMMENT_ON_SCENARIO", notif.getType());
            assertEquals("alice commented on \"My scenario\"", notif.getMessage());
            assertEquals("/scenarios/12", notif.getTargetUrl());
        }
    }

    @Test
    void notifyDiscussionMessage_doesNotNotifyOwnerCommentingOnTheirOwnScenario() {
        User alice = userWithId(1L, "alice");
        when(userService.getUserById(1L)).thenReturn(alice);

        Scenario scenario = new Scenario();
        scenario.setId(12L);
        scenario.setTitle("My scenario");
        scenario.setAuthor_id(1L);

        when(scenarioRepository.findById(12L)).thenReturn(Optional.of(scenario));
        when(scenarioCollaboratorRepository.findByScenarioIdAndStatus(12L, CollaborationStatus.ACCEPTED))
                .thenReturn(List.of());

        DiscussionMessage message = new DiscussionMessage();
        message.setId(200L);
        message.setAuthorId(1L);
        message.setTargetType(DiscussionTargetType.SCENARIO);
        message.setTargetId("12");

        notificationService.notifyDiscussionMessage(message, null);

        verify(repo, never()).save(any());
    }

    @Test
    void notifyDiscussionMessage_doesNotDoublyNotifyParentAuthorWhoIsAlsoScenarioOwner() {
        User alice = userWithId(1L, "alice");
        User bob = userWithId(2L, "bob");
        when(userService.getUserById(1L)).thenReturn(alice);
        when(userService.getUserById(2L)).thenReturn(bob);

        Scenario scenario = new Scenario();
        scenario.setId(12L);
        scenario.setTitle("My scenario");
        scenario.setAuthor_id(2L);

        when(scenarioRepository.findById(12L)).thenReturn(Optional.of(scenario));
        when(scenarioCollaboratorRepository.findByScenarioIdAndStatus(12L, CollaborationStatus.ACCEPTED))
                .thenReturn(List.of());

        DiscussionMessage parent = new DiscussionMessage();
        parent.setId(199L);
        parent.setAuthorId(2L);
        parent.setTargetType(DiscussionTargetType.SCENARIO);
        parent.setTargetId("12");

        DiscussionMessage reply = new DiscussionMessage();
        reply.setId(200L);
        reply.setAuthorId(1L);
        reply.setTargetType(DiscussionTargetType.SCENARIO);
        reply.setTargetId("12");

        notificationService.notifyDiscussionMessage(reply, parent);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo, times(1)).save(captor.capture());
        assertEquals("COMMENT_REPLY", captor.getValue().getType());
        assertEquals(bob, captor.getValue().getUser());
    }
}
