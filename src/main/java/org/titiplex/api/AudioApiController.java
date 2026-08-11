package org.titiplex.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.titiplex.api.dto.ApiError;
import org.titiplex.api.dto.AudioRowDto;
import org.titiplex.api.dto.CreateAudioResponse;
import org.titiplex.api.dto.LanguagePreviewAudioDto;
import org.titiplex.api.dto.ScenarioBackgroundAudioDto;
import org.titiplex.api.dto.UpdateMarkerRequest;
import org.titiplex.api.security.*;
import org.titiplex.persistence.model.ScenarioHistoryAction;
import org.titiplex.service.AudioService;
import org.titiplex.service.ScenarioHistoryService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.ThumbnailService;
import org.titiplex.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(
        name = "Audio",
        description = "Endpoint for fetching and managing audio files and associated metadata."
)
public class AudioApiController {


    private final AudioService audioService;
    private final UserService userService;
    private final ThumbnailService thumbnailService;
    private final ScenarioService scenarioService;
    private final ScenarioHistoryService scenarioHistoryService;

    public AudioApiController(
            AudioService audioService,
            UserService userService,
            ThumbnailService thumbnailService,
            ScenarioService scenarioService,
            ScenarioHistoryService scenarioHistoryService
    ) {
        this.audioService = audioService;
        this.userService = userService;
        this.thumbnailService = thumbnailService;
        this.scenarioService = scenarioService;
        this.scenarioHistoryService = scenarioHistoryService;
    }

