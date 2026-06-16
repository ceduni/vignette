package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LanguagePreviewAudio", description = "Representative preview audio for a language.")
public record LanguagePreviewAudioDto(
        @Schema(description = "Audio file ID", example = "42")
        Long id,
        @Schema(description = "Audio title", example = "Greeting clip")
        String title,
        @Schema(description = "Audio MIME type", example = "audio/webm")
        String mime,
        @Schema(description = "Relative API URL used to stream the audio content", example = "/api/audios/42/content")
        String contentUrl,
        @Schema(description = "Scenario ID associated with this audio", example = "12")
        Long scenarioId,
        @Schema(description = "Scenario title associated with this audio", example = "Basic greetings")
        String scenarioTitle,
        @Schema(description = "Thumbnail ID associated with this audio", example = "7")
        Long thumbnailId,
        @Schema(description = "Language ID associated with this audio", example = "bamb1269")
        String languageId,
        @Schema(description = "Audio index inside its thumbnail", example = "1")
        Integer idx,
        @Schema(description = "Duration in seconds when known", nullable = true, example = "null")
        Integer durationSeconds
) {
}
