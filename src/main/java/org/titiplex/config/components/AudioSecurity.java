package org.titiplex.config.components;

import org.springframework.stereotype.Service;
import org.titiplex.service.AudioService;
import org.titiplex.service.ScenarioService;

@Service
public class AudioSecurity {
    private final AudioService audios;
    private final ScenarioService scenarios;

    public AudioSecurity(AudioService audios, ScenarioService scenarios) {
        this.audios = audios;
        this.scenarios = scenarios;
    }

    public boolean isOwner(Long audioId, String username) {
        var audio = audios.getAudioOrThrow(audioId);
        return scenarios.hasContentEditAccess(audio.getScenarioId(), username);
    }

}