    /**
     * Retrieves a list of audio records associated with a specific thumbnail.
     *
     * @param thumbId ({@link Long}) the ID of the thumbnail for which audio records are to be retrieved
     * @return a {@link List} of {@link AudioRowDto} objects representing the audio records associated with the specified thumbnail
     */
    @Operation(
            summary = "List audio files for a thumbnail",
            description = "Returns all audio files associated with a thumbnail."
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved audio list",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AudioRowDto.class)),
                            examples = @ExampleObject(
                                    name = "Example Audio List",
                                    value = "[{\"id\": 1, \"title\": \"Audio 1\", \"idx\": 1, \"mime\": \"png\"},\n {\"id\": 2, \"title\": \"Audio 2\", \"idx\": 2, \"mime\": \"png\"}]\n"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Thumbnail not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/thumbnails/{thumbId}/audios")
    public List<AudioRowDto> list(
            @Parameter(description = "ID of the thumbnail to retrieve the audio files for", required = true)
            @PathVariable Long thumbId,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        var thumbnail = thumbnailService.getThumbnailById(thumbId);
        var scenario = scenarioService.getRequiredScenario(thumbnail.getScenarioId());
        scenarioService.assertCanViewScenario(scenario, auth);
        return audioService.listForThumbnail(thumbId);
    }

    @Operation(
            summary = "List scenario background audio",
            description = "Returns scenario-level background ambience or soundtrack clips."
    )
    @PublicOperation
    @GetMapping("/scenarios/{scenarioId}/background-audios")
    public List<ScenarioBackgroundAudioDto> listBackground(
            @Parameter(description = "ID of the scenario to retrieve background audio for", required = true)
            @PathVariable Long scenarioId,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        var scenario = scenarioService.getRequiredScenario(scenarioId);
        scenarioService.assertCanViewScenario(scenario, auth);
        return audioService.listBackgroundForScenario(scenarioId);
    }

    @Operation(
            summary = "Uploads scenario background audio",
            description = "Uploads an ambience or soundtrack clip for the whole scenario."
    )
    @OwnerOrAdminOperation(
            resource = ProtectedResource.SCENARIO,
            param = "scenarioId"
    )
    @PostMapping(value = "/scenarios/{scenarioId}/background-audios", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CreateAudioResponse uploadBackground(
            @Parameter(description = "ID of the scenario to associate the background audio with", required = true)
            @PathVariable Long scenarioId,

            @Parameter(description = "Title of the background audio")
            @RequestParam(defaultValue = "") String title,

            @Parameter(description = "Credit or source label for the background audio")
            @RequestParam(defaultValue = "") String sourceLabel,

            @Parameter(description = "Source URL for attribution or provenance")
            @RequestParam(defaultValue = "") String sourceUrl,

            @Parameter(description = "Background audio file to upload.", required = true)
            @RequestPart("audio") MultipartFile audio,

            @Parameter(hidden = true)
            Authentication auth
    ) throws Exception {
        Long authorId = userService.getUserByUsername(auth.getName()).getId();
        Long id = audioService.createBackgroundAudio(scenarioId, title, sourceLabel, sourceUrl, authorId, audio);
        return new CreateAudioResponse(id);
    }

    /**
     * Retrieves the content of an audio file by its ID and returns it.
     * The response includes the appropriate MIME type and caching headers.
     *
     * @param id ({@link Long}) the unique identifier of the audio file
     * @return a {@link ResponseEntity} containing the representation of the audio content,
     * the MIME type of the file, and caching headers
     */
    @Operation(
            summary = "Retrieves the content of an audio file.",
            description = "Returns the audio file content, with appropriate MIME type and caching headers."
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Audio content retrieved successfully",
                    content = @Content(
                            mediaType = "audio/*",
                            schema = @Schema(implementation = ResponseEntity.class, contains = Resource.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Audio not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/audios/{id}/content")
    public ResponseEntity<Resource> content(
            @Parameter(description = "ID of the audio file to retrieve", required = true)
            @PathVariable Long id
    ) {
        var media = audioService.loadContent(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .contentLength(media.sizeBytes())
                .eTag(media.etag())
                .header("Cache-Control", "private, max-age=3600")
                .body(media.resource());
    }

    /**
     * Uploads a new audio file and associates it with a specific thumbnail.
     * Optionally sets metadata such as title, index, and marker information.
     *
     * @param thumbId     ({@link Long}) the ID of the thumbnail to associate the audio file with
     * @param title       the title of the audio, defaults to an empty string if not provided
     * @param idx         an optional index for ordering or identifying the audio
     * @param markerX     an optional x-coordinate for the marker
     * @param markerY     an optional y-coordinate for the marker
     * @param markerLabel an optional label for the marker, defaults to an empty string if not provided
     * @param audio       ({@link MultipartFile}) the audio file to be uploaded; must be provided in multipart form data
     * @param auth        the authentication information of the current user
     * @return a {@link CreateAudioResponse} object containing the ID of the newly created audio record
     * @throws Exception if any error occurs during the upload process
     */
    @Operation(
            summary = "Uploads an audio file.",
            description = """
                    Uploads an audio file and associates it with a thumbnail.
                    
                    Request content type:
                     - multipart/form-data
                    """
    )
    @OwnerOrAdminOperation(
            resource = ProtectedResource.THUMBNAIL,
            param = "thumbId"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Audio uploaded successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateAudioResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
//    @ApiAccess(
//            level = ApiAccessLevel.OWNER_OR_ADMIN,
//            rule = "Requires authentication. Authorization: related resource owner or ADMIN only.",
//            ownerResource = "thumbnail"
//    )
//    @SecurityRequirement(name = "bearerAuth")
//    @PreAuthorize("hasRole('USER') and @scenarioSecurity.isOwnerByThumbnailId(#thumbId, authentication.name)")
    @PostMapping(value = "/thumbnails/{thumbId}/audios", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CreateAudioResponse upload(
            @Parameter(description = "ID of the thumbnail to associate the audio file with", required = true)
            @PathVariable Long thumbId,

            @Parameter(description = "Title of the audio file")
            @RequestParam(defaultValue = "") String title,

            @Parameter(description = "Index of the audio file (order of the audio among all audios associated with a thumbnail.", example = "1")
            @RequestParam(required = false) @Min(1) Integer idx,

            @Parameter(description = "X-coordinate of the marker.")
            @RequestParam(required = false) Double markerX,

            @Parameter(description = "Y-coordinate of the marker.")
            @RequestParam(required = false) Double markerY,

            @Parameter(description = "Label of the marker.")
            @RequestParam(defaultValue = "") String markerLabel,

            @Parameter(description = "Audio file to upload.", required = true)
            @RequestPart("audio") MultipartFile audio,

            @Parameter(hidden = true)
            Authentication auth
    ) throws Exception {

        Long authorId = userService.getUserByUsername(auth.getName()).getId();
        Long id = audioService.createAudio(thumbId, title, idx, authorId, audio, markerX, markerY, markerLabel);

        Long scenarioId = audioService.getScenarioIdForAudio(id);
        String historySummary = (title != null && !title.isBlank()) ? "Added audio \"" + title.trim() + "\"" : "Added an audio clip";
        scenarioHistoryService.record(scenarioId, authorId, ScenarioHistoryAction.AUDIO_ADDED, historySummary);

        return new CreateAudioResponse(id);
    }

    /**
     * Updates the marker information for a specific audio file.
     *
     * @param audioId ({@link Long}) the ID of the audio file whose marker is being updated
     * @param req     the request body containing the marker information, including:
     *                - {@code markerX}: the x-coordinate of the marker (must be between 0 and 100, or null if not set)
     *                - {@code markerY}: the y-coordinate of the marker (must be between 0 and 100, or null if not set)
     *                - {@code markerLabel}: the label for the marker (can be null or blank if not set)
     */
    @Operation(
            summary = "Update an audio marker.",
            description = """
                    Updates the marker for an audio, could it be modifying it or creating it for the first time.
                    """
    )
    @OwnerOrAdminOperation(
            resource = ProtectedResource.AUDIO,
            param = "audioId"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Marker updated successfully"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Audio not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
//    @ApiAccess(
//            level = ApiAccessLevel.OWNER_OR_ADMIN,
//            rule = "Requires authentication. Authorization: related resource owner or ADMIN only.",
//            ownerResource = "audio"
//    )
//    @SecurityRequirement(name = "bearerAuth")
//    @PreAuthorize("hasRole('USER') and @scenarioSecurity.isOwnerByAudioId(#audioId, authentication.name, @audioService)")
    @PatchMapping("/audios/{audioId}/marker")
    public void updateMarker(
            @Parameter(description = "ID of the audio file whose marker is to be updated", required = true)
            @PathVariable Long audioId,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "New marker information.",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = UpdateMarkerRequest.class)
                    )
            )
            @RequestBody UpdateMarkerRequest req,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        Long scenarioId = audioService.getScenarioIdForAudio(audioId);
        audioService.updateMarker(audioId, req.markerX(), req.markerY(), req.markerLabel());

        Long actorId = userService.getUserByUsername(auth.getName()).getId();
        scenarioHistoryService.record(scenarioId, actorId, ScenarioHistoryAction.AUDIO_UPDATED, "Updated an audio marker");
    }

    /**
     * Deletes an audio record identified by the given audio ID.
     * The operation is restricted to users who have the appropriate ownership rights.
     *
     * @param audioId ({@link Long}) the ID of the audio file to be deleted
     */
    @Operation(
            summary = "Deletes an audio file.",
            description = "Deletes the audio file associated to the given ID. One must have the appropriate permissions (owner/manager of the audio or admin)."
    )
    @OwnerOrAdminOperation(
            resource = ProtectedResource.AUDIO,
            param = "audioId"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Audio successfully deleted"),
            @ApiResponse(responseCode = "404", description = "Audio not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden : not the owner", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @ApiAccess(
            level = ApiAccessLevel.OWNER_OR_ADMIN,
            rule = "Requires authentication. Authorization: related resource owner or ADMIN only.",
            ownerResource = "audio"
    )
//    @SecurityRequirement(name = "bearerAuth")
//    @PreAuthorize("hasRole('USER') and @scenarioSecurity.isOwnerByAudioId(#audioId, authentication.name, @audioService)")
    @DeleteMapping("/audios/{audioId}")
    public void delete(
            @Parameter(description = "ID of the audio file to delete", required = true)
            @PathVariable Long audioId,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        Long scenarioId = audioService.getScenarioIdForAudio(audioId);
        audioService.deleteAudio(audioId);

        Long actorId = userService.getUserByUsername(auth.getName()).getId();
        scenarioHistoryService.record(scenarioId, actorId, ScenarioHistoryAction.AUDIO_DELETED, "Deleted an audio clip");
    }

    /**
     * List all audios for a specific language.
     *
     * @param langId the glottolog id for the language
     * @return a {@link List} of {@link AudioRowDto} objects representing the audio records associated with the specified thumbnail
     */
    @Operation(
            summary = "List audio files for a language",
            description = "Returns all audio files associated with a language."
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved audio list",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AudioRowDto.class)),
                            examples = @ExampleObject(
                                    name = "Example Audio List",
                                    value = "[{\"id\": 1, \"title\": \"Audio 1\", \"idx\": 1, \"mime\": \"png\"},\n {\"id\": 2, \"title\": \"Audio 2\", \"idx\": 2, \"mime\": \"png\"}]\n"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Language not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/languages/{langId}/audios")
    public List<AudioRowDto> listByLanguage(
            @Parameter(description = "ID of the language to retrieve the audio files for", required = true)
            @PathVariable String langId
    ) {
        return audioService.listForLanguage(langId);
    }

    @Operation(
            summary = "Get representative preview audio for a language",
            description = """
                    Returns a single representative audio preview for a language.
                    Selection rule:
                    - first published scenario for the language
                    - then first thumbnail in that scenario
                    - then first audio in that thumbnail
                    """
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Preview audio retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LanguagePreviewAudioDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No preview audio found for this language",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/languages/{langId}/preview-audio")
    public LanguagePreviewAudioDto previewByLanguage(
            @Parameter(description = "ID of the language to retrieve the preview audio for", required = true)
            @PathVariable String langId
    ) {
        return audioService.getLanguagePreviewAudio(langId);
    }
}
