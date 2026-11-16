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
import org.titiplex.api.dto.AudioRowDto;
import org.titiplex.api.dto.CreateAudioResponse;
import org.titiplex.api.dto.LanguagePreviewAudioDto;
import org.titiplex.api.dto.SelectScenarioBackgroundAudioRequest;
import org.titiplex.api.dto.UpdateAmbienceRequest;
import org.titiplex.api.dto.UpdateAudioGlossRequest;
import org.titiplex.api.dto.UpdateMarkerRequest;
import org.titiplex.service.AudioManagementService;
import org.titiplex.service.AudioService;
import org.titiplex.service.storage.MediaContent;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The controller only turns requests into service calls and results into responses; permissions are tested in the service. */
@ExtendWith(MockitoExtension.class)
class AudioApiControllerTest {

    @Mock
    private AudioManagementService audioManagement;

    @Mock
    private AudioService audioService;

    @InjectMocks
    private AudioApiController controller;

    private final Authentication auth = new UsernamePasswordAuthenticationToken("alice", "N/A", List.of());
    private final MockMultipartFile audio = new MockMultipartFile("audio", "clip.webm", "audio/webm", new byte[]{1, 2, 3});

    @Test
    void list_returnsTheServiceRows() {
        when(audioManagement.listForThumbnail(7L, auth)).thenReturn(List.of(
                new AudioRowDto(1L, "Audio 1", 1, "audio/webm", 10.0, 20.0, "A", null, null, null),
                new AudioRowDto(2L, "Audio 2", 2, "audio/webm", null, null, null, null, null, null)
        ));

        List<AudioRowDto> result = controller.list(7L, auth);

        assertEquals(2, result.size());
        assertEquals("Audio 1", result.get(0).title());
        assertEquals("audio/webm", result.get(0).mime());
    }

    @Test
    void content_buildsResponseEntityWithHeaders() {
        Resource resource = new ByteArrayResource(new byte[]{9, 8, 7});
        when(audioManagement.loadContent(4L, auth)).thenReturn(new MediaContent(resource, "audio/webm", 3L, "\"audio-etag\""));

        ResponseEntity<Resource> response = controller.content(4L, auth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("audio/webm", Objects.requireNonNull(response.getHeaders().getContentType()).toString());
        assertEquals(3L, response.getHeaders().getContentLength());
        assertEquals("\"audio-etag\"", response.getHeaders().getETag());
        assertEquals("private, no-store", response.getHeaders().getFirst("Cache-Control"));
        assertEquals(resource, response.getBody());
    }

    @Test
    void upload_returnsTheNewAudioId() throws Exception {
        when(audioManagement.upload(9L, "Greeting", 2, audio, 10.0, 20.0, "speaker", auth)).thenReturn(42L);

        CreateAudioResponse response = controller.upload(9L, "Greeting", 2, 10.0, 20.0, "speaker", audio, auth);

        assertEquals(42L, response.id());
    }

    @Test
    void uploadBackground_returnsTheNewAudioId() throws Exception {
        when(audioManagement.uploadBackground(12L, "Forest", "Notes", "", audio, auth)).thenReturn(7L);

        assertEquals(7L, controller.uploadBackground(12L, "Forest", "Notes", "", audio, auth).id());
    }

    @Test
    void selectBackground_delegates() {
        controller.selectBackground(12L, new SelectScenarioBackgroundAudioRequest(99L), auth);

        verify(audioManagement).selectBackground(12L, 99L, auth);
    }

    @Test
    void replace_returnsTheSameAudioId() throws Exception {
        when(audioManagement.replace(5L, "New take", audio, auth)).thenReturn(5L);

        assertEquals(5L, controller.replace(5L, "New take", audio, auth).id());
    }

    @Test
    void updateAmbienceGlossAndMarker_delegate() {
        controller.updateAmbience(5L, new UpdateAmbienceRequest(50, true), auth);
        controller.updateGloss(5L, new UpdateAudioGlossRequest("t", "g", "f"), auth);
        controller.updateMarker(5L, new UpdateMarkerRequest(11.0, 22.0, "target"), auth);

        verify(audioManagement).updateAmbience(5L, 50, true, auth);
        verify(audioManagement).updateGloss(5L, "t", "g", "f", auth);
        verify(audioManagement).updateMarker(5L, 11.0, 22.0, "target", auth);
    }

    @Test
    void delete_delegates() {
        controller.delete(6L, auth);

        verify(audioManagement).delete(6L, auth);
    }

    @Test
    void listByLanguage_delegatesToService() {
        when(audioService.listForLanguage("chuj")).thenReturn(List.of(
                new AudioRowDto(1L, "Clip", 1, "audio/webm", null, null, null, null, null, null)
        ));

        List<AudioRowDto> result = controller.listByLanguage("chuj");

        assertEquals(1, result.size());
        assertEquals("Clip", result.get(0).title());
    }

    @Test
    void previewByLanguage_delegatesToService() {
        when(audioService.getLanguagePreviewAudio("bamb1269")).thenReturn(new LanguagePreviewAudioDto(
                5L, "Preview", "audio/webm", "/api/audios/5/content", 9L, "First scenario", 12L, "bamb1269", 1, null));

        LanguagePreviewAudioDto result = controller.previewByLanguage("bamb1269");

        assertEquals(5L, result.id());
        assertEquals("/api/audios/5/content", result.contentUrl());
    }
}
