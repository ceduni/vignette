package org.titiplex.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.titiplex.api.dto.*;
import org.titiplex.api.security.AdminOperation;
import org.titiplex.config.GlottologProperties;
import org.titiplex.persistence.model.Role;
import org.titiplex.service.GlottologUpdateJobService;
import org.titiplex.service.GlottologAdminService;
import org.titiplex.service.GlottologUpdateService;
import org.titiplex.service.LanguageImportService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.UserService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Global administration endpoints.")
public class AdminApiController {

    private final UserService users;
    private final ScenarioService scenarios;
    private final LanguageImportService languageImportService;
    private final GlottologUpdateService glottologUpdateService;
    private final GlottologUpdateJobService glottologUpdateJobService;
    private final GlottologAdminService glottologAdminService;
    private final GlottologProperties glottologProperties;

    public AdminApiController(
            UserService users,
            ScenarioService scenarios,
            LanguageImportService languageImportService,
            GlottologUpdateService glottologUpdateService,
            GlottologUpdateJobService glottologUpdateJobService,
            GlottologAdminService glottologAdminService,
            GlottologProperties glottologProperties
    ) {
        this.users = users;
        this.scenarios = scenarios;
        this.languageImportService = languageImportService;
        this.glottologUpdateService = glottologUpdateService;
        this.glottologUpdateJobService = glottologUpdateJobService;
        this.glottologAdminService = glottologAdminService;
        this.glottologProperties = glottologProperties;
    }

    @Operation(
            summary = "Get admin overview",
            description = "Returns global counters for the administration dashboard."
    )
    @AdminOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Overview retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AdminOverviewDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin privileges required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/overview")
    public AdminOverviewDto overview(Authentication auth) {
        assertAdmin(auth);

        long userCount = users.countUsers();
        long scenarioCount = scenarios.countAllScenarios();
        long publishedScenarioCount = scenarios.countPublishedScenarios();
        long draftScenarioCount = scenarios.countDraftScenarios();

        return new AdminOverviewDto(
                userCount,
                scenarioCount,
                publishedScenarioCount,
                draftScenarioCount
        );
    }

