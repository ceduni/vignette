package org.titiplex.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.titiplex.api.dto.AudioRowDto;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioHistoryAction;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.model.User;
import org.titiplex.service.storage.MediaContent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioManagementServiceTest {

    @Mock
    private AudioService audioService;
    @Mock
    private ThumbnailService thumbnailService;
    @Mock
    private ScenarioService scenarioService;
    @Mock
    private ScenarioAccessService access;
    @Mock
    private ScenarioHistoryService history;
    @Mock
    private UserService userService;

    private AudioManagementService service;

    private final Authentication auth = new UsernamePasswordAuthenticationToken("alice", "N/A", List.of());
    private final MockMultipartFile file = new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[]{1});

    @BeforeEach
    void setUp() {
        service = new AudioManagementService(audioService, thumbnailService, scenarioService, access, history, userService);
    }

    private void aliceIs(long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        when(userService.getUserByUsername("alice")).thenReturn(user);
    }

    private void thumbnail7BelongsToScenario12() {
        Thumbnail thumbnail = new Thumbnail();
        thumbnail.setId(7L);
        thumbnail.setScenarioId(12L);
        when(thumbnailService.getThumbnailById(7L)).thenReturn(thumbnail);
    }

    private void denyEditOnScenario12() {
        doThrow(new AccessDeniedException("no")).when(access).assertCanEditScenarioById(12L, auth);
    }

    // ── Reading ────────────────────────────────────────────────────────

    @Test
    void listForThumbnail_checksViewAccessOnTheThumbnailScenario() {
        Scenario scenario = new Scenario();
        scenario.setId(12L);
        List<AudioRowDto> rows = List.of(new AudioRowDto(1L, "A", 1, "audio/webm", null, null, null, null, null, null));
        thumbnail7BelongsToScenario12();
        when(scenarioService.getRequiredScenario(12L)).thenReturn(scenario);
        when(audioService.listForThumbnail(7L)).thenReturn(rows);

        assertSame(rows, service.listForThumbnail(7L, auth));

        verify(access).assertCanViewScenario(scenario, auth);
    }

    @Test
    void listBackground_refusedWhenTheScenarioCannotBeViewed() {
        Scenario scenario = new Scenario();
        when(scenarioService.getRequiredScenario(12L)).thenReturn(scenario);
        doThrow(new java.util.NoSuchElementException("Scenario not found")).when(access).assertCanViewScenario(scenario, auth);

        assertThrows(java.util.NoSuchElementException.class, () -> service.listBackground(12L, auth));
        verify(audioService, never()).listBackgroundForScenario(any());
    }

    @Test
    void loadContent_checksViewAccessOnTheAudioScenario() {
        Scenario scenario = new Scenario();
        MediaContent media = new MediaContent(null, "audio/webm", 3L, "\"e\"");
        when(audioService.getScenarioIdForAudio(4L)).thenReturn(12L);
        when(scenarioService.getRequiredScenario(12L)).thenReturn(scenario);
        when(audioService.loadContent(4L)).thenReturn(media);

        assertSame(media, service.loadContent(4L, auth));

        verify(access).assertCanViewScenario(scenario, auth);
    }

    // ── Background audio ───────────────────────────────────────────────

    @Test
    void selectBackground_checksEditAccessBeforeChangingTheSelection() {
        service.selectBackground(12L, 99L, auth);

        var order = inOrder(access, audioService);
        order.verify(access).assertCanEditScenarioById(12L, auth);
        order.verify(audioService).selectBackgroundAudio(12L, 99L);
    }

    @Test
    void selectBackground_refusedWithoutEditAccess() {
        denyEditOnScenario12();

        assertThrows(AccessDeniedException.class, () -> service.selectBackground(12L, 99L, auth));
        verify(audioService, never()).selectBackgroundAudio(any(), any());
    }

    @Test
    void uploadBackground_usesTheAuthenticatedUserAsAuthor() throws Exception {
        aliceIs(12L);
        when(audioService.createBackgroundAudio(5L, "Forest", "Notes", "", 12L, file)).thenReturn(42L);

        assertEquals(42L, service.uploadBackground(5L, "Forest", "Notes", "", file, auth));

        verify(access).assertCanEditScenarioById(5L, auth);
    }

    @Test
    void uploadBackground_refusedWithoutEditAccess() {
        doThrow(new AccessDeniedException("no")).when(access).assertCanEditScenarioById(12L, auth);

        assertThrows(AccessDeniedException.class, () -> service.uploadBackground(12L, "", "", "", file, auth));
        verifyNoInteractions(audioService);
    }

    @Test
    void updateAmbience_checksEditAccessOnTheAudioScenario() {
        when(audioService.getScenarioIdForAudio(3L)).thenReturn(12L);

        service.updateAmbience(3L, 50, true, auth);

        verify(access).assertCanEditScenarioById(12L, auth);
        verify(audioService).updateAmbience(3L, 50, true);
    }

    // ── Scene audio ────────────────────────────────────────────────────

    @Test
    void upload_checksEditAccessCreatesTheAudioAndRecordsHistory() throws Exception {
        thumbnail7BelongsToScenario12();
        aliceIs(5L);
        when(audioService.createAudio(7L, "Greeting", 2, 5L, file, 10.0, 20.0, "speaker")).thenReturn(42L);

        assertEquals(42L, service.upload(7L, "Greeting", 2, file, 10.0, 20.0, "speaker", auth));

        verify(access).assertCanEditScenarioById(12L, auth);
        verify(history).record(12L, 5L, ScenarioHistoryAction.AUDIO_ADDED, "Added audio \"Greeting\"");
    }

    @Test
    void upload_withoutTitleRecordsAGenericSummary() throws Exception {
        thumbnail7BelongsToScenario12();
        aliceIs(5L);
        when(audioService.createAudio(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(42L);

        service.upload(7L, "  ", null, file, null, null, "", auth);

        verify(history).record(12L, 5L, ScenarioHistoryAction.AUDIO_ADDED, "Added an audio clip");
    }

    @Test
    void upload_refusedWithoutEditAccess() throws Exception {
        thumbnail7BelongsToScenario12();
        denyEditOnScenario12();

        assertThrows(AccessDeniedException.class,
                () -> service.upload(7L, "t", null, file, null, null, "", auth));
        verify(audioService, never()).createAudio(any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(history);
    }

    @Test
    void replace_recordsHistory() throws Exception {
        when(audioService.getScenarioIdForAudio(5L)).thenReturn(9L);
        aliceIs(12L);
        when(audioService.replaceAudio(5L, "New take", file)).thenReturn(5L);

        assertEquals(5L, service.replace(5L, "New take", file, auth));

        verify(access).assertCanEditScenarioById(9L, auth);
        verify(history).record(9L, 12L, ScenarioHistoryAction.AUDIO_UPDATED, "Replaced an audio clip");
    }

    @Test
    void updateGloss_recordsHistory() {
        when(audioService.getScenarioIdForAudio(5L)).thenReturn(9L);
        aliceIs(12L);

        service.updateGloss(5L, "t", "g", "f", auth);

        verify(access).assertCanEditScenarioById(9L, auth);
        verify(audioService).updateGloss(5L, "t", "g", "f");
        verify(history).record(9L, 12L, ScenarioHistoryAction.AUDIO_UPDATED, "Updated an audio gloss");
    }

    @Test
    void updateMarker_recordsHistory() {
        when(audioService.getScenarioIdForAudio(5L)).thenReturn(9L);
        aliceIs(12L);

        service.updateMarker(5L, 11.0, 22.0, "target", auth);

        verify(access).assertCanEditScenarioById(9L, auth);
        verify(audioService).updateMarker(5L, 11.0, 22.0, "target");
        verify(history).record(9L, 12L, ScenarioHistoryAction.AUDIO_UPDATED, "Updated an audio marker");
    }

    @Test
    void delete_recordsHistory() {
        when(audioService.getScenarioIdForAudio(6L)).thenReturn(9L);
        aliceIs(12L);

        service.delete(6L, auth);

        verify(access).assertCanEditScenarioById(9L, auth);
        verify(audioService).deleteAudio(6L);
        verify(history).record(9L, 12L, ScenarioHistoryAction.AUDIO_DELETED, "Deleted an audio clip");
    }

    @Test
    void sceneAudioActions_refusedWithoutEditAccess() throws Exception {
        when(audioService.getScenarioIdForAudio(3L)).thenReturn(12L);
        denyEditOnScenario12();

        assertThrows(AccessDeniedException.class, () -> service.updateAmbience(3L, 50, true, auth));
        assertThrows(AccessDeniedException.class, () -> service.updateGloss(3L, "t", "g", "f", auth));
        assertThrows(AccessDeniedException.class, () -> service.replace(3L, "", file, auth));
        assertThrows(AccessDeniedException.class, () -> service.updateMarker(3L, 10.0, 10.0, "x", auth));
        assertThrows(AccessDeniedException.class, () -> service.delete(3L, auth));

        verify(audioService, never()).updateAmbience(any(), any(), any());
        verify(audioService, never()).updateGloss(any(), any(), any(), any());
        verify(audioService, never()).replaceAudio(any(), any(), any());
        verify(audioService, never()).updateMarker(any(), any(), any(), any());
        verify(audioService, never()).deleteAudio(any());
        verifyNoInteractions(history);
    }
}
