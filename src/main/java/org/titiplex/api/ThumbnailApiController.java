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
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.titiplex.api.dto.ApiError;
import org.titiplex.api.dto.ThumbnailRowDto;
import org.titiplex.api.dto.UpdateThumbnailLayoutRequest;
import org.titiplex.api.dto.UpdateThumbnailTitleRequest;
import org.titiplex.api.dto.UploadResponse;
import org.titiplex.api.security.OwnerOrAdminOperation;
import org.titiplex.api.security.ProtectedResource;
import org.titiplex.api.security.PublicOperation;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioHistoryAction;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.model.User;
import org.titiplex.service.ScenarioHistoryService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.ThumbnailService;
import org.titiplex.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(
        name = "Thumbnail",
        description = "Endpoints for managing thumbnail images associated with scenarios, including listing, uploading, and retrieving thumbnail content."
)
public class ThumbnailApiController {

    private final ThumbnailService thumbnailService;
    private final UserService userService;
    private final ScenarioService scenarioService;
    private final ScenarioHistoryService scenarioHistoryService;

    public ThumbnailApiController(
            ThumbnailService thumbnailService,
            UserService userService,
            ScenarioService scenarioService,
            ScenarioHistoryService scenarioHistoryService
    ) {
        this.thumbnailService = thumbnailService;
        this.userService = userService;
        this.scenarioService = scenarioService;
        this.scenarioHistoryService = scenarioHistoryService;
    }

