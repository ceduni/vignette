package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.titiplex.persistence.model.Thumbnail;

@Schema(description = "A scene image made with the Vignette Maker, with the drawing needed to edit it again")
public record VignetteDto(
        @Schema(description = "ID of the scene image (thumbnail)", example = "42")
        Long thumbnailId,
        @Schema(description = "ID of the scenario the scene belongs to", example = "7")
        Long scenarioId,
        @Schema(description = "Title of the scene", example = "At the market")
        String title,
        @Schema(description = "Position of the scene in the scenario", example = "3")
        Integer idx,
        @Schema(description = "Vignette Maker drawing, as a JSON string", example = "{\"people\":[],\"nextId\":1}")
        String sceneJson
) {
    public static VignetteDto from(Thumbnail t) {
        return new VignetteDto(t.getId(), t.getScenarioId(), t.getTitle(), t.getIdx(), t.getVignetteSceneJson());
    }
}
