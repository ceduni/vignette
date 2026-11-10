package org.titiplex.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.titiplex.api.docs.BadRequestResponse;
import org.titiplex.api.docs.ConflictResponse;
import org.titiplex.api.docs.NotFoundResponse;
import org.titiplex.api.docs.UnauthorizedResponse;
import org.titiplex.security.AuthUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.titiplex.api.dto.*;
import org.titiplex.api.security.AuthenticatedOperation;
import org.titiplex.api.security.PublicOperation;
import org.titiplex.persistence.model.User;
import org.titiplex.service.UserService;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Endpoints for user authentication, identity and registration.")
public class AuthApiController {

    private final AuthenticationManager authManager;
    private final JwtEncoder jwtEncoder;
    private final UserService users;
    private static final long ACCESS_TOKEN_EXPIRES_IN_SECONDS = 60 * 60; // 1h

    public AuthApiController(AuthenticationManager authManager, JwtEncoder jwtEncoder, UserService users) {
        this.authManager = authManager;
        this.jwtEncoder = jwtEncoder;
        this.users = users;
    }

    private Authentication requireAuthenticated(Authentication auth) {
        if (!AuthUtils.isAuthenticated(auth)) {
            throw new UnauthenticatedException();
        }
        return auth;
    }

    private LoginResponse issueToken(Authentication auth) {
        Instant now = Instant.now();

        var roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ACCESS_TOKEN_EXPIRES_IN_SECONDS))
                .subject(auth.getName())
                .claim("roles", roles)
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new LoginResponse(token, ACCESS_TOKEN_EXPIRES_IN_SECONDS);
    }

    /**
     * Authenticates a user based on credentials provided in the request body.
     * If authentication is successful, generates a JWT token for the user along with session handling.
     *
     * @param req     the {@link LoginRequest} containing the username and password
     * @param request the HTTP servlet request, used for managing session and security context
     * @return a {@link LoginResponse} containing the generated JWT token and its expiration time
     */
    @Operation(
            summary = "Authenticate a user.",
            description = """
                    Authenticates a user with username and password.
                    
                    Returns a JWT token for authenticated API access.
                    This login flow also establishes a server-side authenticated session.
                    """
    )
    @PublicOperation
    @UnauthorizedResponse
    @BadRequestResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User authenticated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponse.class),
                            examples = @ExampleObject(
                                    name = "Login response",
                                    value = """
                                            {
                                              "token": "eyJhbGciOiJSUzI1NiIs...",
                                              "expiresIn": 3600
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/login")
    public LoginResponse login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Username and password to authenticate",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = LoginRequest.class)
                    )
            )
            @RequestBody LoginRequest req,
            @Parameter(hidden = true)
            HttpServletRequest request) {

        final Authentication auth;
        try {
            auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password())
            );
        } catch (org.springframework.security.core.AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        if (request.getSession() != null) request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );

        return issueToken(auth);
    }

    /**
     * Logs out the currently authenticated user by invalidating their session and clearing the security context.
     *
     * @param request the HTTP servlet request, used to retrieve and invalidate the session
     */
    @Operation(
            summary = "Logout the current user.",
            description = """
                    Logs out the current user.
                    
                    This endpoint invalidates the current server-side session and clears the security context.
                    """
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "User logged out successfully"
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "User logged out successfully"
            )
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Parameter(hidden = true) HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    /**
     * Retrieves information about the currently authenticated user.
     * If the authentication is null, returns null.
     * If the user cannot be found, throws an IllegalStateException.
     *
     * @param auth the authentication object representing the currently authenticated user
     * @return a {@link MeResponse} containing the user's ID, username, and roles
     */
    @Operation(
            summary = "Get the current authenticated user",
            description = "Returns the current authenticated user and granted roles."
    )
    @AuthenticatedOperation
    @UnauthorizedResponse
    @NotFoundResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Information fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MeResponse.class)
                    )
            )
    })
    @GetMapping("/me")
    public MeResponse me(@Parameter(hidden = true) Authentication auth) {
        Authentication authenticated = requireAuthenticated(auth);

        User u = users.getUserByUsername(authenticated.getName());
        if (u == null) throw new IllegalStateException("user not found");

        var roles = authenticated.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return new MeResponse(u.getId(), u.getUsername(), roles);
    }

    /**
     * Registers a new user with the provided details.
     * Ensures that valid data is provided and checks if the username or email is already in use.
     * If the registration is successful, creates a new user and returns the response containing the user's ID and username.
     *
     * @param req the {@link RegisterRequest} containing the username, email, and password
     * @return a {@link RegisterResponse} containing the ID and username of the newly registered user
     * @throws IllegalArgumentException if any input validation fails (e.g., missing or invalid fields, or duplicate username/email)
     */
    @Operation(
            summary = "Registers a new user.",
            description = "Registers a new user and returns the created account identifier."
    )
    @PublicOperation
    @BadRequestResponse
    @ConflictResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Registered successfully.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RegisterResponse.class),
                            examples = @ExampleObject(
                                    name = "Register response",
                                    value = """
                                            {
                                              "id": 12,
                                              "username": "alice"
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User registration details",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = RegisterRequest.class)
                    )
            )
            @RequestBody RegisterRequest req
    ) {
        if (req.password() == null || req.password().length() < 8)
            throw new IllegalArgumentException("password too short");

        // Normalization, required fields and uniqueness checks live in UserService.register.
        User u = users.register(req.username(), req.email(), req.password());
        return new RegisterResponse(u.getId(), u.getUsername());
    }

    @Operation(
            summary = "Refresh the current access token",
            description = """
                    Issues a new JWT access token for the current authenticated session.
                    
                    This endpoint is meant to be called with the existing server-side session
                    cookie, not with an Authorization bearer token.
                    """
    )
    @PublicOperation
    @UnauthorizedResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "A new access token was issued successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponse.class)
                    )
            )
    })
    @PostMapping("/refresh")
    public LoginResponse refresh(@Parameter(hidden = true) Authentication auth) {
        Authentication authenticated = requireAuthenticated(auth);

        User u = users.getUserByUsername(authenticated.getName());
        if (u == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        return issueToken(authenticated);
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class UnauthenticatedException extends RuntimeException {
    }
}
