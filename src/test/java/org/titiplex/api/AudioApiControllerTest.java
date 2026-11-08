package org.titiplex.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.titiplex.api.dto.AudioRowDto;
import org.titiplex.api.dto.CreateAudioResponse;
import org.titiplex.api.dto.LanguagePreviewAudioDto;
import org.titiplex.api.dto.SelectScenarioBackgroundAudioRequest;
import org.titiplex.api.dto.UpdateMarkerRequest;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.model.User;
import org.titiplex.service.AudioService;
import org.titiplex.service.ScenarioHistoryService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.ThumbnailService;
import org.titiplex.service.UserService;
import org.titiplex.service.storage.MediaContent;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"SameParameterValue", "SequencedCollectionMethodCanBeUsed"})
@ExtendWith(MockitoExtension.class)
class AudioApiControllerTest {

    @Mock
    private AudioService audioService;

    @Mock
    private UserService userService;

    @Mock
    private ThumbnailService thumbnailService;

    @Mock
    private ScenarioService scenarioService;

    @Mock
    private ScenarioHistoryService scenarioHistoryService;

    @InjectMocks
    private AudioApiController controller;

    @Test
    void selectBackground_updatesTheScenarioPlayerSelection() {
        Authentication auth = auth("alice", "ROLE_USER");

        controller.selectBackground(12L, new SelectScenarioBackgroundAudioRequest(99L), auth);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(scenarioService, audioService);
        order.verify(scenarioService).assertCanEditScenarioById(12L, auth);
        order.verify(audioService).selectBackgroundAudio(12L, 99L);
    }

    // ── Access is enforced in the controller via ScenarioService (single source of truth) ──

