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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.titiplex.api.dto.*;
import org.titiplex.api.security.OwnerOrAdminOperation;
import org.titiplex.api.security.ProtectedResource;
import org.titiplex.api.security.PublicOperation;
import org.titiplex.api.security.UserOperation;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.service.LanguageService;
import org.titiplex.service.ScenarioHistoryService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
@Tag(
        name = "Scenario",
        description = "Endpoints for creating, retrieving, listing, and deleting scenarios."
)
public class ScenarioApiController {

    private final ScenarioService scenarioService;
    private final UserService userService;
    private final LanguageService languageService;
    private final ScenarioHistoryService scenarioHistoryService;

    public ScenarioApiController(
            ScenarioService scenarioService,
            UserService userService,
            LanguageService languageService,
            ScenarioHistoryService scenarioHistoryService
    ) {
        this.scenarioService = scenarioService;
        this.userService = userService;
        this.languageService = languageService;
        this.scenarioHistoryService = scenarioHistoryService;
    }

    /**
     * Creates a new scenario based on the provided details.
     *
     * @param req  the request object {@link CreateScenarioRequest} containing the title, description, and language ID of the scenario
     * @param auth the authentication object representing the currently authenticated user
     * @return a response object {@link CreateScenarioResponse} containing the ID of the newly created scenario
     * @throws IllegalArgumentException if the title or language ID is null, blank, or invalid,
     *                                  or if a scenario with the same title, language, and author already exists
     */
    @Operation(
            summary = "Create a new scenario",
            description = "Creates a new scenario for the authenticated user."
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Scenario created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateScenarioResponse.class),
                            examples = @ExampleObject(
                                    name = "Created Scenario Response",
                                    value = "{\"id\": 42}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input : missing required fields, unknown language ID, or duplicate scenario",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied : USER role required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Language not found with the specified ID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict : scenario with same title, language, and author already exists",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateScenarioResponse create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Scenario creation details including title, description, and language ID",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = CreateScenarioRequest.class)
                    )
            )
            @RequestBody CreateScenarioRequest req,

            @Parameter(hidden = true)
            Authentication auth) {
        if (req.title() == null || req.title().isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (req.languageId() == null || req.languageId().isBlank()) {
            throw new IllegalArgumentException("Language is required");
        }
        if (!languageService.existsById(req.languageId())) {
            throw new IllegalArgumentException("Unknown language id");
        }

        var username = auth.getName();
        Long userId = userService.getUserByUsername(username).getId();

        String title = req.title().trim();
        if (scenarioService.existsByTitleAndAuthorNameAndLanguageId(title, username, req.languageId())) {
            throw new IllegalArgumentException("Scenario already exists for this user and language");
        }

        Long id = scenarioService.createScenario(
                title,
                req.description(),
                userId,
                req.languageId(),
                req.tags()
        ).getId();
        return new CreateScenarioResponse(id);
    }

    /**
     * Retrieves a specific scenario based on the provided ID.
     *
     * @param id ({@link Long}) the unique identifier of the scenario to retrieve
     * @return a {@link ScenarioDto} representing the scenario matching the provided ID
     */
    @Operation(
            summary = "Get a scenario by ID",
            description = "Retrieves detailed information about a specific scenario including its thumbnails and metadata."
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Scenario retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScenarioDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scenario not found with the specified ID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/{id}")
    public ScenarioDto getOne(
            @Parameter(
                    description = "ID of the scenario to retrieve",
                    required = true
            )
            @PathVariable Long id,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        Scenario s = scenarioService.getVisibleScenario(id, auth);
        String viewerUsername = (auth != null && auth.isAuthenticated()) ? auth.getName() : null;

        return scenarioService.toDto(s, viewerUsername);
    }

    /**
     * Retrieves the chronological change history of a scenario.
     *
     * @param id ({@link Long}) the unique identifier of the scenario
     * @return a {@link List} of {@link ScenarioHistoryEntryDto}, most recent first
     */
    @Operation(
            summary = "Get a scenario's change history",
            description = "Returns the chronological list of edits made to a scenario (metadata, storyboard, thumbnails, audio, publish events), most recent first. Visible to anyone who can view the scenario."
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "History retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ScenarioHistoryEntryDto.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scenario not found with the specified ID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/{id}/history")
    public List<ScenarioHistoryEntryDto> history(
            @Parameter(
                    description = "ID of the scenario to retrieve the history for",
                    required = true
            )
            @PathVariable Long id,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        scenarioService.getVisibleScenario(id, auth);

        return scenarioHistoryService.list(id).stream()
                .map(e -> new ScenarioHistoryEntryDto(
                        e.getId(),
                        e.getActorUsername(),
                        e.getAction().name(),
                        e.getSummary(),
                        e.getCreatedAt()
                ))
                .toList();
    }

    /**
     * Retrieves a list of all scenarios and converts them into DTOs.
     *
     * @return a list of {@link ScenarioDto} objects representing all scenarios,
     * ordered by creation date in descending order
     */
    @Operation(
            summary = "List all scenarios",
            description = "Retrieves a list of all scenarios in the system, ordered by creation date (most recent first)."
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Scenarios retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ScenarioDto.class))
                    )
            )
    })
    @GetMapping
    public List<ScenarioDto> listAll(
            @Parameter(hidden = true)
            Authentication auth
    ) {
        return scenarioService.listVisibleScenarioDtos(auth);
    }

    @Operation(
            summary = "List published scenarios by language family",
            description = """
                    Returns up to 15 published scenarios whose language belongs to the given family.
                    Results are ordered by most recently created first.
                    """
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Scenarios retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ScenarioDto.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Language family not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/by-family/{familyId}")
    public List<ScenarioDto> listByFamily(
            @Parameter(description = "Glottolog ID of the language family", required = true, example = "indo1319")
            @PathVariable String familyId,

            @Parameter(description = "Maximum number of scenarios to return (1-15, default 15)")
            @RequestParam(required = false) @Min(1) @Max(15) Integer limit
    ) {
        return scenarioService.listPublishedScenariosByFamilyId(familyId, limit);
    }

    @Operation(
            summary = "List published scenarios by country",
            description = """
                    Returns up to 15 published scenarios for languages associated with the given ISO_A3 country code.
                    Results are ordered by most recently created first.
                    """
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Scenarios retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ScenarioDto.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid country code",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/by-country/{isoA3}")
    public List<ScenarioDto> listByCountry(
            @Parameter(description = "ISO 3166-1 alpha-3 country code", required = true, example = "CAN")
            @PathVariable String isoA3,

            @Parameter(description = "Maximum number of scenarios to return (1-15, default 15)")
            @RequestParam(required = false) @Min(1) @Max(15) Integer limit
    ) {
        return scenarioService.listPublishedScenariosByCountryIso(isoA3, limit);
    }

    @Operation(
            summary = "Update storyboard settings",
            description = """
                    Updates storyboard display settings for a scenario.
                    
                    Editable fields include:
                    - layoutMode
                    - preset
                    - columns
                    
                    Requires scenario ownership or admin privileges.
                    """
    )
    @OwnerOrAdminOperation(
            resource = ProtectedResource.SCENARIO
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Storyboard settings updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScenarioDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid storyboard settings",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User is not allowed to edit this scenario",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scenario not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PatchMapping("/{id}/storyboard")
    public ScenarioDto updateStoryboard(
            @Parameter(
                    description = "ID of the scenario to update",
                    required = true,
                    example = "12"
            )
            @PathVariable Long id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Storyboard settings to update",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = UpdateScenarioStoryboardRequest.class),
                            examples = @ExampleObject(
                                    name = "Storyboard update",
                                    value = """
                                            {
                                              "layoutMode": "PRESET",
                                              "preset": "GRID_3",
                                              "columns": 3
                                            }
                                            """
                            )
                    )
            )
            @RequestBody UpdateScenarioStoryboardRequest req,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        return scenarioService.toDto(scenarioService.updateStoryboard(id, req, auth));
    }

    @Operation(
            summary = "Publish a scenario",
            description = """
                    Publishes a scenario and makes it visible to public users.
                    
                    If the scenario was never published before, the publication timestamp is set.
                    A fork pending or rejected review cannot be published.
                    Only the original author of the scenario can publish it (not editor collaborators).
                    """
    )
    @OwnerOrAdminOperation(
            resource = ProtectedResource.SCENARIO_AUTHOR_ONLY
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Scenario published successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScenarioDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User is not the original author, or fork review is pending/rejected",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scenario not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PostMapping("/{id}/publish")
    public ScenarioDto publish(
            @Parameter(
                    description = "ID of the scenario to publish",
                    required = true,
                    example = "12"
            )
            @PathVariable Long id,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        return scenarioService.toDto(scenarioService.publishScenario(id, auth));
    }

    @Operation(
            summary = "Approve a pending fork",
            description = """
                    Approves a fork that is pending review, allowing it to be published.

                    Only the original author of the forked scenario can approve it.
                    """
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Fork approved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScenarioDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Only the original author can approve this fork",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scenario not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Scenario is not a fork pending review",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PostMapping("/{id}/review/approve")
    public ScenarioDto approveFork(
            @Parameter(description = "ID of the fork to approve", required = true)
            @PathVariable Long id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Optional review comment",
                    required = false,
                    content = @Content(schema = @Schema(implementation = ReviewRequest.class))
            )
            @RequestBody(required = false) ReviewRequest body,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        String comment = body != null ? body.comment() : null;
        return scenarioService.toDto(scenarioService.reviewFork(id, true, comment, auth));
    }

    @Operation(
            summary = "Reject a pending fork",
            description = """
                    Rejects a fork that is pending review, permanently preventing it from being published.

                    Only the original author of the forked scenario can reject it.
                    """
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Fork rejected successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScenarioDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Only the original author can reject this fork",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scenario not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Scenario is not a fork pending review",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PostMapping("/{id}/review/reject")
    public ScenarioDto rejectFork(
            @Parameter(description = "ID of the fork to reject", required = true)
            @PathVariable Long id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Optional review comment explaining the rejection",
                    required = false,
                    content = @Content(schema = @Schema(implementation = ReviewRequest.class))
            )
            @RequestBody(required = false) ReviewRequest body,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        String comment = body != null ? body.comment() : null;
        return scenarioService.toDto(scenarioService.reviewFork(id, false, comment, auth));
    }

    @Schema(name = "ReviewRequest", description = "Optional comment attached to a fork review decision")
    public record ReviewRequest(String comment) {}

    @Operation(
            summary = "List my scenarios",
            description = "Returns all scenarios authored by the current authenticated user, including drafts."
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User scenarios retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ScenarioDto.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/mine")
    public List<ScenarioDto> listMine(
            @Parameter(hidden = true)
            Authentication auth
    ) {
        return scenarioService.listMyScenarioDtos(auth);
    }

    @Operation(
            summary = "List scenarios I collaborate on",
            description = "Returns all scenarios where the current authenticated user is an accepted collaborator (not the author)."
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Shared scenarios retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ScenarioDto.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/shared-with-me")
    public List<ScenarioDto> listSharedWithMe(
            @Parameter(hidden = true)
            Authentication auth
    ) {
        return scenarioService.listSharedWithMeScenarioDtos(auth);
    }

    @Operation(
            summary = "List published scenarios a user has worked on",
            description = "Returns all published scenarios where the given user is the author or an accepted collaborator. Drafts are always excluded."
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Scenarios retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ScenarioDto.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/by-author/{username}")
    public List<ScenarioDto> listPublishedScenariosWorkedOnByUsername(
            @Parameter(description = "Username to look up", required = true)
            @PathVariable String username
    ) {
        return scenarioService.listPublishedScenariosWorkedOnByUsername(username);
    }

    @Operation(
            summary = "Update scenario metadata",
            description = """
                    Updates editable scenario metadata such as title and description.
                    
                    Requires scenario ownership or admin privileges.
                    """
    )
    @OwnerOrAdminOperation(
            resource = ProtectedResource.SCENARIO
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Scenario metadata updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScenarioDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid metadata payload",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User is not allowed to edit this scenario",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scenario not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PatchMapping("/{id}/metadata")
    public ScenarioDto updateMetadata(
            @PathVariable Long id,
            @RequestBody UpdateScenarioMetadataRequest req,
            @Parameter(hidden = true)
            Authentication auth
    ) {
        return scenarioService.toDto(scenarioService.updateScenarioMetadata(id, req, auth));
    }

    /**
     * Deletes a scenario based on the provided ID. The operation is authorized
     * for users with the 'ADMIN' role or for the owner of the specified scenario.
     *
     * @param id ({@link Long}) the identification of the scenario to delete
     */
    @Operation(
            summary = "Delete a scenario",
            description = "Deletes a scenario and all its associated data (thumbnails, audios, etc.)."
    )
    @OwnerOrAdminOperation(
            resource = ProtectedResource.SCENARIO
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Scenario deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden : user is not the owner or admin",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scenario not found with the specified ID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(
                    description = "ID of the scenario to delete",
                    required = true
            )
            @PathVariable Long id,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        scenarioService.deleteScenario(id, auth);
    }

    @Operation(
            summary = "Fork a scenario",
            description = "Creates a copy of a published scenario for the authenticated user."
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Fork created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateScenarioResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Scenario is not published",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scenario not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PostMapping("/{id}/fork")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateScenarioResponse fork(
            @Parameter(description = "ID of the scenario to fork", required = true)
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Optional fork details, such as a custom title for the copy.",
                    required = false,
                    content = @Content(
                            schema = @Schema(implementation = ForkScenarioRequest.class)
                    )
            )
            @RequestBody(required = false) ForkScenarioRequest req,
            @Parameter(hidden = true)
            Authentication auth
    ) {
        String requestedTitle = req != null ? req.title() : null;
        Long forkId = scenarioService.forkScenario(id, requestedTitle, auth).getId();
        return new CreateScenarioResponse(forkId);
    }
}
