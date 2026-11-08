package org.titiplex.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.titiplex.config.GlottologProperties;
import org.titiplex.config.SecurityConfig;
import org.titiplex.service.GlottologAdminService;
import org.titiplex.service.GlottologUpdateJobService;
import org.titiplex.service.GlottologUpdateService;
import org.titiplex.service.LanguageImportService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.UserService;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin access is enforced by {@code @AdminOperation} (Spring method security), which only
 * applies when the controller is called through Spring. These tests therefore go through
 * MockMvc instead of calling the controller directly.
 */
@WebMvcTest(AdminApiController.class)
@Import(SecurityConfig.class)
class AdminApiControllerWebMvcTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private ScenarioService scenarioService;
    @MockitoBean
    private LanguageImportService languageImportService;
    @MockitoBean
    private GlottologUpdateService glottologUpdateService;
    @MockitoBean
    private GlottologUpdateJobService glottologUpdateJobService;
    @MockitoBean
    private GlottologAdminService glottologAdminService;
    @MockitoBean
    private GlottologProperties glottologProperties;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/overview", "/api/admin/users", "/api/admin/scenarios", "/api/admin/glottolog/preview"})
    void rejectsNonAdmin(String url) throws Exception {
        mvc.perform(get(url).with(user("bob").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/overview", "/api/admin/users", "/api/admin/scenarios", "/api/admin/glottolog/preview"})
    void rejectsAnonymous(String url) throws Exception {
        mvc.perform(get(url))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/overview", "/api/admin/users", "/api/admin/scenarios", "/api/admin/glottolog/preview"})
    void allowsAdmin(String url) throws Exception {
        mvc.perform(get(url).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    // csrf() is added so that the 403 really comes from the admin check, not from a missing CSRF token.
    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/glottolog/sync", "/api/admin/glottolog/update"})
    void postActionsRejectNonAdmin(String url) throws Exception {
        mvc.perform(post(url).with(csrf()).with(user("bob").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/glottolog/sync", "/api/admin/glottolog/update"})
    void postActionsRejectAnonymous(String url) throws Exception {
        mvc.perform(post(url).with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
