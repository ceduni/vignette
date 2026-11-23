package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.titiplex.persistence.model.Thumbnail;

@Schema(description = "Thumbnail row information with storyboard metadata")
public record ThumbnailRowDto(
        @Schema(description = "Unique thumbnail identifier")
        Long id,
        @Schema(description = "Title of the thumbnail", example = "Introduction Scene")
        String title,
        @Schema(description = "Index/order of the thumbnail in the scenario", example = "1")
        Integer idx,
        @Schema(description = "Storyboard column start")
        Integer gridColumn,
        @Schema(description = "Storyboard row start")
        Integer gridRow,
        @Schema(description = "Storyboard column span")
        Integer gridColumnSpan,
        @Schema(description = "Storyboard row span")
        Integer gridRowSpan,
        @Schema(description = "Stored image width")
        Integer imageWidth,
        @Schema(description = "Stored image height")
        Integer imageHeight,
        @Schema(description = "True when the image was made with the Vignette Maker and can be reopened in it")
        boolean vignette
) {
    public static ThumbnailRowDto from(Thumbnail t) {
        return new ThumbnailRowDto(
                t.getId(),
                t.getTitle(),
                t.getIdx(),
                t.getGridColumn(),
                t.getGridRow(),
                t.getGridColumnSpan(),
                t.getGridRowSpan(),
                t.getImageWidth(),
                t.getImageHeight(),
                t.getVignetteSceneJson() != null
        );
    }
}
