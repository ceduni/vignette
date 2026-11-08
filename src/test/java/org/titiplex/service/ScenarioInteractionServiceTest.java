package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.titiplex.api.dto.ScenarioInteractionStatusDto;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioBookmark;
import org.titiplex.persistence.model.ScenarioLike;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.ScenarioBookmarkRepository;
import org.titiplex.persistence.repo.ScenarioLikeRepository;
import org.titiplex.persistence.repo.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScenarioInteractionServiceTest {

    @Mock
    private ScenarioService scenarioService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ScenarioLikeRepository likeRepository;
    @Mock
    private ScenarioBookmarkRepository bookmarkRepository;

    @InjectMocks
    private ScenarioInteractionService service;

    private static Authentication authAs(String username) {
        return new UsernamePasswordAuthenticationToken(
                username, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private Scenario scenario(Long id) {
        Scenario scenario = new Scenario();
        scenario.setId(id);
        return scenario;
    }

    private User user(String username) {
        User user = new User();
        user.setId(2L);
        user.setUsername(username);
        return user;
    }

    @Test
    void likeScenario_rejectsScenarioTheUserCannotSee() {
        Authentication auth = authAs("bob");
        when(scenarioService.getVisibleScenario(1L, auth)).thenThrow(new AccessDeniedException("private"));

        assertThrows(AccessDeniedException.class, () -> service.likeScenario(1L, auth));
        verify(likeRepository, never()).saveAndFlush(any());
    }

    @Test
    void bookmarkScenario_rejectsScenarioTheUserCannotSee() {
        Authentication auth = authAs("bob");
        when(scenarioService.getVisibleScenario(1L, auth)).thenThrow(new AccessDeniedException("private"));

        assertThrows(AccessDeniedException.class, () -> service.bookmarkScenario(1L, auth));
        verify(bookmarkRepository, never()).saveAndFlush(any());
    }

    @Test
    void getStatus_rejectsScenarioTheUserCannotSee() {
        Authentication auth = authAs("bob");
        when(scenarioService.getVisibleScenario(1L, auth)).thenThrow(new AccessDeniedException("private"));

        assertThrows(AccessDeniedException.class, () -> service.getStatus(1L, auth));
    }

    @Test
    void likeScenario_savesLikeOnVisibleScenario() {
        Authentication auth = authAs("bob");
        Scenario scenario = scenario(1L);
        User bob = user("bob");
        when(scenarioService.getVisibleScenario(1L, auth)).thenReturn(scenario);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(likeRepository.existsByScenarioAndUser(scenario, bob)).thenReturn(false, true);
        when(likeRepository.countByScenario(scenario)).thenReturn(1L);

        ScenarioInteractionStatusDto status = service.likeScenario(1L, auth);

        verify(likeRepository).saveAndFlush(any(ScenarioLike.class));
        assertTrue(status.liked());
        assertEquals(1L, status.likeCount());
    }

    @Test
    void likeScenario_concurrentDuplicateIsTreatedAsAlreadyLiked() {
        Authentication auth = authAs("bob");
        Scenario scenario = scenario(1L);
        User bob = user("bob");
        when(scenarioService.getVisibleScenario(1L, auth)).thenReturn(scenario);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        // The check says "not liked yet", but another request wins the insert.
        when(likeRepository.existsByScenarioAndUser(scenario, bob)).thenReturn(false, true);
        when(likeRepository.saveAndFlush(any(ScenarioLike.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(likeRepository.countByScenario(scenario)).thenReturn(1L);

        ScenarioInteractionStatusDto status = assertDoesNotThrow(() -> service.likeScenario(1L, auth));

        assertTrue(status.liked());
        assertEquals(1L, status.likeCount());
    }

    @Test
    void bookmarkScenario_concurrentDuplicateIsTreatedAsAlreadyBookmarked() {
        Authentication auth = authAs("bob");
        Scenario scenario = scenario(1L);
        User bob = user("bob");
        when(scenarioService.getVisibleScenario(1L, auth)).thenReturn(scenario);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(bookmarkRepository.existsByScenarioAndUser(scenario, bob)).thenReturn(false, true);
        when(bookmarkRepository.saveAndFlush(any(ScenarioBookmark.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        ScenarioInteractionStatusDto status = assertDoesNotThrow(() -> service.bookmarkScenario(1L, auth));

        assertTrue(status.bookmarked());
    }
}
