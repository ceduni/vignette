package org.titiplex.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.titiplex.api.docs.BadRequestResponse;
import org.titiplex.api.docs.ConflictResponse;
import org.titiplex.api.docs.ForbiddenResponse;
import org.titiplex.api.docs.NotFoundResponse;
import org.titiplex.api.docs.UnauthorizedResponse;
import org.titiplex.api.dto.VignetteDto;
import org.titiplex.api.security.OwnerOrAdminOperation;
import org.titiplex.api.security.ProtectedResource;
import org.titiplex.api.security.PublicOperation;
import org.titiplex.service.VignetteService;

/**
 * Saves Vignette Maker scenes into a scenario. The exported image becomes a scene image
 * (listed with the other thumbnails) and keeps the drawing, so it can be reopened and edited.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Vignette", description = "Scene images made with the Vignette Maker: save into a scenario, reopen, edit.")
public class VignetteApiController {

    private final VignetteService vignetteService;

    public VignetteApiController(VignetteService vignetteService) {
        this.vignetteService = vignetteService;
    }

    @Operation(
            summary = "Save a vignette into a scenario",
            description = """
                    Adds the exported image as a new scene at the end of the scenario and keeps the
                    Vignette Maker drawing with it.

                    Request content type: multipart/form-data
                    """
    )
    @OwnerOrAdminOperation(resource = ProtectedResource.SCENARIO, param = "scenarioId")
    @BadRequestResponse
    @UnauthorizedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @ConflictResponse
    @ApiResponse(
            responseCode = "201",
            description = "Vignette saved",
            content = @Content(schema = @Schema(implementation = VignetteDto.class))
    )
    @PostMapping(value = "/scenarios/{scenarioId}/vignettes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VignetteDto create(
            @Parameter(description = "ID of the scenario", required = true, example = "7")
            @PathVariable Long scenarioId,

            @Parameter(description = "Optional title of the scene", example = "At the market")
            @RequestParam(required = false, defaultValue = "") String title,

            @Parameter(description = "Vignette Maker drawing (JSON object)", required = true)
            @RequestParam String sceneJson,

            @Parameter(description = "Exported image of the vignette (SVG)", required = true)
            @RequestPart("image") MultipartFile image,

            @Parameter(hidden = true) Authentication auth
    ) throws Exception {
        return VignetteDto.from(vignetteService.create(scenarioId, title, image, sceneJson, auth));
    }

    @Operation(
            summary = "Get a vignette's drawing",
            description = "Returns the Vignette Maker drawing of a scene image, to reopen it in the editor."
    )
    @PublicOperation
    @NotFoundResponse
    @ApiResponse(
            responseCode = "200",
            description = "Vignette found",
            content = @Content(schema = @Schema(implementation = VignetteDto.class))
    )
    @GetMapping("/thumbnails/{id}/vignette")
    public VignetteDto get(
            @Parameter(description = "ID of the scene image", required = true, example = "42")
            @PathVariable Long id,

            @Parameter(hidden = true) Authentication auth
    ) {
        return VignetteDto.from(vignetteService.get(id, auth));
    }

    @Operation(
            summary = "Update a vignette",
            description = """
                    Replaces the image and the drawing of a scene made with the Vignette Maker.
                    Its position, title, layout and audios are kept.

                    Request content type: multipart/form-data
                    """
    )
    @OwnerOrAdminOperation(resource = ProtectedResource.THUMBNAIL, param = "id")
    @BadRequestResponse
    @UnauthorizedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @ConflictResponse
    @ApiResponse(
            responseCode = "200",
            description = "Vignette updated",
            content = @Content(schema = @Schema(implementation = VignetteDto.class))
    )
    @PutMapping(value = "/thumbnails/{id}/vignette", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VignetteDto update(
            @Parameter(description = "ID of the scene image", required = true, example = "42")
            @PathVariable Long id,

            @Parameter(description = "Vignette Maker drawing (JSON object)", required = true)
            @RequestParam String sceneJson,

            @Parameter(description = "New exported image of the vignette (SVG)", required = true)
            @RequestPart("image") MultipartFile image,

            @Parameter(hidden = true) Authentication auth
    ) throws Exception {
        return VignetteDto.from(vignetteService.update(id, image, sceneJson, auth));
    }
}
