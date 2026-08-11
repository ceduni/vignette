package org.titiplex.config.components;

import org.springframework.stereotype.Service;
import org.titiplex.service.AudioService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.ThumbnailService;

@Service
public class AudioSecurity {
    private final AudioService audios;
    private final ThumbnailService thumbnails;
    private final ScenarioService scenarios;

    public AudioSecurity(AudioService audios, ThumbnailService thumbnails, ScenarioService scenarios) {
        this.audios = audios;
        this.thumbnails = thumbnails;
        this.scenarios = scenarios;
    }

    public boolean isOwner(Long audioId, String username) {
        var audio = audios.getAudioOrThrow(audioId);
        var thumb = thumbnails.getThumbnailById(audio.getThumbnailId());
        return scenarios.hasContentEditAccess(thumb.getScenarioId(), username);
    }

}