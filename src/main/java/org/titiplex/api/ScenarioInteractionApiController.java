package org.titiplex.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.titiplex.api.dto.ApiError;
import org.titiplex.api.dto.ScenarioInteractionStatusDto;
import org.titiplex.api.security.UserOperation;
import org.titiplex.service.ScenarioInteractionService;

@RestController
@RequestMapping("/api/scenarios")
@Tag(
        name = "Scenario Interactions",
        description = "Endpoints for liking and bookmarking scenarios."
)
public class ScenarioInteractionApiController {

    private final ScenarioInteractionService interactionService;

    public ScenarioInteractionApiController(ScenarioInteractionService interactionService) {
        this.interactionService = interactionService;
    }

    // ── Likes ──────────────────────────────────────────────────────────────

    @Operation(summary = "Like a scenario", description = "Adds a like to the specified scenario for the authenticated user.")
    @UserOperation
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Like added",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ScenarioInteractionStatusDto.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Scenario not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/like")
    public ScenarioInteractionStatusDto likeScenario(
            @Parameter(description = "ID of the scenario to like", required = true)
            @PathVariable Long id,
            @Parameter(hidden = true) Authentication auth
    ) {
        return interactionService.likeScenario(id, auth.getName());
    }

    @Operation(summary = "Unlike a scenario", description = "Removes the like from the specified scenario for the authenticated user.")
    @UserOperation
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Like removed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ScenarioInteractionStatusDto.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Scenario not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}/like")
    public ScenarioInteractionStatusDto unlikeScenario(
            @Parameter(description = "ID of the scenario to unlike", required = true)
            @PathVariable Long id,
            @Parameter(hidden = true) Authentication auth
    ) {
        return interactionService.unlikeScenario(id, auth.getName());
    }

    // ── Bookmarks ──────────────────────────────────────────────────────────

    @Operation(summary = "Bookmark a scenario", description = "Adds a bookmark to the specified scenario for the authenticated user.")
    @UserOperation
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bookmark added",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ScenarioInteractionStatusDto.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Scenario not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/bookmark")
    public ScenarioInteractionStatusDto bookmarkScenario(
            @Parameter(description = "ID of the scenario to bookmark", required = true)
            @PathVariable Long id,
            @Parameter(hidden = true) Authentication auth
    ) {
        return interactionService.bookmarkScenario(id, auth.getName());
    }

    @Operation(summary = "Remove bookmark", description = "Removes the bookmark from the specified scenario for the authenticated user.")
    @UserOperation
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bookmark removed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ScenarioInteractionStatusDto.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Scenario not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}/bookmark")
    public ScenarioInteractionStatusDto unbookmarkScenario(
            @Parameter(description = "ID of the scenario to unbookmark", required = true)
            @PathVariable Long id,
            @Parameter(hidden = true) Authentication auth
    ) {
        return interactionService.unbookmarkScenario(id, auth.getName());
    }

    // ── Status ─────────────────────────────────────────────────────────────

    @Operation(summary = "Get interaction status", description = "Returns whether the authenticated user has liked and/or bookmarked the specified scenario.")
    @UserOperation
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status retrieved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ScenarioInteractionStatusDto.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Scenario not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}/interactions")
    public ScenarioInteractionStatusDto getInteractionStatus(
            @Parameter(description = "ID of the scenario", required = true)
            @PathVariable Long id,
            @Parameter(hidden = true) Authentication auth
    ) {
        return interactionService.getStatus(id, auth.getName());
    }
}
