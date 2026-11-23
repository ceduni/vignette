package org.titiplex.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioHistoryAction;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.model.User;

import java.util.NoSuchElementException;

/**
 * Vignette Maker scenes saved into a scenario: the exported image becomes a scene image
 * (a thumbnail) and the editable drawing is kept with it, so the scene can be reopened later.
 * Each operation checks the user's access to the scenario and, for changes, records history.
 */
@Service
public class VignetteService {

    /** Version pour tests */
    static final int SCENE_JSON_MAX_LENGTH = 1_000_000;

    private final ThumbnailService thumbnailService;
    private final ScenarioService scenarioService;
    private final ScenarioAccessService access;
    private final ScenarioHistoryService history;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public VignetteService(ThumbnailService thumbnailService,
                           ScenarioService scenarioService,
                           ScenarioAccessService access,
                           ScenarioHistoryService history,
                           UserService userService,
                           ObjectMapper objectMapper) {
        this.thumbnailService = thumbnailService;
        this.scenarioService = scenarioService;
        this.access = access;
        this.history = history;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    public Thumbnail create(Long scenarioId, String title, MultipartFile image, String sceneJson,
                            Authentication auth) throws Exception {
        requireImage(image);
        String drawing = validatedSceneJson(sceneJson);

        User user = userService.getUserByUsername(auth.getName());
        Scenario scenario = scenarioService.getRequiredScenario(scenarioId);
        access.assertCanEditScenario(scenario, auth);

        Thumbnail saved = thumbnailService.save(title, image, scenario, user, drawing);
        history.record(scenario.getId(), user.getId(), ScenarioHistoryAction.THUMBNAIL_ADDED,
                "Added vignette \"" + saved.getTitle() + "\"");
        return saved;
    }

    /** The scene image, once the user is known to be allowed to view it; 404 when it is not a vignette. */
    public Thumbnail get(Long thumbnailId, Authentication auth) {
        Thumbnail thumbnail = thumbnailService.getThumbnailById(thumbnailId);
        access.assertCanViewScenario(scenarioService.getRequiredScenario(thumbnail.getScenarioId()), auth);
        if (thumbnail.getVignetteSceneJson() == null) {
            throw new NoSuchElementException("This scene image was not made with the Vignette Maker");
        }
        return thumbnail;
    }

    public Thumbnail update(Long thumbnailId, MultipartFile image, String sceneJson,
                            Authentication auth) throws Exception {
        requireImage(image);
        String drawing = validatedSceneJson(sceneJson);

        Thumbnail thumbnail = thumbnailService.getThumbnailById(thumbnailId);
        Scenario scenario = scenarioService.getRequiredScenario(thumbnail.getScenarioId());
        access.assertCanEditScenario(scenario, auth);
        if (thumbnail.getVignetteSceneJson() == null) {
            throw new IllegalArgumentException("This scene image was not made with the Vignette Maker");
        }

        Thumbnail saved = thumbnailService.replaceVignette(thumbnailId, image, drawing);
        Long actorId = userService.getUserByUsername(auth.getName()).getId();
        history.record(scenario.getId(), actorId, ScenarioHistoryAction.THUMBNAIL_UPDATED,
                "Edited vignette \"" + saved.getTitle() + "\"");
        return saved;
    }

    private static void requireImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Image is required");
        }
    }

    /** The drawing must be a JSON object; it is stored as sent. */
    String validatedSceneJson(String sceneJson) {
        if (sceneJson == null || sceneJson.isBlank()) {
            throw new IllegalArgumentException("Scene data is required");
        }
        if (sceneJson.length() > SCENE_JSON_MAX_LENGTH) {
            throw new IllegalArgumentException("Scene data is too large");
        }
        try {
            JsonNode node = objectMapper.readTree(sceneJson);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("Scene data must be a JSON object");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Scene data is not valid JSON");
        }
        return sceneJson;
    }
}
