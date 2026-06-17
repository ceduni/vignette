package org.titiplex.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.titiplex.api.dto.ScenarioDto;
import org.titiplex.config.SecurityConfig;
import org.titiplex.config.components.ScenarioSecurity;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.User;
import org.titiplex.service.LanguageService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.UserService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScenarioApiController.class)
@Import(SecurityConfig.class)
class ScenarioApiControllerWebMvcTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ScenarioService scenarioService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private LanguageService languageService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean(name = "scenarioSecurity")
    private ScenarioSecurity scenarioSecurity;

    @Test
    void listAll_isPublic() throws Exception {
        User author = new User();
        author.setId(7L);
        author.setUsername("alice");

        Scenario scenario = new Scenario();
        scenario.setId(9L);
        scenario.setTitle("Story");
        scenario.setDescription("Desc");
        scenario.setLanguage_id("chuj");
        scenario.setAuthor_id(7L);
        scenario.setAuthor(author);
        scenario.setCreatedAt(Instant.parse("2026-03-20T10:15:30Z"));

        ScenarioDto dto = new ScenarioDto(
                9L,
                "Story",
                "Desc",
                "chuj",
                "alice",
                Instant.parse("2026-03-20T10:15:30Z"),
                "DRAFT",
                null,
                "PRESET",
                "GRID_3",
                3,
                List.of(),
                null
        );

        when(scenarioService.listVisibleScenarios(any())).thenReturn(List.of(scenario));
        when(scenarioService.toDto(scenario)).thenReturn(dto);

        mvc.perform(get("/api/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9))
                .andExpect(jsonPath("$[0].title").value("Story"))
                .andExpect(jsonPath("$[0].languageId").value("chuj"))
                .andExpect(jsonPath("$[0].authorUsername").value("alice"));
    }

    @Test
    void getOne_isPublic() throws Exception {
        User author = new User();
        author.setId(7L);
        author.setUsername("alice");

        Scenario scenario = new Scenario();
        scenario.setId(11L);
        scenario.setTitle("Scenario 11");
        scenario.setDescription("Some desc");
        scenario.setLanguage_id("chuj");
        scenario.setAuthor_id(7L);
        scenario.setAuthor(author);
        scenario.setCreatedAt(Instant.parse("2026-03-20T10:15:30Z"));

        ScenarioDto dto = new ScenarioDto(
                11L,
                "Scenario 11",
                "Some desc",
                "chuj",
                "alice",
                Instant.parse("2026-03-20T10:15:30Z"),
                "DRAFT",
                null,
                "PRESET",
                "GRID_3",
                3,
                List.of(),
                null
        );

        when(scenarioService.getVisibleScenario(eq(11L), any())).thenReturn(scenario);
        when(scenarioService.toDto(scenario)).thenReturn(dto);

        mvc.perform(get("/api/scenarios/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.title").value("Scenario 11"))
                .andExpect(jsonPath("$.languageId").value("chuj"))
                .andExpect(jsonPath("$.authorUsername").value("alice"));
    }

    @Test
    void create_requiresAuthentication() throws Exception {
        mvc.perform(post("/api/scenarios")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "My scenario",
                                  "description": "This is a test",
                                  "languageId": "chuj"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_requiresCsrfForSessionAuthenticatedRequest() throws Exception {
        mvc.perform(post("/api/scenarios")
                        .with(user("alice").roles("USER"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "My scenario",
                                  "description": "This is a test",
                                  "languageId": "chuj"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_returnsCreatedWhenAuthenticatedWithCsrf() throws Exception {
        User user = new User();
        user.setId(12L);
        user.setUsername("alice");

        Scenario created = new Scenario();
        created.setId(44L);

        when(languageService.existsById("chuj")).thenReturn(true);
        when(userService.getUserByUsername("alice")).thenReturn(user);
        when(scenarioService.existsByTitleAndAuthorNameAndLanguageId("My scenario", "alice", "chuj"))
                .thenReturn(false);
        when(scenarioService.createScenario("My scenario", "This is a test", 12L, "chuj", List.of()))
                .thenReturn(created);

        mvc.perform(post("/api/scenarios")
                        .with(user("alice").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "My scenario",
                                  "description": "This is a test",
                                  "languageId": "chuj"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(44));
    }

    @Test
    void delete_requiresAuthentication() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/scenarios/77")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_requiresCsrfForSessionAuth() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/scenarios/77")
                        .with(user("alice").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_allowsAdminWithCsrf() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/scenarios/77")
                        .with(user("alice").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(scenarioService).deleteScenario(77L);
    }
}