    @Operation(
            summary = "List users for administration",
            description = "Returns all users for administration purposes."
    )
    @AdminOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Users retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AdminUserRowDto.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin privileges required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/users")
    public List<AdminUserRowDto> listUsers(Authentication auth) {
        assertAdmin(auth);

        return users.listAllUsers().stream()
                .map(user -> new AdminUserRowDto(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()),
                        user.isProfilePublic()
                ))
                .toList();
    }

    @Operation(
            summary = "List scenarios for administration",
            description = "Returns all scenarios for administration purposes, including drafts."
    )
    @AdminOperation
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
                    responseCode = "403",
                    description = "Admin privileges required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/scenarios")
    public List<ScenarioDto> listScenarios(Authentication auth) {
        assertAdmin(auth);

        return scenarios.listAllScenarios().stream()
                .map(scenarios::toDto)
                .toList();
    }

    @Operation(
            summary = "Update user roles",
            description = "Replaces the roles of a given user. Admin only."
    )
    @AdminOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User roles updated successfully",
                    content = @Content(schema = @Schema(implementation = AdminUserRowDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin privileges required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PatchMapping("/users/{id}/roles")
    public AdminUserRowDto updateUserRoles(
            @PathVariable Long id,
            @RequestBody UpdateUserRolesRequest req,
            Authentication auth
    ) {
        assertAdmin(auth);

        var user = users.updateRoles(id, req.roles());

        return new AdminUserRowDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()),
                user.isProfilePublic()
        );
    }

    @Operation(
            summary = "Update scenario visibility",
            description = "Updates scenario visibility for administration purposes. Admin only."
    )
    @AdminOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Scenario visibility updated successfully",
                    content = @Content(schema = @Schema(implementation = ScenarioDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin privileges required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PatchMapping("/scenarios/{id}/visibility")
    public ScenarioDto updateScenarioVisibility(
            @PathVariable Long id,
            @RequestBody UpdateScenarioVisibilityRequest req,
            Authentication auth
    ) {
        assertAdmin(auth);
        return scenarios.toDto(scenarios.adminUpdateVisibility(id, req.visibilityStatus()));
    }

    @Operation(
            summary = "Preview Glottolog import",
            description = "Returns row counts for the bundled Glottolog CSV without modifying the database."
    )
    @AdminOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Preview generated successfully",
                    content = @Content(schema = @Schema(implementation = GlottologImportPreviewDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin privileges required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/glottolog/preview")
    public GlottologImportPreviewDto previewGlottolog(Authentication auth) throws IOException {
        assertAdmin(auth);
        return glottologUpdateService.preview();
    }

    @Operation(
            summary = "Synchronize languages from Glottolog",
            description = """
                    Imports or updates languages from the bundled Glottolog CSV.
                    Keeps languages and dialects with countries, plus ancestor families for grouping.
                    """
    )
    @AdminOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Synchronization completed successfully",
                    content = @Content(schema = @Schema(implementation = GlottologSyncResultDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin privileges required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PostMapping("/glottolog/sync")
    public GlottologSyncResultDto syncGlottolog(Authentication auth) throws IOException {
        assertAdmin(auth);
        if (!glottologProperties.isLegacyPipelineEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "Legacy CSV sync is disabled. Deposit a manual update request for the Python worker."
            );
        }
        Path activeCsv = glottologUpdateService.resolveActiveCsvPath();
        if (activeCsv != null) {
            return languageImportService.syncFromCsv(activeCsv);
        }
        return languageImportService.syncFromClasspath();
    }

    @Operation(
            summary = "Download Glottolog and synchronize languages",
            description = """
                    Runs the Glottolog download script (Zenodo CLDF), writes a filtered CSV,
                    then imports or updates languages in the database.
                    This operation can take several minutes.
                    """
    )
    @AdminOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Download and synchronization completed successfully",
                    content = @Content(schema = @Schema(implementation = GlottologUpdateResultDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin privileges required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Download script failed or Python is unavailable",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PostMapping("/glottolog/update")
    public Object updateGlottolog(Authentication auth) throws IOException {
        assertAdmin(auth);
        if (!glottologProperties.isLegacyPipelineEnabled()) {
            return glottologAdminService.createManualUpdateRequest(auth.getName());
        }
        return glottologAdminService.runImmediateUpdate(auth.getName());
    }

    @Operation(
            summary = "Start a Glottolog update",
            description = """
                    When the legacy Java pipeline is disabled (default), deposits a manual update request
                    for the external Python worker. When legacy mode is enabled, starts the old Java job.
                    """
    )
    @AdminOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Request deposited or legacy job started"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin privileges required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PostMapping("/glottolog/update/start")
    public Object startGlottologUpdateJob(Authentication auth) {
        assertAdmin(auth);
        if (!glottologProperties.isLegacyPipelineEnabled()) {
            return glottologAdminService.createManualUpdateRequest(auth.getName());
        }
        return glottologUpdateJobService.startJob(auth.getName());
    }

    @Operation(
            summary = "Deposit a manual Glottolog update request",
            description = "Creates a traceable PENDING request for the external Python worker."
    )
    @AdminOperation
    @PostMapping("/glottolog/update/request")
    public GlottologUpdateRequestDto requestGlottologUpdate(Authentication auth) {
        assertAdmin(auth);
        return glottologAdminService.createManualUpdateRequest(auth.getName());
    }

    @Operation(
            summary = "List recent Glottolog update requests",
            description = "Returns the latest manual update requests and their statuses."
    )
    @AdminOperation
    @GetMapping("/glottolog/update/requests")
    public List<GlottologUpdateRequestDto> listGlottologUpdateRequests(Authentication auth) {
        assertAdmin(auth);
        return glottologAdminService.listUpdateRequests();
    }

    @Operation(
            summary = "Get Glottolog pipeline / job status",
            description = """
                    Returns Python-owned pipeline state when the legacy Java pipeline is disabled.
                    Otherwise returns the legacy in-memory Java job status.
                    """
    )
    @AdminOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Status returned successfully"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin privileges required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/glottolog/update/status")
    public Object glottologUpdateStatus(Authentication auth) {
        assertAdmin(auth);
        if (!glottologProperties.isLegacyPipelineEnabled()) {
            return glottologAdminService.getSettings();
        }
        return glottologUpdateJobService.currentStatus();
    }

    @Operation(
            summary = "Get Glottolog admin settings",
            description = "Returns configurable Glottolog admin settings and available scheduling options."
    )
    @AdminOperation
    @GetMapping("/glottolog/settings")
    public GlottologAdminSettingsDto glottologSettings(Authentication auth) {
        assertAdmin(auth);
        return glottologAdminService.getSettings();
    }

    @Operation(
            summary = "Update Glottolog admin settings",
            description = "Updates configurable Glottolog admin settings for the import workflow."
    )
    @AdminOperation
    @PutMapping("/glottolog/settings")
    public GlottologAdminSettingsDto updateGlottologSettings(
            @RequestBody UpdateGlottologAdminSettingsRequest request,
            Authentication auth
    ) {
        assertAdmin(auth);
        return glottologAdminService.updateSettings(request, auth.getName());
    }

    @Operation(
            summary = "Get Glottolog update history",
            description = "Returns the latest Glottolog import runs for the admin dashboard."
    )
    @AdminOperation
    @GetMapping("/glottolog/history")
    public List<GlottologUpdateHistoryRowDto> glottologHistory(Authentication auth) {
        assertAdmin(auth);
        return glottologAdminService.listHistory();
    }

    @Operation(
            summary = "Get Glottolog email notifications",
            description = "Returns recent admin email notifications sent (or archived) after Glottolog updates."
    )
    @AdminOperation
    @GetMapping("/glottolog/notifications")
    public List<GlottologNotificationRowDto> glottologNotifications(Authentication auth) {
        assertAdmin(auth);
        return glottologAdminService.listNotifications();
    }

    private void assertAdmin(Authentication auth) {
        boolean isAdmin = auth != null
                && auth.getAuthorities() != null
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        if (!isAdmin) {
            throw new AccessDeniedException("Admin privileges required");
        }
    }
}
