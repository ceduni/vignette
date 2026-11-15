package org.titiplex.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.titiplex.api.dto.AudioRowDto;
import org.titiplex.api.dto.ScenarioBackgroundAudioDto;
import org.titiplex.persistence.model.ScenarioHistoryAction;
import org.titiplex.service.storage.MediaContent;

import java.util.List;

/**
 * Audio operations done on behalf of a user: each one checks that the user may view or edit
 * the scenario the audio belongs to, does the work in {@link AudioService}, and, for changes,
 * records it in the scenario history.
 */
@Service
public class AudioManagementService {

    private final AudioService audioService;
    private final ThumbnailService thumbnailService;
    private final ScenarioService scenarioService;
    private final ScenarioAccessService access;
    private final ScenarioHistoryService history;
    private final UserService userService;

    public AudioManagementService(
            AudioService audioService,
            ThumbnailService thumbnailService,
            ScenarioService scenarioService,
            ScenarioAccessService access,
            ScenarioHistoryService history,
            UserService userService
    ) {
        this.audioService = audioService;
        this.thumbnailService = thumbnailService;
        this.scenarioService = scenarioService;
        this.access = access;
        this.history = history;
        this.userService = userService;
    }

    // ── Reading ────────────────────────────────────────────────────────

    public List<AudioRowDto> listForThumbnail(Long thumbnailId, Authentication auth) {
        assertCanViewScenarioOf(thumbnailService.getThumbnailById(thumbnailId).getScenarioId(), auth);
        return audioService.listForThumbnail(thumbnailId);
    }

    public List<ScenarioBackgroundAudioDto> listBackground(Long scenarioId, Authentication auth) {
        assertCanViewScenarioOf(scenarioId, auth);
        return audioService.listBackgroundForScenario(scenarioId);
    }

    public MediaContent loadContent(Long audioId, Authentication auth) {
        assertCanViewScenarioOf(audioService.getScenarioIdForAudio(audioId), auth);
        return audioService.loadContent(audioId);
    }

    // ── Background audio ───────────────────────────────────────────────

    public Long uploadBackground(Long scenarioId, String title, String sourceLabel, String sourceUrl,
                                 MultipartFile audio, Authentication auth) throws Exception {
        access.assertCanEditScenarioById(scenarioId, auth);
        return audioService.createBackgroundAudio(scenarioId, title, sourceLabel, sourceUrl, actorId(auth), audio);
    }

    public void selectBackground(Long scenarioId, Long audioId, Authentication auth) {
        access.assertCanEditScenarioById(scenarioId, auth);
        audioService.selectBackgroundAudio(scenarioId, audioId);
    }

    public void updateAmbience(Long audioId, Integer volume, Boolean loop, Authentication auth) {
        access.assertCanEditScenarioById(audioService.getScenarioIdForAudio(audioId), auth);
        audioService.updateAmbience(audioId, volume, loop);
    }

    // ── Scene audio ────────────────────────────────────────────────────

    public Long upload(Long thumbnailId, String title, Integer idx, MultipartFile audio,
                       Double markerX, Double markerY, String markerLabel, Authentication auth) throws Exception {
        Long scenarioId = thumbnailService.getThumbnailById(thumbnailId).getScenarioId();
        access.assertCanEditScenarioById(scenarioId, auth);
        Long actorId = actorId(auth);

        Long id = audioService.createAudio(thumbnailId, title, idx, actorId, audio, markerX, markerY, markerLabel);

        String summary = (title != null && !title.isBlank()) ? "Added audio \"" + title.trim() + "\"" : "Added an audio clip";
        history.record(scenarioId, actorId, ScenarioHistoryAction.AUDIO_ADDED, summary);
        return id;
    }

    public Long replace(Long audioId, String title, MultipartFile audio, Authentication auth) throws Exception {
        Long scenarioId = audioService.getScenarioIdForAudio(audioId);
        access.assertCanEditScenarioById(scenarioId, auth);
        Long id = audioService.replaceAudio(audioId, title, audio);
        recordHistory(scenarioId, auth, ScenarioHistoryAction.AUDIO_UPDATED, "Replaced an audio clip");
        return id;
    }

    public void updateGloss(Long audioId, String transcription, String gloss, String freeTranslation, Authentication auth) {
        Long scenarioId = audioService.getScenarioIdForAudio(audioId);
        access.assertCanEditScenarioById(scenarioId, auth);
        audioService.updateGloss(audioId, transcription, gloss, freeTranslation);
        recordHistory(scenarioId, auth, ScenarioHistoryAction.AUDIO_UPDATED, "Updated an audio gloss");
    }

    public void updateMarker(Long audioId, Double markerX, Double markerY, String markerLabel, Authentication auth) {
        Long scenarioId = audioService.getScenarioIdForAudio(audioId);
        access.assertCanEditScenarioById(scenarioId, auth);
        audioService.updateMarker(audioId, markerX, markerY, markerLabel);
        recordHistory(scenarioId, auth, ScenarioHistoryAction.AUDIO_UPDATED, "Updated an audio marker");
    }

    public void delete(Long audioId, Authentication auth) {
        Long scenarioId = audioService.getScenarioIdForAudio(audioId);
        access.assertCanEditScenarioById(scenarioId, auth);
        audioService.deleteAudio(audioId);
        recordHistory(scenarioId, auth, ScenarioHistoryAction.AUDIO_DELETED, "Deleted an audio clip");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private void assertCanViewScenarioOf(Long scenarioId, Authentication auth) {
        access.assertCanViewScenario(scenarioService.getRequiredScenario(scenarioId), auth);
    }

    private Long actorId(Authentication auth) {
        return userService.getUserByUsername(auth.getName()).getId();
    }

    private void recordHistory(Long scenarioId, Authentication auth, ScenarioHistoryAction action, String summary) {
        history.record(scenarioId, actorId(auth), action, summary);
    }
}
