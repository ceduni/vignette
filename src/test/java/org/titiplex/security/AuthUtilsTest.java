package org.titiplex.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthUtilsTest {

    private static Authentication user(String name, String... roles) {
        return new UsernamePasswordAuthenticationToken(
                name, "n/a", java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }

    private static Authentication anonymous() {
        return new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
    }

    @Test
    void isAuthenticated_onlyForRealUsers() {
        assertTrue(AuthUtils.isAuthenticated(user("alice", "ROLE_USER")));
        assertFalse(AuthUtils.isAuthenticated(null));
        assertFalse(AuthUtils.isAuthenticated(anonymous()));
        // Token built without authorities is "not authenticated" by Spring
        assertFalse(AuthUtils.isAuthenticated(new UsernamePasswordAuthenticationToken("alice", "n/a")));
    }

    @Test
    void requireAuthenticated_throwsForAnonymousAndNull() {
        assertThrows(InsufficientAuthenticationException.class, () -> AuthUtils.requireAuthenticated(null));
        assertThrows(InsufficientAuthenticationException.class, () -> AuthUtils.requireAuthenticated(anonymous()));
        assertDoesNotThrow(() -> AuthUtils.requireAuthenticated(user("alice", "ROLE_USER")));
    }

    @Test
    void authenticatedUsername_isNullForAnonymous() {
        assertEquals("alice", AuthUtils.authenticatedUsername(user("alice", "ROLE_USER")));
        assertNull(AuthUtils.authenticatedUsername(anonymous()));
        assertNull(AuthUtils.authenticatedUsername(null));
    }

    @Test
    void isAdmin_requiresAdminRole() {
        assertTrue(AuthUtils.isAdmin(user("root", "ROLE_USER", "ROLE_ADMIN")));
        assertFalse(AuthUtils.isAdmin(user("alice", "ROLE_USER")));
        assertFalse(AuthUtils.isAdmin(anonymous()));
        assertFalse(AuthUtils.isAdmin(null));
    }
}
