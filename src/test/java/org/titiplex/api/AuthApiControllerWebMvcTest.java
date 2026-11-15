package org.titiplex.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.titiplex.config.SecurityConfig;
import org.titiplex.persistence.model.User;
import org.titiplex.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthApiController.class)
@Import(SecurityConfig.class)
class AuthApiControllerWebMvcTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthenticationManager authManager;

    @MockitoBean
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserService users;

    @Test
    void login_returnsTokenJson() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "alice",
                "N/A",
                createAuthorityList("ROLE_USER")
        );

        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);

        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtEncoder.encode(any())).thenReturn(jwt);
        when(jwt.getTokenValue()).thenReturn("jwt-token");

        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600));
    }

    @Test
    void register_returnsCreatedResponse() throws Exception {
        User created = new User();
        created.setId(12L);
        created.setUsername("alice");

        when(users.register("alice", "alice@example.com", "password123")).thenReturn(created);

        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void me_returnsUnauthorizedWhenAnonymous() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsCurrentUserWhenAuthenticated() throws Exception {
        User userEntity = new User();
        userEntity.setId(7L);
        userEntity.setUsername("alice");

        when(users.getUserByUsername("alice")).thenReturn(userEntity);

        mvc.perform(get("/api/auth/me")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }

    @Test
    void refresh_returnsUnauthorizedWhenAnonymous() throws Exception {
        mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_returnsNewTokenWhenAuthenticated() throws Exception {
        User userEntity = new User();
        userEntity.setId(7L);
        userEntity.setUsername("alice");

        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);

        when(users.getUserByUsername("alice")).thenReturn(userEntity);
        when(jwtEncoder.encode(any())).thenReturn(jwt);
        when(jwt.getTokenValue()).thenReturn("refreshed-token");

        mvc.perform(post("/api/auth/refresh")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("refreshed-token"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600));
    }
}