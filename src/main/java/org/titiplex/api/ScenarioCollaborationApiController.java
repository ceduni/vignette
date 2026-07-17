package org.titiplex.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.titiplex.api.dto.*;
import org.titiplex.api.security.AuthenticatedOperation;
import org.titiplex.persistence.model.CollaboratorRole;
import org.titiplex.persistence.model.User;
import org.titiplex.service.ScenarioCollaborationService;
import org.titiplex.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Collaboration", description = "Endpoints for scenario team collaboration.")
public class ScenarioCollaborationApiController {

    private final ScenarioCollaborationService collaborationService;
    private final UserService userService;

    public ScenarioCollaborationApiController(ScenarioCollaborationService collaborationService, UserService userService) {
        this.collaborationService = collaborationService;
        this.userService = userService;
    }

    // ── User search (for the invite picker) ────────────────────────────

    @Operation(summary = "Search users by username", description = "Used by the collaborator invite picker.")
    @AuthenticatedOperation
    @GetMapping("/users/search")
    public List<UserSearchResultDto> searchUsers(
            @RequestParam("q") String query,
            @Parameter(hidden = true) Authentication auth
    ) {
        return userService.searchByUsername(query, auth.getName()).stream()
                .map(u -> new UserSearchResultDto(u.getId(), u.getUsername(), u.getDisplayName()))
                .toList();
    }

    // ── Collaborators ────────────────────────────────────────────────

    @Operation(summary = "List collaborators of a scenario")
    @AuthenticatedOperation
    @GetMapping("/scenarios/{scenarioId}/collaborators")
    public List<ScenarioCollaboratorDto> listCollaborators(@PathVariable Long scenarioId) {
        return collaborationService.listCollaborators(scenarioId).stream()
                .map(collaborationService::toDto)
                .toList();
    }

    @Operation(summary = "List my pending collaboration invitations")
    @AuthenticatedOperation
    @GetMapping("/collaborators/invitations")
    public List<ScenarioCollaboratorDto> listMyInvitations(@Parameter(hidden = true) Authentication auth) {
        return collaborationService.listPendingInvitationsForUser(auth).stream()
                .map(collaborationService::toDto)
                .toList();
    }

    @Operation(summary = "Invite a user to collaborate on a scenario by username")
    @AuthenticatedOperation
    @PostMapping("/scenarios/{scenarioId}/collaborators/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioCollaboratorDto invite(
            @PathVariable Long scenarioId,
            @RequestBody InviteCollaboratorRequest request,
            @Parameter(hidden = true) Authentication auth
    ) {
        if (request.username() == null || request.username().isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        CollaboratorRole role = parseRole(request.role());
        var collaborator = collaborationService.inviteByUsername(scenarioId, request.username().trim(), role, auth);
        return collaborationService.toDto(collaborator);
    }

    @Operation(summary = "Accept a collaboration invitation")
    @AuthenticatedOperation
    @PostMapping("/collaborators/{collaboratorId}/accept")
    public ScenarioCollaboratorDto accept(@PathVariable Long collaboratorId, @Parameter(hidden = true) Authentication auth) {
        return collaborationService.toDto(collaborationService.respondToInvitation(collaboratorId, true, auth));
    }

    @Operation(summary = "Decline a collaboration invitation")
    @AuthenticatedOperation
    @PostMapping("/collaborators/{collaboratorId}/decline")
    public ScenarioCollaboratorDto decline(@PathVariable Long collaboratorId, @Parameter(hidden = true) Authentication auth) {
        return collaborationService.toDto(collaborationService.respondToInvitation(collaboratorId, false, auth));
    }

    @Operation(summary = "Remove a collaborator from a scenario")
    @AuthenticatedOperation
    @DeleteMapping("/scenarios/{scenarioId}/collaborators/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long scenarioId, @PathVariable Long userId, @Parameter(hidden = true) Authentication auth) {
        collaborationService.removeCollaborator(scenarioId, userId, auth);
    }

    @Operation(summary = "Change a collaborator's role")
    @AuthenticatedOperation
    @PatchMapping("/scenarios/{scenarioId}/collaborators/{userId}/role")
    public ScenarioCollaboratorDto changeRole(
            @PathVariable Long scenarioId,
            @PathVariable Long userId,
            @RequestBody ChangeCollaboratorRoleRequest request,
            @Parameter(hidden = true) Authentication auth
    ) {
        CollaboratorRole role = parseRole(request.role());
        return collaborationService.toDto(collaborationService.changeRole(scenarioId, userId, role, auth));
    }

    // ── Invite links ─────────────────────────────────────────────────

    @Operation(summary = "List invite links for a scenario")
    @AuthenticatedOperation
    @GetMapping("/scenarios/{scenarioId}/invite-links")
    public List<ScenarioInviteLinkDto> listInviteLinks(@PathVariable Long scenarioId, @Parameter(hidden = true) Authentication auth) {
        return collaborationService.listInviteLinks(scenarioId, auth).stream()
                .map(collaborationService::toDto)
                .toList();
    }

    @Operation(summary = "Create a shareable invite link for a scenario")
    @AuthenticatedOperation
    @PostMapping("/scenarios/{scenarioId}/invite-links")
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioInviteLinkDto createInviteLink(
            @PathVariable Long scenarioId,
            @RequestBody CreateInviteLinkRequest request,
            @Parameter(hidden = true) Authentication auth
    ) {
        CollaboratorRole role = parseRole(request.role());
        var link = collaborationService.createInviteLink(scenarioId, role, request.expiresAt(), request.maxUses(), auth);
        return collaborationService.toDto(link);
    }

    @Operation(summary = "Revoke an invite link")
    @AuthenticatedOperation
    @DeleteMapping("/invite-links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInviteLink(@PathVariable Long linkId, @Parameter(hidden = true) Authentication auth) {
        collaborationService.revokeInviteLink(linkId, auth);
    }

    @Operation(summary = "Join a scenario as a collaborator via an invite link token")
    @AuthenticatedOperation
    @PostMapping("/invite-links/{token}/join")
    public ScenarioCollaboratorDto joinViaLink(@PathVariable String token, @Parameter(hidden = true) Authentication auth) {
        return collaborationService.toDto(collaborationService.joinViaLink(token, auth));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private CollaboratorRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }
        try {
            return CollaboratorRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + role);
        }
    }
}
