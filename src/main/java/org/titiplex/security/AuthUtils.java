package org.titiplex.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;

/**
 * Single place for the small authentication checks used across services and controllers.
 * Use with a static import: {@code import static org.titiplex.security.AuthUtils.*;}
 */
public final class AuthUtils {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private AuthUtils() {
    }

    /** True for a real, logged-in user (anonymous visitors are not authenticated). */
    public static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /** Throws 401 (InsufficientAuthenticationException) unless a real user is logged in. */
    public static void requireAuthenticated(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            throw new InsufficientAuthenticationException("Authentication required");
        }
    }

    /** The logged-in username, or {@code null} for anonymous visitors. */
    public static String authenticatedUsername(Authentication authentication) {
        return isAuthenticated(authentication) ? authentication.getName() : null;
    }

    /** True if the logged-in user has the ADMIN role. */
    public static boolean isAdmin(Authentication authentication) {
        return isAuthenticated(authentication)
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    }
}
