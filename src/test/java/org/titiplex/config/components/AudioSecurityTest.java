package org.titiplex.config.components;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.titiplex.persistence.model.Audio;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.service.AudioService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.ThumbnailService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioSecurityTest {

    @Mock
    private AudioService audioService;

    @Mock
    private ThumbnailService thumbnailService;

    @Mock
    private ScenarioService scenarioService;

    @InjectMocks
    private AudioSecurity audioSecurity;

    private Audio audioOn(long thumbnailId) {
        Audio a = new Audio();
        a.setId(3L);
        a.setThumbnailId(thumbnailId);
        a.setAuthorId(1L);
        return a;
    }

    private Thumbnail thumbnailOf(long thumbnailId, long scenarioId) {
        Thumbnail t = new Thumbnail();
        t.setId(thumbnailId);
        t.setScenarioId(scenarioId);
        t.setAuthorId(1L);
        return t;
    }

    @Test
    void isOwner_trueForCollaboratorWhoDidNotUploadTheAudio() {
        when(audioService.getAudioOrThrow(3L)).thenReturn(audioOn(7L));
        when(thumbnailService.getThumbnailById(7L)).thenReturn(thumbnailOf(7L, 9L));
        when(scenarioService.hasContentEditAccess(9L, "bob")).thenReturn(true);

        assertTrue(audioSecurity.isOwner(3L, "bob"));
    }

    @Test
    void isOwner_falseWhenNoScenarioEditAccess() {
        when(audioService.getAudioOrThrow(3L)).thenReturn(audioOn(7L));
        when(thumbnailService.getThumbnailById(7L)).thenReturn(thumbnailOf(7L, 9L));
        when(scenarioService.hasContentEditAccess(9L, "stranger")).thenReturn(false);

        assertFalse(audioSecurity.isOwner(3L, "stranger"));
    }
}
