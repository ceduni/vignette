package org.titiplex.config.components;

import org.springframework.stereotype.Service;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.ThumbnailService;

@Service
public class ThumbnailSecurity {
    private final ThumbnailService thumbnails;
    private final ScenarioService scenarios;

    public ThumbnailSecurity(ThumbnailService thumbnails, ScenarioService scenarios) {
        this.thumbnails = thumbnails;
        this.scenarios = scenarios;
    }

    public boolean isOwner(Long thumbnailId, String username) {
        var thumb = thumbnails.getThumbnailById(thumbnailId);
        return scenarios.hasContentEditAccess(thumb.getScenarioId(), username);
    }
}