package org.titiplex.config.components;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.ThumbnailService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThumbnailSecurityTest {

    @Mock
    private ThumbnailService thumbnailService;

    @Mock
    private ScenarioService scenarioService;

    @InjectMocks
    private ThumbnailSecurity thumbnailSecurity;

    private Thumbnail thumbnailOf(long scenarioId) {
        Thumbnail t = new Thumbnail();
        t.setId(7L);
        t.setScenarioId(scenarioId);
        t.setAuthorId(1L);
        return t;
    }

    @Test
    void isOwner_trueForCollaboratorWhoDidNotUploadTheThumbnail() {
        when(thumbnailService.getThumbnailById(7L)).thenReturn(thumbnailOf(9L));
        when(scenarioService.hasContentEditAccess(9L, "bob")).thenReturn(true);

        assertTrue(thumbnailSecurity.isOwner(7L, "bob"));
    }

    @Test
    void isOwner_falseWhenNoScenarioEditAccess() {
        when(thumbnailService.getThumbnailById(7L)).thenReturn(thumbnailOf(9L));
        when(scenarioService.hasContentEditAccess(9L, "stranger")).thenReturn(false);

        assertFalse(thumbnailSecurity.isOwner(7L, "stranger"));
    }
}
