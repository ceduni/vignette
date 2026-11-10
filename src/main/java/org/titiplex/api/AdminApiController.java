package org.titiplex.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.titiplex.api.docs.BadRequestResponse;
import org.titiplex.api.docs.ForbiddenResponse;
import org.titiplex.api.dto.*;
import org.titiplex.api.security.AdminOperation;
import org.titiplex.persistence.model.Role;
import org.titiplex.service.GlottologAdminService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.UserService;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Global administration endpoints.")
public class AdminApiController {

    private final UserService users;
    private final ScenarioService scenarios;
    private final GlottologAdminService glottologAdminService;

    public AdminApiController(
            UserService users,
            ScenarioService scenarios,
            GlottologAdminService glottologAdminService
    ) {
        this.users = users;
        this.scenarios = scenarios;
        this.glottologAdminService = glottologAdminService;
    }

    @Operation(
            summary = "Get admin overview",
            description = "Returns global counters for the administration dashboard."
    )
    @AdminOperation
    @ForbiddenResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Overview retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AdminOverviewDto.class))
            )
    })
    @GetMapping("/overview")
    public AdminOverviewDto overview(Authentication auth) {

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
    @ForbiddenResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Users retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AdminUserRowDto.class))
                    )
            )
    })
    @GetMapping("/users")
    public List<AdminUserRowDto> listUsers(Authentication auth) {

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
    @ForbiddenResponse
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
    @GetMapping("/scenarios")
    public List<ScenarioDto> listScenarios(Authentication auth) {

        return scenarios.listAllScenarios().stream()
                .map(scenarios::toDto)
                .toList();
    }

    @Operation(
            summary = "Update user roles",
            description = "Replaces the roles of a given user. Admin only."
    )
    @AdminOperation
    @BadRequestResponse
    @ForbiddenResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User roles updated successfully",
                    content = @Content(schema = @Schema(implementation = AdminUserRowDto.class))
            )
    })
    @PatchMapping("/users/{id}/roles")
    public AdminUserRowDto updateUserRoles(
            @PathVariable Long id,
            @RequestBody UpdateUserRolesRequest req,
            Authentication auth
    ) {

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
    @BadRequestResponse
    @ForbiddenResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Scenario visibility updated successfully",
                    content = @Content(schema = @Schema(implementation = ScenarioDto.class))
            )
    })
    @PatchMapping("/scenarios/{id}/visibility")
    public ScenarioDto updateScenarioVisibility(
            @PathVariable Long id,
            @RequestBody UpdateScenarioVisibilityRequest req,
            Authentication auth
    ) {
        return scenarios.toDto(scenarios.adminUpdateVisibility(id, req.visibilityStatus()));
    }

    @Operation(
            summary = "Glottolog catalogue summary",
            description = "Returns the number of languages currently in the database (imported by the Python worker)."
    )
    @AdminOperation
    @ForbiddenResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Preview generated successfully",
                    content = @Content(schema = @Schema(implementation = GlottologImportPreviewDto.class))
            )
    })
    @GetMapping("/glottolog/preview")
    public GlottologImportPreviewDto previewGlottolog(Authentication auth) {
        return glottologAdminService.preview();
    }

    @Operation(
            summary = "Deposit a manual Glottolog update request",
            description = "Creates a traceable PENDING request for the external Python worker."
    )
    @AdminOperation
    @PostMapping("/glottolog/update/request")
    public GlottologUpdateRequestDto requestGlottologUpdate(Authentication auth) {
        return glottologAdminService.createManualUpdateRequest(auth.getName());
    }

    @Operation(
            summary = "List recent Glottolog update requests",
            description = "Returns the latest manual update requests and their statuses."
    )
    @AdminOperation
    @GetMapping("/glottolog/update/requests")
    public List<GlottologUpdateRequestDto> listGlottologUpdateRequests(Authentication auth) {
        return glottologAdminService.listUpdateRequests();
    }

    @Operation(
            summary = "Get Glottolog admin settings",
            description = "Returns configurable Glottolog admin settings and available scheduling options."
    )
    @AdminOperation
    @GetMapping("/glottolog/settings")
    public GlottologAdminSettingsDto glottologSettings(Authentication auth) {
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
        return glottologAdminService.updateSettings(request, auth.getName());
    }

    @Operation(
            summary = "Get Glottolog update history",
            description = "Returns the latest Glottolog import runs for the admin dashboard."
    )
    @AdminOperation
    @GetMapping("/glottolog/history")
    public List<GlottologUpdateHistoryRowDto> glottologHistory(Authentication auth) {
        return glottologAdminService.listHistory();
    }

    @Operation(
            summary = "Get Glottolog email notifications",
            description = "Returns recent admin email notifications sent (or archived) after Glottolog updates."
    )
    @AdminOperation
    @GetMapping("/glottolog/notifications")
    public List<GlottologNotificationRowDto> glottologNotifications(Authentication auth) {
        return glottologAdminService.listNotifications();
    }
}
