package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.NotificationRepository;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private Authentication authentication;

    @InjectMocks
    private NotificationService notificationService;

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
}
