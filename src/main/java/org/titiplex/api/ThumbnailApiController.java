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
import org.titiplex.api.docs.BadRequestResponse;
import org.titiplex.api.docs.ForbiddenResponse;
import org.titiplex.api.docs.NotFoundResponse;
import org.titiplex.api.docs.UnauthorizedResponse;
import org.titiplex.api.dto.ApiError;
import org.titiplex.api.dto.ThumbnailRowDto;
import org.titiplex.api.dto.UpdateThumbnailLayoutRequest;
import org.titiplex.api.dto.UpdateThumbnailTitleRequest;
import org.titiplex.api.dto.UploadResponse;
import org.titiplex.api.security.ApiAccess;
import org.titiplex.api.security.ApiAccessLevel;
import org.titiplex.api.security.OwnerOrAdminOperation;
import org.titiplex.api.security.ProtectedResource;
import org.titiplex.api.security.PublicOperation;
import org.titiplex.service.ThumbnailManagementService;
import org.titiplex.persistence.model.Thumbnail;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(
        name = "Thumbnail",
        description = "Endpoints for managing thumbnail images associated with scenarios, including listing, uploading, and retrieving thumbnail content."
)
public class ThumbnailApiController {

    private final ThumbnailManagementService thumbnailManagement;

    public ThumbnailApiController(ThumbnailManagementService thumbnailManagement) {
        this.thumbnailManagement = thumbnailManagement;
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
    @NotFoundResponse
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
        return thumbnailManagement.list(scenarioId, auth).stream()
                .map(ThumbnailRowDto::from)
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
    @BadRequestResponse
    @UnauthorizedResponse
    @ForbiddenResponse
    @NotFoundResponse
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

        return new UploadResponse(thumbnailManagement.upload(scenarioId, title, image, auth).getId());
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
    @ApiAccess(level = ApiAccessLevel.PUBLIC,
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
        var media = thumbnailManagement.loadContent(id, auth);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .contentLength(media.sizeBytes())
                .eTag(media.etag())
                .header("Cache-Control", "private, no-store")
                // Second barrier behind SvgSanitizer: even if an SVG kept something active,
                // opening the image URL directly cannot run scripts or load anything.
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
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
    @BadRequestResponse
    @UnauthorizedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Thumbnail layout updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ThumbnailRowDto.class)
                    )
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
        return ThumbnailRowDto.from(thumbnailManagement.updateLayout(id, req, auth));
    }

    public record ReorderRequest(List<Long> thumbnailIds) {}

    @PatchMapping("/scenarios/{scenarioId}/thumbnails/reorder")
    public List<ThumbnailRowDto> reorder(@PathVariable Long scenarioId,
            @RequestBody ReorderRequest request, Authentication auth) {
        return thumbnailManagement.reorder(scenarioId, request.thumbnailIds(), auth).stream()
                .map(ThumbnailRowDto::from)
                .toList();
    }

    @PatchMapping("/thumbnails/{id}/title")
    public ThumbnailRowDto updateTitle(
            @PathVariable Long id,
            @RequestBody UpdateThumbnailTitleRequest req,
            Authentication auth
    ) {
        return ThumbnailRowDto.from(thumbnailManagement.updateTitle(id, req.title(), auth));
    }

    @Operation(
            summary = "Delete a thumbnail",
            description = "Deletes a thumbnail and its associated audio clips. Requires scenario ownership or admin privileges."
    )
    @OwnerOrAdminOperation(
            resource = ProtectedResource.THUMBNAIL
    )
    @UnauthorizedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Thumbnail successfully deleted")
    })
    @DeleteMapping("/thumbnails/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "ID of the thumbnail to delete", required = true)
            @PathVariable Long id,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        thumbnailManagement.delete(id, auth);
    }
}