    private void denyEditOnScenario(long scenarioId, Authentication auth) {
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("no"))
                .when(scenarioService).assertCanEditScenarioById(scenarioId, auth);
    }

    @Test
    void selectBackground_refusedWithoutEditAccess() {
        Authentication auth = auth("stranger", "ROLE_USER");
        denyEditOnScenario(12L, auth);

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.selectBackground(12L, new SelectScenarioBackgroundAudioRequest(99L), auth));
        org.mockito.Mockito.verify(audioService, org.mockito.Mockito.never()).selectBackgroundAudio(any(), any());
    }

    @Test
    void uploadBackground_refusedWithoutEditAccess() throws Exception {
        Authentication auth = auth("stranger", "ROLE_USER");
        denyEditOnScenario(12L, auth);
        MockMultipartFile file = new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[]{1});

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.uploadBackground(12L, "", "", "", file, auth));
        org.mockito.Mockito.verifyNoInteractions(audioService);
    }

    @Test
    void upload_refusedWithoutEditAccessOnTheThumbnailScenario() throws Exception {
        Authentication auth = auth("stranger", "ROLE_USER");
        Thumbnail thumbnail = new Thumbnail();
        thumbnail.setId(7L);
        thumbnail.setScenarioId(12L);
        when(thumbnailService.getThumbnailById(7L)).thenReturn(thumbnail);
        denyEditOnScenario(12L, auth);
        MockMultipartFile file = new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[]{1});

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.upload(7L, "t", null, null, null, "", file, auth));
        org.mockito.Mockito.verify(audioService, org.mockito.Mockito.never())
                .createAudio(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void audioActions_refusedWithoutEditAccess() throws Exception {
        Authentication auth = auth("stranger", "ROLE_USER");
        when(audioService.getScenarioIdForAudio(3L)).thenReturn(12L);
        denyEditOnScenario(12L, auth);
        MockMultipartFile file = new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[]{1});
        var denied = org.springframework.security.access.AccessDeniedException.class;

        org.junit.jupiter.api.Assertions.assertThrows(denied, () -> controller.updateAmbience(
                3L, new org.titiplex.api.dto.UpdateAmbienceRequest(50, true), auth));
        org.junit.jupiter.api.Assertions.assertThrows(denied, () -> controller.updateGloss(
                3L, new org.titiplex.api.dto.UpdateAudioGlossRequest("t", "g", "f"), auth));
        org.junit.jupiter.api.Assertions.assertThrows(denied, () -> controller.replace(3L, "", file, auth));
        org.junit.jupiter.api.Assertions.assertThrows(denied, () -> controller.updateMarker(
                3L, new UpdateMarkerRequest(10.0, 10.0, "x"), auth));
        org.junit.jupiter.api.Assertions.assertThrows(denied, () -> controller.delete(3L, auth));

        org.mockito.Mockito.verify(audioService, org.mockito.Mockito.never()).updateAmbience(any(), any(), any());
        org.mockito.Mockito.verify(audioService, org.mockito.Mockito.never()).updateGloss(any(), any(), any(), any());
        org.mockito.Mockito.verify(audioService, org.mockito.Mockito.never()).replaceAudio(any(), any(), any());
        org.mockito.Mockito.verify(audioService, org.mockito.Mockito.never()).updateMarker(any(), any(), any(), any());
        org.mockito.Mockito.verify(audioService, org.mockito.Mockito.never()).deleteAudio(any());
    }

    @Test
    void list_checksScenarioVisibilityAndReturnsServiceDtos() {
        Authentication auth = auth("alice", "ROLE_USER");

        Thumbnail thumbnail = new Thumbnail();
        thumbnail.setId(7L);
        thumbnail.setScenarioId(11L);

        Scenario scenario = new Scenario();
        scenario.setId(11L);

        when(thumbnailService.getThumbnailById(7L)).thenReturn(thumbnail);
        when(scenarioService.getRequiredScenario(11L)).thenReturn(scenario);
        when(audioService.listForThumbnail(7L)).thenReturn(List.of(
                new AudioRowDto(1L, "Audio 1", 1, "audio/webm", 10.0, 20.0, "A", null, null, null),
                new AudioRowDto(2L, "Audio 2", 2, "audio/webm", null, null, null, null, null, null)
        ));

        List<AudioRowDto> result = controller.list(7L, auth);

        verify(scenarioService).assertCanViewScenario(scenario, auth);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("Audio 1", result.get(0).title());
        assertEquals("audio/webm", result.get(0).mime());
    }

    @Test
    void content_buildsResponseEntityWithHeaders() {
        Resource resource = new ByteArrayResource(new byte[]{9, 8, 7});
        MediaContent media = new MediaContent(resource, "audio/webm", 3L, "\"audio-etag\"");

        when(audioService.loadContent(4L)).thenReturn(media);

        ResponseEntity<Resource> response = controller.content(4L, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("audio/webm", Objects.requireNonNull(response.getHeaders().getContentType()).toString());
        assertEquals(3L, response.getHeaders().getContentLength());
        assertEquals("\"audio-etag\"", response.getHeaders().getETag());
        assertEquals("private, no-store", response.getHeaders().getFirst("Cache-Control"));
        assertEquals(resource, response.getBody());
    }

    @Test
    void upload_usesAuthenticatedUserAndDelegatesToService() throws Exception {
        Authentication auth = auth("alice", "ROLE_USER");

        User user = new User();
        user.setId(12L);
        user.setUsername("alice");

        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "clip.webm",
                "audio/webm",
                new byte[]{1, 2, 3}
        );

        when(userService.getUserByUsername("alice")).thenReturn(user);
        Thumbnail ownerThumbnail = new Thumbnail();
        ownerThumbnail.setId(9L);
        ownerThumbnail.setScenarioId(11L);
        when(thumbnailService.getThumbnailById(9L)).thenReturn(ownerThumbnail);
        when(audioService.createAudio(9L, "Greeting", 2, 12L, audio, 10.0, 20.0, "speaker"))
                .thenReturn(42L);

        CreateAudioResponse response = controller.upload(
                9L,
                "Greeting",
                2,
                10.0,
                20.0,
                "speaker",
                audio,
                auth
        );

        assertEquals(42L, response.id());

        verify(audioService).createAudio(9L, "Greeting", 2, 12L, audio, 10.0, 20.0, "speaker");
    }

    @Test
    void replace_keepsTheAudioRecordAndRecordsHistory() throws Exception {
        Authentication auth = auth("alice", "ROLE_USER");
        User user = new User();
        user.setId(12L);
        user.setUsername("alice");
        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "replacement.webm",
                "audio/webm",
                new byte[]{4, 5, 6}
        );

        when(userService.getUserByUsername("alice")).thenReturn(user);
        when(audioService.getScenarioIdForAudio(5L)).thenReturn(9L);
        when(audioService.replaceAudio(5L, "New take", audio)).thenReturn(5L);

        CreateAudioResponse response = controller.replace(5L, "New take", audio, auth);

        assertEquals(5L, response.id());
        verify(audioService).replaceAudio(5L, "New take", audio);
        verify(scenarioHistoryService).record(
                9L,
                12L,
                org.titiplex.persistence.model.ScenarioHistoryAction.AUDIO_UPDATED,
                "Replaced an audio clip"
        );
    }

    @Test
    void updateMarker_delegatesToService() {
        Authentication auth = auth("alice", "ROLE_USER");
        User user = new User();
        user.setId(12L);
        user.setUsername("alice");
        when(userService.getUserByUsername("alice")).thenReturn(user);

        controller.updateMarker(5L, new UpdateMarkerRequest(11.0, 22.0, "target"), auth);

        verify(audioService).updateMarker(5L, 11.0, 22.0, "target");
    }

    @Test
    void delete_delegatesToService() {
        Authentication auth = auth("alice", "ROLE_USER");
        User user = new User();
        user.setId(12L);
        user.setUsername("alice");
        when(userService.getUserByUsername("alice")).thenReturn(user);

        controller.delete(6L, auth);
        verify(audioService).deleteAudio(6L);
    }

    @Test
    void listByLanguage_delegatesToService() {
        when(audioService.listForLanguage("chuj")).thenReturn(List.of(
                new AudioRowDto(1L, "Clip", 1, "audio/webm", null, null, null, null, null, null)
        ));

        List<AudioRowDto> result = controller.listByLanguage("chuj");

        assertEquals(1, result.size());
        assertEquals("Clip", result.get(0).title());
        verify(audioService).listForLanguage("chuj");
    }

    @Test
    void previewByLanguage_delegatesToService() {
        when(audioService.getLanguagePreviewAudio("bamb1269")).thenReturn(
                new LanguagePreviewAudioDto(
                        5L,
                        "Preview",
                        "audio/webm",
                        "/api/audios/5/content",
                        9L,
                        "First scenario",
                        12L,
                        "bamb1269",
                        1,
                        null
                )
        );

        LanguagePreviewAudioDto result = controller.previewByLanguage("bamb1269");

        assertEquals(5L, result.id());
        assertEquals("/api/audios/5/content", result.contentUrl());
        verify(audioService).getLanguagePreviewAudio("bamb1269");
    }

    private Authentication auth(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "N/A",
                Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
    }
}
