package org.titiplex.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.titiplex.api.dto.UpdateThumbnailLayoutRequest;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioHistoryAction;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.model.User;
import org.titiplex.service.storage.MediaContent;

import java.util.List;

/**
 * Scene image operations done on behalf of a user: each one checks that the user may view or
 * edit the scenario the image belongs to, does the work in {@link ThumbnailService}, and, for
 * changes, records it in the scenario history.
 */
@Service
public class ThumbnailManagementService {

    private final ThumbnailService thumbnailService;
    private final ScenarioService scenarioService;
    private final ScenarioAccessService access;
    private final ScenarioHistoryService history;
    private final UserService userService;

    public ThumbnailManagementService(
            ThumbnailService thumbnailService,
            ScenarioService scenarioService,
            ScenarioAccessService access,
            ScenarioHistoryService history,
            UserService userService
    ) {
        this.thumbnailService = thumbnailService;
        this.scenarioService = scenarioService;
        this.access = access;
        this.history = history;
        this.userService = userService;
    }

    // ── Reading ────────────────────────────────────────────────────────

    public List<Thumbnail> list(Long scenarioId, Authentication auth) {
        access.assertCanViewScenario(scenarioService.getRequiredScenario(scenarioId), auth);
        return thumbnailService.listByScenarioId(scenarioId);
    }

    public MediaContent loadContent(Long thumbnailId, Authentication auth) {
        Thumbnail thumbnail = thumbnailService.getThumbnailById(thumbnailId);
        access.assertCanViewScenario(scenarioService.getRequiredScenario(thumbnail.getScenarioId()), auth);
        return thumbnailService.loadContent(thumbnailId);
    }

    // ── Changing ───────────────────────────────────────────────────────

    public Thumbnail upload(Long scenarioId, String title, MultipartFile image, Authentication auth) throws Exception {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Image is required");
        }

        User user = userService.getUserByUsername(auth.getName());
        Scenario scenario = scenarioService.getRequiredScenario(scenarioId);
        access.assertCanEditScenario(scenario, auth);

        Thumbnail saved = thumbnailService.save(title, image, scenario, user);

        String summary = (saved.getTitle() != null && !saved.getTitle().isBlank())
                ? "Added thumbnail \"" + saved.getTitle() + "\""
                : "Added a thumbnail";
        history.record(scenario.getId(), user.getId(), ScenarioHistoryAction.THUMBNAIL_ADDED, summary);
        return saved;
    }

    public Thumbnail updateLayout(Long thumbnailId, UpdateThumbnailLayoutRequest request, Authentication auth) {
        Scenario scenario = editableScenarioOf(thumbnailId, auth);
        Thumbnail saved = thumbnailService.updateLayout(thumbnailId, request);
        recordHistory(scenario.getId(), auth, ScenarioHistoryAction.THUMBNAIL_UPDATED, "Repositioned a thumbnail");
        return saved;
    }

    public Thumbnail updateTitle(Long thumbnailId, String title, Authentication auth) {
        Scenario scenario = editableScenarioOf(thumbnailId, auth);
        Thumbnail saved = thumbnailService.updateTitle(thumbnailId, title);
        recordHistory(scenario.getId(), auth, ScenarioHistoryAction.THUMBNAIL_UPDATED, "Renamed a thumbnail");
        return saved;
    }

    public List<Thumbnail> reorder(Long scenarioId, List<Long> thumbnailIds, Authentication auth) {
        access.assertCanEditScenario(scenarioService.getRequiredScenario(scenarioId), auth);
        List<Thumbnail> rows = thumbnailService.reorder(scenarioId, thumbnailIds);
        recordHistory(scenarioId, auth, ScenarioHistoryAction.THUMBNAIL_UPDATED, "Reordered scenes");
        return rows;
    }

    public void delete(Long thumbnailId, Authentication auth) {
        Long scenarioId = thumbnailService.getThumbnailById(thumbnailId).getScenarioId();
        access.assertCanEditScenarioById(scenarioId, auth);
        thumbnailService.delete(thumbnailId);
        recordHistory(scenarioId, auth, ScenarioHistoryAction.THUMBNAIL_DELETED, "Deleted a thumbnail");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /** The scenario the thumbnail belongs to, once the user is known to be allowed to edit it. */
    private Scenario editableScenarioOf(Long thumbnailId, Authentication auth) {
        Thumbnail thumbnail = thumbnailService.getThumbnailById(thumbnailId);
        Scenario scenario = scenarioService.getRequiredScenario(thumbnail.getScenarioId());
        access.assertCanEditScenario(scenario, auth);
        return scenario;
    }

    private void recordHistory(Long scenarioId, Authentication auth, ScenarioHistoryAction action, String summary) {
        Long actorId = userService.getUserByUsername(auth.getName()).getId();
        history.record(scenarioId, actorId, action, summary);
    }
}
