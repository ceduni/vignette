package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.titiplex.persistence.model.Language;
import org.titiplex.persistence.model.LanguageFollow;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.LanguageFollowRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LanguageFollowServiceTest {

    @Mock
    private LanguageFollowRepository repo;
    @Mock
    private UserService userService;
    @Mock
    private LanguageService languageService;

    @InjectMocks
    private LanguageFollowService service;

    private Authentication authAsBob() {
        User bob = new User();
        bob.setId(2L);
        bob.setUsername("bob");
        when(userService.getUserByUsername("bob")).thenReturn(bob);
        when(languageService.getLanguage("fra")).thenReturn(new Language());
        return new UsernamePasswordAuthenticationToken(
                "bob", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void toggleFollow_followsWhenNotFollowing() {
        Authentication auth = authAsBob();
        when(repo.existsByLanguageIdAndUserId("fra", 2L)).thenReturn(false);

        assertTrue(service.toggleFollow("fra", auth));
        verify(repo).saveAndFlush(any(LanguageFollow.class));
    }

    @Test
    void toggleFollow_unfollowsWhenFollowing() {
        Authentication auth = authAsBob();
        when(repo.existsByLanguageIdAndUserId("fra", 2L)).thenReturn(true);

        assertFalse(service.toggleFollow("fra", auth));
        verify(repo).deleteByLanguageIdAndUserId("fra", 2L);
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void toggleFollow_concurrentDuplicateIsTreatedAsFollowing() {
        Authentication auth = authAsBob();
        when(repo.existsByLanguageIdAndUserId("fra", 2L)).thenReturn(false);
        when(repo.saveAndFlush(any(LanguageFollow.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        boolean following = assertDoesNotThrow(() -> service.toggleFollow("fra", auth));

        assertTrue(following);
    }
}
