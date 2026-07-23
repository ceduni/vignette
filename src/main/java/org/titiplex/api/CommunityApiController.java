package org.titiplex.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.titiplex.api.dto.*;
import org.titiplex.api.security.PublicOperation;
import org.titiplex.api.security.UserOperation;
import org.titiplex.persistence.model.*;
import org.titiplex.service.CommunityService;
import org.titiplex.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/community")
@Tag(
        name = "Community",
        description = """
                Endpoints for community interactions around languages and media.
                
                This includes:
                - threaded discussions attached to a language or audio clip
                - accreditation requests for community contribution roles
                - accreditation review and direct grant flows
                """
)
public class CommunityApiController {
    private final CommunityService communityService;
    private final UserService userService;

    public CommunityApiController(CommunityService communityService, UserService userService) {
        this.communityService = communityService;
        this.userService = userService;
    }

    @Operation(
            summary = "List discussion messages",
            description = """
                    Returns all messages attached to a specific discussion target.
                    
                    A discussion target is identified by:
                    - targetType: the type of object being discussed
                    - targetId: the identifier of that object
                    
                    Current supported target types include language-level and audio-level discussions.
                    """
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Discussion messages retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DiscussionMessageDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid discussion target type or target id",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Discussion target does not exist",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/discussions")
    public List<DiscussionMessageDto> listDiscussions(
            @Parameter(
                    description = "Type of target being discussed",
                    required = true,
                    example = "LANGUAGE"
            )
            @RequestParam DiscussionTargetType targetType,

            @Parameter(
                    description = "Identifier of the target being discussed",
                    required = true,
                    example = "chuj"
            )
            @RequestParam String targetId
    ) {
        return communityService.listMessages(targetType, targetId).stream().map(this::toDto).toList();
    }

    @Operation(
            summary = "Create a discussion message",
            description = """
                    Creates a new message attached to a discussion target.
                    
                    The message may optionally reply to an existing message by providing parentMessageId.
                    The authenticated user becomes the message author.
                    """
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Discussion message created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DiscussionMessageDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request body, unknown target, invalid parent message, or blank content",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PostMapping("/discussions")
    public DiscussionMessageDto createDiscussion(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = """
                            New discussion message payload.
                            
                            Example:
                            - create a top-level message on a language
                            - or reply to an existing message with parentMessageId
                            """,
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = CreateDiscussionMessageRequest.class),
                            examples = @ExampleObject(
                                    name = "Create discussion message",
                                    value = """
                                            {
                                              "targetType": "LANGUAGE",
                                              "targetId": "chuj",
                                              "parentMessageId": null,
                                              "contributionType": "GLOSS",
                                              "content": "I think this gloss should be revised."
                                            }
                                            """
                            )
                    )
            )
            @RequestBody CreateDiscussionMessageRequest req,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        Long userId = userService.getUserByUsername(auth.getName()).getId();
        DiscussionMessage created = communityService.createMessage(
                userId,
                req.targetType(),
                req.targetId(),
                req.parentMessageId(),
                req.contributionType(),
                req.content()
        );
        return toDto(created);
    }

    @Operation(
            summary = "Create an accreditation request",
            description = """
                    Creates a new accreditation request for the authenticated user.
                    
                    This can be:
                    - a global accreditation request
                    - or a scenario-scoped accreditation request
                    
                    Scenario-scoped requests must include scenarioId.
                    """
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Accreditation request created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccreditationRequestDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid scope, missing scenarioId, blank motivation, or duplicate pending request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PostMapping("/accreditation-requests")
    public AccreditationRequestDto createAccreditationRequest(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Accreditation request payload",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = CreateAccreditationRequestBody.class),
                            examples = @ExampleObject(
                                    name = "Scenario accreditation request",
                                    value = """
                                            {
                                              "permissionType": "SCENARIO_EDIT",
                                              "scopeType": "SCENARIO",
                                              "targetId": "12",
                                              "motivation": "I can help organize and maintain this scenario."
                                            }
                                            """
                            )
                    )
            )
            @RequestBody CreateAccreditationRequestBody req,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        Long userId = userService.getUserByUsername(auth.getName()).getId();
        AccreditationRequest created = communityService.createRequest(
                userId,
                req.permissionType(),
                req.scopeType(),
                req.targetId(),
                req.motivation()
        );
        return toDto(created);
    }

    @Operation(
            summary = "List accreditation requests",
            description = """
                    Lists accreditation requests for a given scope.
                    
                    Access rules:
                    - GLOBAL scope: admin only
                    - SCENARIO scope: admin or scenario owner
                    """
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Accreditation requests retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccreditationRequestDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid scope or missing scenarioId",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not allowed to read accreditation requests for this scope",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/accreditation-requests")
    public List<AccreditationRequestDto> listAccreditationRequests(
            @Parameter(
                    description = "Permission of accreditation request to receive",
                    required = true,
                    example = "LANGUAGE_EDIT"
            )
            @RequestParam AccreditationPermissionType permissionType,

            @Parameter(
                    description = "Scope of accreditation requests to retrieve",
                    required = true,
                    example = "SCENARIO"
            )
            @RequestParam AccreditationScopeType scopeType,

            @Parameter(
                    description = "target identifier for scopeType",
                    example = "12"
            )
            @RequestParam(required = false) String targetId,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        if (!canManage(scopeType, targetId, auth)) {
            throw forbidden("Not allowed to read these accreditation requests");
        }

        return communityService.listRequests(permissionType, scopeType, targetId).stream().map(this::toDto).toList();
    }

    @Operation(
            summary = "Review an accreditation request",
            description = """
                    Approves or rejects an existing accreditation request.
                    
                    Access rules:
                    - GLOBAL scope request: admin only
                    - SCENARIO scope request: admin or scenario owner
                    """
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Accreditation request reviewed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccreditationRequestDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Unknown request or request already reviewed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not allowed to review this accreditation request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PostMapping("/accreditation-requests/{requestId}/review")
    public AccreditationRequestDto reviewAccreditationRequest(
            @Parameter(
                    description = "Identifier of the accreditation request to review",
                    required = true,
                    example = "42"
            )
            @PathVariable Long requestId,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Review decision payload",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = ReviewAccreditationRequestBody.class),
                            examples = @ExampleObject(
                                    name = "Approve request",
                                    value = """
                                            {
                                              "approved": true,
                                              "reviewNote": "Approved for scenario moderation."
                                            }
                                            """
                            )
                    )
            )
            @RequestBody ReviewAccreditationRequestBody req,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        Long reviewerUserId = userService.getUserByUsername(auth.getName()).getId();
        AccreditationRequest request = communityService.getRequest(requestId);

        if (!canManage(request.getScopeType(), request.getTargetId(), auth)) {
            throw forbidden("Not allowed to review this request");
        }

        AccreditationRequest reviewed = communityService.reviewRequest(requestId, reviewerUserId, req.approved(), req.reviewNote());
        return toDto(reviewed);
    }

    @Operation(
            summary = "List granted accreditations",
            description = """
                    Lists granted accreditations for a given scope.
                    
                    Access rules:
                    - GLOBAL scope: admin only
                    - SCENARIO scope: admin or scenario owner
                    """
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Granted accreditations retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CommunityAccreditationDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid scope or missing scenarioId",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not allowed to list accreditations for this scope",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/accreditations")
    public List<CommunityAccreditationDto> listAccreditations(
            @Parameter(
                    description = "Permissions of accreditations to receive",
                    required = true,
                    example = "LANGUAGE_EDIT"
            )
            @RequestParam(required = false) AccreditationPermissionType permissionType,

            @Parameter(
                    description = "Scope of accreditations to retrieve",
                    required = true,
                    example = "SCENARIO"
            )
            @RequestParam AccreditationScopeType scopeType,

            @Parameter(
                    description = "target identifier for scopeType",
                    example = "12"
            )
            @RequestParam(required = false) String targetId,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        if (!canManage(scopeType, targetId, auth)) {
            throw forbidden("Not allowed to list accreditations for this scope");
        }
        return communityService.listAccreditations(permissionType, scopeType, targetId).stream().map(this::toDto).toList();
    }

    @Operation(
            summary = "Grant an accreditation directly",
            description = """
                    Grants an accreditation directly to a user without going through a review request flow.
                    
                    Access rules:
                    - GLOBAL scope: admin only
                    - SCENARIO scope: admin or scenario owner
                    """
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Accreditation granted successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CommunityAccreditationDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid scope, missing scenarioId, or unknown target user",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not allowed to grant accreditation for this scope",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PostMapping("/accreditations")
    public CommunityAccreditationDto grantAccreditation(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Direct accreditation grant payload",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = GrantAccreditationBody.class),
                            examples = @ExampleObject(
                                    name = "Grant scenario accreditation",
                                    value = """
                                            {
                                               "userId": 15,
                                               "permissionType": "SCENARIO_MODERATE",
                                               "scopeType": "SCENARIO",
                                               "targetId": "12",
                                               "note": "Trusted moderator for this scenario."
                                             }
                                            """
                            )
                    )
            )
            @RequestBody GrantAccreditationBody req,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        if (!canManage(req.scopeType(), req.targetId(), auth)) {
            throw forbidden("Not allowed to grant accreditation for this scope");
        }
        Long granterUserId = userService.getUserByUsername(auth.getName()).getId();
        CommunityAccreditation created = communityService.grantAccreditation(
                req.userId(),
                req.permissionType(),
                req.scopeType(),
                req.targetId(),
                granterUserId,
                req.note()
        );
        return toDto(created);
    }

    private boolean canManage(AccreditationScopeType scopeType,
                              String targetId,
                              Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (scopeType == AccreditationScopeType.GLOBAL) {
            return isAdmin;
        }

        if (scopeType == AccreditationScopeType.SCENARIO) {
            if (isAdmin) return true;
            if (targetId == null || targetId.isBlank()) return false;
            try {
                Long scenarioId = Long.parseLong(targetId);
                return communityService.isScenarioOwner(scenarioId, auth.getName());
            } catch (NumberFormatException e) {
                return false;
            }
        }

        if (scopeType == AccreditationScopeType.LANGUAGE || scopeType == AccreditationScopeType.LANGUAGE_FAMILY) {
            return isAdmin;
        }

        return false;
    }

    private boolean canReview(AccreditationScopeType scopeType, Long scenarioId, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (scopeType == AccreditationScopeType.GLOBAL) {
            return isAdmin;
        }
        return isAdmin || (scenarioId != null && communityService.isScenarioOwner(scenarioId, auth.getName()));
    }

    private DiscussionMessageDto toDto(DiscussionMessage message) {
        String authorUsername = message.getAuthor() != null
                ? message.getAuthor().getUsername()
                : userService.getUserById(message.getAuthorId()).getUsername();

        return new DiscussionMessageDto(
                message.getId(),
                message.getTargetType(),
                message.getTargetId(),
                message.getParentMessageId(),
                message.getAuthorId(),
                authorUsername,
                message.getContributionType(),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private AccreditationRequestDto toDto(AccreditationRequest request) {
        return new AccreditationRequestDto(
                request.getId(),
                request.getRequestedByUserId(),
                request.getRequester() == null ? "Unknown" : request.getRequester().getUsername(),
                request.getPermissionType(),
                request.getScopeType(),
                request.getTargetId(),
                request.getMotivation(),
                request.getStatus(),
                request.getReviewedByUserId(),
                request.getReviewNote(),
                request.getCreatedAt(),
                request.getReviewedAt()
        );
    }

    private CommunityAccreditationDto toDto(CommunityAccreditation accreditation) {
        return new CommunityAccreditationDto(
                accreditation.getId(),
                accreditation.getUserId(),
                accreditation.getUser() == null ? "Unknown" : accreditation.getUser().getUsername(),
                accreditation.getPermissionType(),
                accreditation.getScopeType(),
                accreditation.getTargetId(),
                accreditation.getGrantedByUserId(),
                accreditation.getGrantedAt(),
                accreditation.getNote()
        );
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
