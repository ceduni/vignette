package org.titiplex.config.components;

import org.springframework.stereotype.Component;
import org.titiplex.service.AudioService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.ThumbnailService;

@Component
public class ScenarioSecurity {
    private final ScenarioService scenarioService;
    private final ThumbnailService thumbnailService;

    public ScenarioSecurity(ScenarioService scenarioService, ThumbnailService thumbnailService) {
        this.scenarioService = scenarioService;
        this.thumbnailService = thumbnailService;
    }

    public boolean isOwner(Long scenarioId, String username) {
        return scenarioService.hasEditAccess(scenarioId, username);
    }

    public boolean isOwnerByThumbnailId(Long thumbId, String username) {
        var t = thumbnailService.getThumbnailById(thumbId);
        return scenarioService.existsByIdAndAuthorUsername(t.getScenarioId(), username);
    }

    public boolean isOwnerByAudioId(Long audioId, String username, AudioService audioService) {
        var a = audioService.getAudioOrThrow(audioId);
        return isOwnerByThumbnailId(a.getThumbnailId(), username);
    }
}