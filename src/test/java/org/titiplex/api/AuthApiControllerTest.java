package org.titiplex.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.titiplex.api.dto.*;
import org.titiplex.persistence.model.User;
import org.titiplex.service.UserService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthApiControllerTest {

    @Mock
    private AuthenticationManager authManager;

    @Mock
    private JwtEncoder jwtEncoder;

    @Mock
    private UserService users;

    @InjectMocks
    private AuthApiController controller;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_authenticatesUserStoresSessionAndReturnsToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        Jwt jwt = mock(Jwt.class);

        Authentication authenticated = new UsernamePasswordAuthenticationToken(
                "alice",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authenticated);
        when(request.getSession()).thenReturn(session);
        when(request.getSession(true)).thenReturn(session);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);
        when(jwt.getTokenValue()).thenReturn("jwt-token");

        LoginResponse response = controller.login(
                new LoginRequest("alice", "secret123"),
                request
        );

        assertEquals("jwt-token", response.accessToken());
        assertEquals(3600L, response.expiresInSeconds());

        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authManager).authenticate(captor.capture());

        assertEquals("alice", captor.getValue().getPrincipal());
        assertEquals("secret123", captor.getValue().getCredentials());

        verify(request).changeSessionId();
        verify(session).setAttribute(
                eq(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY),
                any()
        );

        assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void logout_invalidatesSessionAndClearsSecurityContext() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "N/A")
        );

        when(request.getSession(false)).thenReturn(session);

        controller.logout(request);

        verify(session).invalidate();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void me_returnsCurrentUserAndRoles() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "alice",
                "N/A",
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                )
        );

        User user = new User();
        user.setId(12L);
        user.setUsername("alice");

        when(users.getUserByUsername("alice")).thenReturn(user);

        MeResponse response = controller.me(auth);

        assertEquals(12L, response.id());
        assertEquals("alice", response.username());
        assertEquals(List.of("ROLE_USER", "ROLE_ADMIN"), response.roles());
    }

    @Test
    void me_rejectsUnauthenticatedAccess() {
        Authentication anonymous = new UsernamePasswordAuthenticationToken(
                "anonymousUser",
                "N/A",
                List.of()
        );

        assertThrows(RuntimeException.class, () -> controller.me(anonymous));
    }

    @Test
    void register_delegatesToService() {
        User created = new User();
        created.setId(99L);
        created.setUsername("alice");

        when(users.register("  alice  ", "  alice@example.com  ", "password123")).thenReturn(created);

        RegisterResponse response = controller.register(
                new RegisterRequest("  alice  ", "  alice@example.com  ", "password123")
        );

        assertEquals(99L, response.id());
        assertEquals("alice", response.username());

        verify(users).register("  alice  ", "  alice@example.com  ", "password123");
    }

    @Test
    void refresh_returnsNewTokenForAuthenticatedUser() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "alice",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        User user = new User();
        user.setId(12L);
        user.setUsername("alice");

        Jwt jwt = mock(Jwt.class);

        when(users.getUserByUsername("alice")).thenReturn(user);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);
        when(jwt.getTokenValue()).thenReturn("refreshed-token");

        LoginResponse response = controller.refresh(auth);

        assertEquals("refreshed-token", response.accessToken());
        assertEquals(3600L, response.expiresInSeconds());
    }
}