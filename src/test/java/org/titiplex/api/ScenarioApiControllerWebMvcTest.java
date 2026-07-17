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
                null,
                "NONE",
                null,
                null,
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
                null,
                "NONE",
                null,
                null,
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

    @Test
    void approveFork_requiresAuthentication() throws Exception {
        mvc.perform(post("/api/scenarios/30/review/approve")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approveFork_requiresCsrfForSessionAuth() throws Exception {
        mvc.perform(post("/api/scenarios/30/review/approve")
                        .with(user("alice").roles("USER"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveFork_returnsUpdatedDtoWhenAuthorizedWithCsrf() throws Exception {
        Scenario approved = new Scenario();
        approved.setId(30L);

        ScenarioDto dto = new ScenarioDto(
                30L, "Forked story", "Desc", "chuj", "bob",
                Instant.parse("2026-03-20T10:15:30Z"),
                "DRAFT", null, "PRESET", "GRID_3", 3,
                List.of(),
                21L,
                "APPROVED", "alice", Instant.parse("2026-03-25T10:00:00Z"), null
        );

        when(scenarioService.reviewFork(eq(30L), eq(true), any(), any())).thenReturn(approved);
        when(scenarioService.toDto(approved)).thenReturn(dto);

        mvc.perform(post("/api/scenarios/30/review/approve")
                        .with(user("alice").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedByUsername").value("alice"));
    }

    @Test
    void rejectFork_returnsUpdatedDtoWithComment() throws Exception {
        Scenario rejected = new Scenario();
        rejected.setId(31L);

        ScenarioDto dto = new ScenarioDto(
                31L, "Forked story", "Desc", "chuj", "bob",
                Instant.parse("2026-03-20T10:15:30Z"),
                "DRAFT", null, "PRESET", "GRID_3", 3,
                List.of(),
                21L,
                "REJECTED", "alice", Instant.parse("2026-03-25T10:00:00Z"), "Not accurate enough"
        );

        when(scenarioService.reviewFork(eq(31L), eq(false), any(), any())).thenReturn(rejected);
        when(scenarioService.toDto(rejected)).thenReturn(dto);

        mvc.perform(post("/api/scenarios/31/review/reject")
                        .with(user("alice").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "comment": "Not accurate enough"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.reviewStatus").value("REJECTED"))
                .andExpect(jsonPath("$.reviewComment").value("Not accurate enough"));
    }
}