package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AudioRow", description = "Audio file Metadata.")
public record AudioRowDto(
        @Schema(description = "Audio file ID (unique).")
        Long id,
        @Schema(description = "Audio file title.", example = "My audio file", defaultValue = "\"\"")
        String title,
        @Schema(
                description = "Index of the audio file among all audios associated to the corresponding thumbnail.",
                example = "5",
                minimum = "1"
        )
        Integer idx,
        @Schema(description = "MIME type of the audio file.", example = "audio/mpeg")
        String mime,
        @Schema(description = "X axis of the audio marker.", example = "15.8")
        Double markerX,
        @Schema(description = "Y axis of the audio marker.", example = "12.3")
        Double markerY,
        @Schema(description = "Label of the audio marker.", example = "Person number 1")
        String markerLabel,
        String transcription,
        String gloss,
        String freeTranslation
) {
}
