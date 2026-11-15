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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.titiplex.api.docs.BadRequestResponse;
import org.titiplex.api.docs.NotFoundResponse;
import org.titiplex.api.docs.UnauthorizedResponse;
import org.titiplex.api.dto.PublicUserProfileResponse;
import org.titiplex.api.dto.UpdateUserProfileRequest;
import org.titiplex.api.dto.UserProfileResponse;
import org.titiplex.api.security.AuthenticatedOperation;
import org.titiplex.api.security.PublicOperation;
import org.titiplex.persistence.model.Role;
import org.titiplex.persistence.model.User;
import org.titiplex.service.UserService;

import java.security.Principal;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@Tag(
        name = "User",
        description = "Endpoints for managing user profiles, including viewing and updating private profiles, and accessing public profiles."
)
public class UserApiController {

    private final UserService users;

    public UserApiController(UserService users) {
        this.users = users;
    }

    /**
     * Retrieves the private profile of the currently authenticated user.
     *
     * @param principal the security context {@link Principal} providing details about the currently authenticated user
     * @return the private user profile information encapsulated in a {@link UserProfileResponse}
     * @throws IllegalStateException if the authenticated user cannot be found in the system
     */
    @Operation(
            summary = "Get my profile",
            description = "Returns the complete private profile of the current authenticated user."
    )
    @AuthenticatedOperation
    @UnauthorizedResponse
    @NotFoundResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Profile retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserProfileResponse.class),
                            examples = @ExampleObject(
                                    name = "User Profile Example",
                                    value = """
                                            {
                                              "id": 1,
                                              "username": "john_doe",
                                              "email": "john@example.com",
                                              "displayName": "John Doe",
                                              "bio": "Linguist and researcher",
                                              "institution": "University Example",
                                              "researchInterests": "Phonetics, Syntax",
                                              "profilePublic": true,
                                              "roles": ["ROLE_USER"],
                                              "academyAffiliations": "Academy of Sciences"
                                            }
                                            """
                            )
                    )
            )
    })
    @GetMapping("/me/profile")
    public UserProfileResponse myProfile(
            @Parameter(hidden = true)
            Principal principal
    ) {
        User user = users.getUserByUsername(principal.getName());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found");
        }
        return toPrivateProfile(user);
    }

    /**
     * Updates the profile information of the currently authenticated user.
     *
     * @param request   the details of the user's updated profile, including display name, bio,
     *                  institution, research interests, profile visibility, and academy affiliations; via {@link UpdateUserProfileRequest}.
     * @param principal the security context {@link Principal} providing details about the currently authenticated user
     * @return the updated private user profile information encapsulated in a {@link UserProfileResponse}
     */
    @Operation(
            summary = "Update my profile",
            description = "Updates the private profile of the current authenticated user."
    )
    @AuthenticatedOperation
    @BadRequestResponse
    @UnauthorizedResponse
    @NotFoundResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Profile updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserProfileResponse.class)
                    )
            )
    })
    @PutMapping("/me/profile")
    public UserProfileResponse updateMyProfile(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Profile update details including display name, bio, institution, research interests, visibility, and affiliations",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = UpdateUserProfileRequest.class),
                            examples = @ExampleObject(
                                    name = "Profile Update Example",
                                    value = """
                                            {
                                              "displayName": "Dr. John Doe",
                                              "bio": "Senior linguist specializing in phonetics",
                                              "institution": "MIT Linguistics Department",
                                              "researchInterests": "Phonetics, Phonology, Syntax",
                                              "profilePublic": true,
                                              "academyAffiliations": "International Phonetic Association"
                                            }
                                            """
                            )
                    )
            )
            @RequestBody UpdateUserProfileRequest request,

            @Parameter(hidden = true)
            Principal principal) {
        User user = users.getUserByUsername(principal.getName());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found");
        }

        User updated = users.updateProfile(
                user,
                request.displayName(),
                request.bio(),
                request.institution(),
                request.researchInterests(),
                request.profilePublic(),
                request.academyAffiliations()
        );

        return toPrivateProfile(updated);
    }

    /**
     * Retrieves the public profile of a user specified by their unique identifier.
     * <p>
     * The method first checks if the user exists and if their profile is marked as public.
     * If the user does not exist or their profile is not public, it throws a {@link UserProfileNotFoundException}.
     * Otherwise, it converts the user entity into a response object representing the public profile.
     *
     * @param id ({@link Long}) the unique identifier of the user whose public profile needs to be retrieved
     * @return the public profile information encapsulated in a {@link PublicUserProfileResponse} object
     * @throws UserProfileNotFoundException if the user does not exist or their profile is not public
     */
    @Operation(
            summary = "Get public user profile",
            description = "Returns the public profile of a user."
    )
    @PublicOperation
    @NotFoundResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Public profile retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PublicUserProfileResponse.class),
                            examples = @ExampleObject(
                                    name = "Public Profile Example",
                                    value = """
                                            {
                                              "id": 5,
                                              "username": "jane_smith",
                                              "displayName": "Dr. Jane Smith",
                                              "bio": "Syntax researcher",
                                              "institution": "Harvard University",
                                              "researchInterests": "Syntax, Semantics",
                                              "roles": ["ROLE_USER"],
                                              "academyAffiliations": "Linguistic Society of America"
                                            }
                                            """
                            )
                    )
            )
    })
    @GetMapping("/{id}/profile")
    public PublicUserProfileResponse publicProfile(
            @Parameter(description = "ID of the user whose public profile needs to be retrieved", required = true)
            @PathVariable Long id
    ) {
        User user = users.getExistingUserById(id);
        if (user == null || !user.isProfilePublic()) {
            throw new UserProfileNotFoundException();
        }
        return toPublicProfile(user);
    }

    private UserProfileResponse toPrivateProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                user.getInstitution(),
                user.getResearchInterests(),
                user.isProfilePublic(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()),
                user.getAcademyAffiliations()
        );
    }

    private PublicUserProfileResponse toPublicProfile(User user) {
        return new PublicUserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getBio(),
                user.getInstitution(),
                user.getResearchInterests(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()),
                user.getAcademyAffiliations()
        );
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class UserProfileNotFoundException extends RuntimeException {
    }
}
