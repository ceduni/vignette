package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.titiplex.persistence.model.*;
import org.titiplex.persistence.repo.AudioRepository;
import org.titiplex.persistence.repo.ScenarioRepository;
import org.titiplex.persistence.repo.ThumbnailRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScenarioServiceForkTest {

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
    private ThumbnailRepository thumbnailRepository;
    @Mock
    private AudioRepository audioRepository;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private ScenarioService scenarioService;

    private Scenario publishedScenario(long id) {
        Language language = new Language();
        language.setId("fra");

        Scenario scenario = new Scenario();
        scenario.setId(id);
        scenario.setTitle("Original scenario");
        scenario.setDescription("Original description");
        scenario.setAuthor_id(7L);
        scenario.setLanguage_id("fra");
        scenario.setLanguage(language);
        scenario.setVisibilityStatus(ScenarioVisibilityStatus.PUBLISHED);
        scenario.setStoryboardLayoutMode(StoryboardLayoutMode.PRESET);
        scenario.setStoryboardPreset("GRID_3");
        scenario.setStoryboardColumns(3);
        scenario.setTags(Set.of());
        return scenario;
    }

    private User bob() {
        User user = new User();
        user.setId(42L);
        user.setUsername("bob");
        return user;
    }

    private Thumbnail thumbnail(long id, long scenarioId, int idx) {
        Thumbnail t = new Thumbnail();
        t.setId(id);
        t.setTitle("thumb-" + id);
        t.setIdx(idx);
        t.setContentType("image/png");
        t.setAuthorId(7L);
        t.setScenarioId(scenarioId);
        t.setStoragePath("thumbnails/scenario-" + scenarioId + "/ab/cd/hash" + id + ".png");
        t.setSizeBytes(1024L);
        t.setOriginalFilename("original.png");
        t.setImageSha256("hash" + id);
        t.setGridColumn(0);
        t.setGridRow(0);
        t.setGridColumnSpan(1);
        t.setGridRowSpan(1);
        t.setImageWidth(800);
        t.setImageHeight(600);
        return t;
    }

    private Audio audio(long id, long scenarioId, long thumbnailId, int idx) {
        Audio a = new Audio();
        a.setId(id);
        a.setStoragePath("audios/scenario-" + scenarioId + "/thumbnail-" + thumbnailId + "/hash" + id + ".webm");
        a.setSizeBytes(2048L);
        a.setOriginalFilename("recording.webm");
        a.setAudioSha256("audiohash" + id);
        a.setTitle("audio-" + id);
        a.setIdx(idx);
        a.setMime("audio/webm");
        a.setAuthorId(7L);
        a.setScenarioId(scenarioId);
        a.setLanguageId("fra");
        a.setThumbnailId(thumbnailId);
        a.setMarkerX(0.5);
        a.setMarkerY(0.3);
        a.setMarkerLabel("marker");
        return a;
    }

    private void mockSaveAssignsId(long assignedId) {
        when(scenarioRepository.save(any(Scenario.class))).thenAnswer(invocation -> {
            Scenario s = invocation.getArgument(0);
            s.setId(assignedId);
            return s;
        });
    }

    @Test
    void forkScenario_copiesThumbnailsAndAudiosReusingSameFiles() {
        Scenario original = publishedScenario(1L);
        Thumbnail originalThumb = thumbnail(101L, 1L, 0);
        Audio originalAudio = audio(301L, 1L, 101L, 0);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("bob");
        when(userService.getUserByUsername("bob")).thenReturn(bob());
        when(userService.getUserById(42L)).thenReturn(bob());
        when(scenarioRepository.findByIdWithTags(1L)).thenReturn(Optional.of(original));
        when(scenarioRepository.existsByTitleAndAuthorUsernameAndLanguageId(anyString(), anyString(), anyString())).thenReturn(false);
        mockSaveAssignsId(99L);

        when(thumbnailRepository.findByScenarioIdOrderByIdxAsc(1L)).thenReturn(List.of(originalThumb));
        when(thumbnailRepository.save(any(Thumbnail.class))).thenAnswer(invocation -> {
            Thumbnail t = invocation.getArgument(0);
            t.setId(201L);
            return t;
        });
        when(audioRepository.findByThumbnailIdOrderByIdxAsc(101L)).thenReturn(List.of(originalAudio));
        when(audioRepository.save(any(Audio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Scenario fork = scenarioService.forkScenario(1L, null, authentication);

        assertEquals(99L, fork.getId());
        assertEquals(1L, fork.getParentScenarioId());
        assertEquals(42L, fork.getAuthor_id());
        assertEquals(ScenarioVisibilityStatus.DRAFT, fork.getVisibilityStatus());

        ArgumentCaptor<Thumbnail> thumbCaptor = ArgumentCaptor.forClass(Thumbnail.class);
        verify(thumbnailRepository).save(thumbCaptor.capture());
        Thumbnail savedThumb = thumbCaptor.getValue();
        assertEquals(99L, savedThumb.getScenarioId());
        assertEquals(42L, savedThumb.getAuthorId());
        assertEquals(originalThumb.getImageSha256(), savedThumb.getImageSha256());
        assertEquals(originalThumb.getStoragePath(), savedThumb.getStoragePath());

        ArgumentCaptor<Audio> audioCaptor = ArgumentCaptor.forClass(Audio.class);
        verify(audioRepository).save(audioCaptor.capture());
        Audio savedAudio = audioCaptor.getValue();
        assertEquals(99L, savedAudio.getScenarioId());
        assertEquals(42L, savedAudio.getAuthorId());
        assertEquals(201L, savedAudio.getThumbnailId());
        assertEquals(originalAudio.getAudioSha256(), savedAudio.getAudioSha256());
        assertEquals(originalAudio.getStoragePath(), savedAudio.getStoragePath());
    }

    @Test
    void forkScenario_throwsWhenScenarioNotPublished() {
        Scenario draft = publishedScenario(2L);
        draft.setVisibilityStatus(ScenarioVisibilityStatus.DRAFT);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(scenarioRepository.findByIdWithTags(2L)).thenReturn(Optional.of(draft));

        assertThrows(IllegalArgumentException.class,
                () -> scenarioService.forkScenario(2L, null, authentication));

        verifyNoInteractions(thumbnailRepository, audioRepository);
    }

    @Test
    void forkScenario_throwsWhenNotAuthenticated() {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThrows(InsufficientAuthenticationException.class,
                () -> scenarioService.forkScenario(1L, null, authentication));

        verifyNoInteractions(scenarioRepository, thumbnailRepository, audioRepository);
    }

    @Test
    void forkScenario_throwsWhenScenarioNotFound() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(scenarioRepository.findByIdWithTags(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> scenarioService.forkScenario(999L, null, authentication));
    }

    @Test
    void forkScenario_generatesUniqueTitleOnCollision() {
        Scenario original = publishedScenario(1L);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("bob");
        when(userService.getUserByUsername("bob")).thenReturn(bob());
        when(userService.getUserById(42L)).thenReturn(bob());
        when(scenarioRepository.findByIdWithTags(1L)).thenReturn(Optional.of(original));
        // First candidate title already taken (by this same user/language), second one is free
        when(scenarioRepository.existsByTitleAndAuthorUsernameAndLanguageId(anyString(), eq("bob"), eq("fra")))
                .thenReturn(true, false);
        mockSaveAssignsId(100L);
        when(thumbnailRepository.findByScenarioIdOrderByIdxAsc(1L)).thenReturn(List.of());

        Scenario fork = scenarioService.forkScenario(1L, null, authentication);

        assertTrue(fork.getTitle().contains("#2"));
        verify(scenarioRepository, times(2))
                .existsByTitleAndAuthorUsernameAndLanguageId(anyString(), eq("bob"), eq("fra"));
    }

    @Test
    void forkScenario_honorsCustomTitle() {
        Scenario original = publishedScenario(1L);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("bob");
        when(userService.getUserByUsername("bob")).thenReturn(bob());
        when(userService.getUserById(42L)).thenReturn(bob());
        when(scenarioRepository.findByIdWithTags(1L)).thenReturn(Optional.of(original));
        when(scenarioRepository.existsByTitleAndAuthorUsernameAndLanguageId("My remix", "bob", "fra"))
                .thenReturn(false);
        mockSaveAssignsId(100L);
        when(thumbnailRepository.findByScenarioIdOrderByIdxAsc(1L)).thenReturn(List.of());

        Scenario fork = scenarioService.forkScenario(1L, "  My remix  ", authentication);

        assertEquals("My remix", fork.getTitle());
    }

    @Test
    void forkScenario_rejectsCustomTitleThatCollidesForSameUserAndLanguage() {
        Scenario original = publishedScenario(1L);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("bob");
        when(userService.getUserByUsername("bob")).thenReturn(bob());
        when(scenarioRepository.findByIdWithTags(1L)).thenReturn(Optional.of(original));
        when(scenarioRepository.existsByTitleAndAuthorUsernameAndLanguageId("Taken title", "bob", "fra"))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> scenarioService.forkScenario(1L, "Taken title", authentication));

        verify(scenarioRepository, never()).save(any());
    }

    @Test
    void forkScenario_secondCopyByDifferentUserDoesNotCollideWithFirst() {
        // Same source scenario forked by two different users should never block each other,
        // even if they end up with the exact same auto-generated title.
        Scenario original = publishedScenario(1L);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("bob");
        when(userService.getUserByUsername("bob")).thenReturn(bob());
        when(userService.getUserById(42L)).thenReturn(bob());
        when(scenarioRepository.findByIdWithTags(1L)).thenReturn(Optional.of(original));
        // "Copy of Original scenario" is already taken by ANOTHER user, but that must not
        // affect bob: the check is scoped to (title, author, language), not global.
        when(scenarioRepository.existsByTitleAndAuthorUsernameAndLanguageId("Copy of Original scenario", "bob", "fra"))
                .thenReturn(false);
        mockSaveAssignsId(101L);
        when(thumbnailRepository.findByScenarioIdOrderByIdxAsc(1L)).thenReturn(List.of());

        Scenario fork = scenarioService.forkScenario(1L, null, authentication);

        assertEquals("Copy of Original scenario", fork.getTitle());
    }

    @Test
    void forkScenario_withNoThumbnails_stillSucceeds() {
        Scenario original = publishedScenario(1L);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("bob");
        when(userService.getUserByUsername("bob")).thenReturn(bob());
        when(userService.getUserById(42L)).thenReturn(bob());
        when(scenarioRepository.findByIdWithTags(1L)).thenReturn(Optional.of(original));
        when(scenarioRepository.existsByTitleAndAuthorUsernameAndLanguageId(anyString(), anyString(), anyString())).thenReturn(false);
        mockSaveAssignsId(99L);
        when(thumbnailRepository.findByScenarioIdOrderByIdxAsc(1L)).thenReturn(List.of());

        Scenario fork = scenarioService.forkScenario(1L, null, authentication);

        assertEquals(99L, fork.getId());
        verify(thumbnailRepository, never()).save(any());
        verify(audioRepository).findByScenarioIdAndScopeOrderByIdxAscIdAsc(1L, AudioScope.BACKGROUND);
        verify(audioRepository, never()).save(any());
    }

    @Test
    void forkScenario_thumbnailWithoutAudio_createsNoAudio() {
        Scenario original = publishedScenario(1L);
        Thumbnail originalThumb = thumbnail(101L, 1L, 0);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("bob");
        when(userService.getUserByUsername("bob")).thenReturn(bob());
        when(userService.getUserById(42L)).thenReturn(bob());
        when(scenarioRepository.findByIdWithTags(1L)).thenReturn(Optional.of(original));
        when(scenarioRepository.existsByTitleAndAuthorUsernameAndLanguageId(anyString(), anyString(), anyString())).thenReturn(false);
        mockSaveAssignsId(99L);

        when(thumbnailRepository.findByScenarioIdOrderByIdxAsc(1L)).thenReturn(List.of(originalThumb));
        when(thumbnailRepository.save(any(Thumbnail.class))).thenAnswer(invocation -> {
            Thumbnail t = invocation.getArgument(0);
            t.setId(201L);
            return t;
        });
        when(audioRepository.findByThumbnailIdOrderByIdxAsc(101L)).thenReturn(List.of());

        scenarioService.forkScenario(1L, null, authentication);

        verify(audioRepository, never()).save(any());
    }
}
