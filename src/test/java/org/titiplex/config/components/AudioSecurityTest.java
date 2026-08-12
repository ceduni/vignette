package org.titiplex.config.components;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.titiplex.persistence.model.Audio;
import org.titiplex.service.AudioService;
import org.titiplex.service.ScenarioService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioSecurityTest {

    @Mock
    private AudioService audioService;

    @Mock
    private ScenarioService scenarioService;

    @InjectMocks
    private AudioSecurity audioSecurity;

    private Audio audioOn(long scenarioId) {
        Audio a = new Audio();
        a.setId(3L);
        a.setScenarioId(scenarioId);
        a.setAuthorId(1L);
        return a;
    }

    @Test
    void isOwner_trueForCollaboratorWhoDidNotUploadTheAudio() {
        when(audioService.getAudioOrThrow(3L)).thenReturn(audioOn(9L));
        when(scenarioService.hasContentEditAccess(9L, "bob")).thenReturn(true);

        assertTrue(audioSecurity.isOwner(3L, "bob"));
    }

    @Test
    void isOwner_falseWhenNoScenarioEditAccess() {
        when(audioService.getAudioOrThrow(3L)).thenReturn(audioOn(9L));
        when(scenarioService.hasContentEditAccess(9L, "stranger")).thenReturn(false);

        assertFalse(audioSecurity.isOwner(3L, "stranger"));
    }

    @Test
    void isOwner_supportsBackgroundAudioWithoutThumbnail() {
        Audio audio = audioOn(9L);
        audio.setThumbnailId(null);
        when(audioService.getAudioOrThrow(3L)).thenReturn(audio);
        when(scenarioService.hasContentEditAccess(9L, "alice")).thenReturn(true);

        assertTrue(audioSecurity.isOwner(3L, "alice"));
    }
}