    /**
     * Retrieves a list of thumbnails associated with a specific scenario ID.
     *
     * @param scenarioId the ID ({@link Long}) of the scenario for which thumbnails are to be retrieved
     * @return a {@link List} of {@link ThumbnailRowDto} objects representing the thumbnails of the specified scenario
     */
    @Operation(
            summary = "List thumbnails for a scenario",
            description = "Returns all thumbnails associated with a specific scenario, ordered by their index."
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Thumbnails retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ThumbnailRowDto.class)),
                            examples = @ExampleObject(
                                    name = "Example Thumbnail List",
                                    value = "[{\"id\": 1, \"title\": \"Scene 1\", \"idx\": 1}, {\"id\": 2, \"title\": \"Scene 2\", \"idx\": 2}]"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scenario not found with the specified ID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/scenarios/{scenarioId}/thumbnails")
    public List<ThumbnailRowDto> list(
            @Parameter(
                    description = "ID of the scenario to retrieve thumbnails for.",
                    required = true
            )
            @PathVariable Long scenarioId,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        Scenario scenario = scenarioService.getRequiredScenario(scenarioId);
        scenarioService.assertCanViewScenario(scenario, auth);

        return thumbnailService.listByScenarioId(scenarioId).stream()
                .map(t -> new ThumbnailRowDto(
                        t.getId(),
                        t.getTitle(),
                        t.getIdx(),
                        t.getGridColumn(),
                        t.getGridRow(),
                        t.getGridColumnSpan(),
                        t.getGridRowSpan(),
                        t.getImageWidth(),
                        t.getImageHeight()
                ))
                .toList();
    }

    /**
     * Uploads a thumbnail image for a specified scenario. The method requires proper authorization.
     * Users with 'ADMIN' role or users with 'USER' role who are owners of the specified scenario can upload thumbnails.
     *
     * @param scenarioId the ID ({@link Long}) of the scenario to which the thumbnail is associated
     * @param title      an optional title for the uploaded thumbnail; defaults to an empty string if not provided
     * @param image      the image file ({@link MultipartFile}) to be uploaded as a thumbnail; must not be null or empty
     * @param auth       the authentication object representing the currently authenticated user
     * @return an {@link UploadResponse} containing the ID of the newly uploaded thumbnail
     */
    @Operation(
            summary = "Upload a thumbnail image",
            description = """
                    Uploads a thumbnail image for a scenario.
                    
                    Request content type:
                     - multipart/form-data
                    """
    )
    @OwnerOrAdminOperation(
            resource = ProtectedResource.SCENARIO,
            param = "scenarioId"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Thumbnail uploaded successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UploadResponse.class),
                            examples = @ExampleObject(
                                    name = "Upload Success Response",
                                    value = "{\"id\": 42}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input : missing or empty image file",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden : user is not the scenario owner or admin",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scenario not found with the specified ID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "415",
                    description = "Unsupported media type : invalid image format",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error : I/O error during image processing",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
//    @ApiAccess(
//            level = ApiAccessLevel.OWNER_OR_ADMIN,
//            rule = "Requires authentication. Authorization: scenario owner or ADMIN only.",
//            ownerResource = "scenario"
//    )
//    @SecurityRequirement(name = "bearerAuth")
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('USER') and @scenarioSecurity.isOwner(#scenarioId, authentication.name))")
    @PostMapping(value = "/scenarios/{scenarioId}/thumbnails", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(
            @Parameter(
                    description = "ID of the scenario to associate the thumbnail with",
                    required = true
            )
            @PathVariable Long scenarioId,

            @Parameter(
                    description = "Optional title for the thumbnail",
                    example = "Introduction Scene"
            )
            @RequestParam(required = false, defaultValue = "") String title,

            @Parameter(
                    description = "Image file to upload (JPEG, PNG, etc.)",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestPart("image") MultipartFile image,

            @Parameter(hidden = true)
            Authentication auth
    ) throws Exception {

        if (image == null || image.isEmpty()) throw new IllegalArgumentException("Image is required");

        User user = userService.getUserByUsername(auth.getName());
        Scenario scenario = scenarioService.getRequiredScenario(scenarioId);
        scenarioService.assertCanEditScenario(scenario, auth);

        Thumbnail saved = thumbnailService.save(title, image, scenario, user);

        String historySummary = (saved.getTitle() != null && !saved.getTitle().isBlank())
                ? "Added thumbnail \"" + saved.getTitle() + "\""
                : "Added a thumbnail";
        scenarioHistoryService.record(scenario.getId(), user.getId(), ScenarioHistoryAction.THUMBNAIL_ADDED, historySummary);

        return new UploadResponse(saved.getId());
    }

    /**
     * Retrieves the binary content of a thumbnail image by its ID.
     *
     * @param id the ID ({@link Long}) of the thumbnail whose binary content is to be retrieved
     * @return a {@link ResponseEntity} containing the content's stream of the thumbnail image
     * with the appropriate content type header, or the default content type
     * of "application/octet-stream" if none is specified
     */
    @Operation(
            summary = "Get thumbnail image content",
            description = "Returns the raw thumbnail content with the appropriate MIME type and cache headers."
    )
    @org.titiplex.api.security.ApiAccess(level = org.titiplex.api.security.ApiAccessLevel.PUBLIC,
            rule = "Published media is public. Draft media requires permission to view its scenario.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Thumbnail content retrieved successfully",
                    content = @Content(
                            mediaType = "image/*",
                            schema = @Schema(implementation = ResponseEntity.class, contains = Resource.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Thumbnail not found with the specified ID"
            )
    })
    @GetMapping("/thumbnails/{id}/content")
    public ResponseEntity<Resource> content(
            @Parameter(
                    description = "ID of the thumbnail to retrieve",
                    required = true,
                    example = "1"
            )
            @PathVariable Long id,
            @Parameter(hidden = true) Authentication auth
    ) {
        var thumbnail = thumbnailService.getThumbnailById(id);
        var scenario = scenarioService.getRequiredScenario(thumbnail.getScenarioId());
        scenarioService.assertCanViewScenario(scenario, auth);
        var media = thumbnailService.loadContent(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .contentLength(media.sizeBytes())
                .eTag(media.etag())
                .header("Cache-Control", "private, no-store")
                .body(media.resource());
    }

    @Operation(
            summary = "Update thumbnail layout",
            description = """
                    Updates the persisted layout position of a thumbnail inside a storyboard.
                    
                    Editable fields include:
                    - gridColumn
                    - gridRow
                    - gridColumnSpan
                    - gridRowSpan
                    
                    Requires scenario ownership or admin privileges.
                    """
    )
    @OwnerOrAdminOperation(
            resource = ProtectedResource.THUMBNAIL
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Thumbnail layout updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ThumbnailRowDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid layout request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User is not allowed to edit this thumbnail layout",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Thumbnail or parent scenario not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PatchMapping("/thumbnails/{id}/layout")
    public ThumbnailRowDto updateLayout(
            @Parameter(
                    description = "ID of the thumbnail to update",
                    required = true,
                    example = "7"
            )
            @PathVariable Long id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "New layout values for the thumbnail",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = UpdateThumbnailLayoutRequest.class),
                            examples = @ExampleObject(
                                    name = "Thumbnail layout update",
                                    value = """
                                            {
                                              "gridColumn": 2,
                                              "gridRow": 1,
                                              "gridColumnSpan": 1,
                                              "gridRowSpan": 2
                                            }
                                            """
                            )
                    )
            )
            @RequestBody UpdateThumbnailLayoutRequest req,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        Thumbnail thumbnail = thumbnailService.getThumbnailById(id);
        Scenario scenario = scenarioService.getRequiredScenario(thumbnail.getScenarioId());
        scenarioService.assertCanEditScenario(scenario, auth);

        Thumbnail saved = thumbnailService.updateLayout(id, req);

        Long actorId = userService.getUserByUsername(auth.getName()).getId();
        scenarioHistoryService.record(scenario.getId(), actorId, ScenarioHistoryAction.THUMBNAIL_UPDATED, "Repositioned a thumbnail");

        return new ThumbnailRowDto(
                saved.getId(),
                saved.getTitle(),
                saved.getIdx(),
                saved.getGridColumn(),
                saved.getGridRow(),
                saved.getGridColumnSpan(),
                saved.getGridRowSpan(),
                saved.getImageWidth(),
                saved.getImageHeight()
        );
    }

    public record ReorderRequest(List<Long> thumbnailIds) {}

    @PatchMapping("/scenarios/{scenarioId}/thumbnails/reorder")
    public List<ThumbnailRowDto> reorder(@PathVariable Long scenarioId,
            @RequestBody ReorderRequest request, Authentication auth) {
        Scenario scenario = scenarioService.getRequiredScenario(scenarioId);
        scenarioService.assertCanEditScenario(scenario, auth);
        var rows = thumbnailService.reorder(scenarioId, request.thumbnailIds());
        Long actorId = userService.getUserByUsername(auth.getName()).getId();
        scenarioHistoryService.record(scenarioId, actorId, ScenarioHistoryAction.THUMBNAIL_UPDATED, "Reordered scenes");
        return rows.stream().map(t -> new ThumbnailRowDto(t.getId(), t.getTitle(), t.getIdx(),
                t.getGridColumn(), t.getGridRow(), t.getGridColumnSpan(), t.getGridRowSpan(),
                t.getImageWidth(), t.getImageHeight())).toList();
    }

    @PatchMapping("/thumbnails/{id}/title")
    public ThumbnailRowDto updateTitle(
            @PathVariable Long id,
            @RequestBody UpdateThumbnailTitleRequest req,
            Authentication auth
    ) {
        Thumbnail thumbnail = thumbnailService.getThumbnailById(id);
        Scenario scenario = scenarioService.getRequiredScenario(thumbnail.getScenarioId());
        scenarioService.assertCanEditScenario(scenario, auth);

        Thumbnail saved = thumbnailService.updateTitle(id, req.title());
        Long actorId = userService.getUserByUsername(auth.getName()).getId();
        scenarioHistoryService.record(scenario.getId(), actorId, ScenarioHistoryAction.THUMBNAIL_UPDATED, "Renamed a thumbnail");

        return new ThumbnailRowDto(
                saved.getId(),
                saved.getTitle(),
                saved.getIdx(),
                saved.getGridColumn(),
                saved.getGridRow(),
                saved.getGridColumnSpan(),
                saved.getGridRowSpan(),
                saved.getImageWidth(),
                saved.getImageHeight()
        );
    }

    @Operation(
            summary = "Delete a thumbnail",
            description = "Deletes a thumbnail and its associated audio clips. Requires scenario ownership or admin privileges."
    )
    @OwnerOrAdminOperation(
            resource = ProtectedResource.THUMBNAIL
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Thumbnail successfully deleted"),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden : not the scenario owner or admin", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Thumbnail not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/thumbnails/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "ID of the thumbnail to delete", required = true)
            @PathVariable Long id,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        Thumbnail thumbnail = thumbnailService.getThumbnailById(id);
        Long scenarioId = thumbnail.getScenarioId();

        thumbnailService.delete(id);

        Long actorId = userService.getUserByUsername(auth.getName()).getId();
        scenarioHistoryService.record(scenarioId, actorId, ScenarioHistoryAction.THUMBNAIL_DELETED, "Deleted a thumbnail");
    }
}
