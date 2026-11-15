package org.titiplex.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.titiplex.Application;
import org.titiplex.PostgresTestContainerConfig;
import org.titiplex.persistence.model.Language;
import org.titiplex.persistence.repo.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("SameParameterValue")
@SpringBootTest(
        classes = Application.class,
        properties = {
                "app.storage.root=target/test-storage-it-fail"
        }
)
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class ApiFailureFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private ThumbnailRepository thumbnailRepository;

    @Autowired
    private AudioRepository audioRepository;

    @MockitoBean
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final Path storageRoot = Path.of("target/test-storage-it-fail");

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        cleanStorage();

        Jwt jwt = Mockito.mock(Jwt.class);
        when(jwtEncoder.encode(any())).thenReturn(jwt);
        when(jwt.getTokenValue()).thenReturn("test-jwt-token");

        Language language = new Language();
        language.setId("chuj");
        language.setName("Chuj");
        language.setBookkeeping(false);
        language.setLevel("Language");
        language.setChildFamilyCount(0);
        language.setChildLanguageCount(0);
        language.setChildDialectCount(0);
        languageRepository.save(language);
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanDatabase();
        cleanStorage();
    }

    @Test
    void login_failsWithWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createScenario_requiresAuthentication() throws Exception {
        mvc.perform(post("/api/scenarios")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "My scenario",
                                  "description": "Desc",
                                  "languageId": "chuj"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createScenario_rejectsMissingCsrfForSessionAuth() throws Exception {
        MockHttpSession session = registerAndLogin("alice", "alice@example.com", "password123");

        mvc.perform(post("/api/scenarios")
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "My scenario",
                                  "description": "Desc",
                                  "languageId": "chuj"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createScenario_rejectsUnknownLanguage() throws Exception {
        MockHttpSession session = registerAndLogin("alice", "alice@example.com", "password123");

        mvc.perform(post("/api/scenarios")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "My scenario",
                                  "description": "Desc",
                                  "languageId": "unknown-lang"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createScenario_rejectsDuplicateScenarioForSameUserAndLanguage() throws Exception {
        MockHttpSession session = registerAndLogin("alice", "alice@example.com", "password123");

        mvc.perform(post("/api/scenarios")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "My scenario",
                                  "description": "Desc",
                                  "languageId": "chuj"
                                }
                                """))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/scenarios")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "My scenario",
                                  "description": "Another desc",
                                  "languageId": "chuj"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void privateProfile_isNotPubliclyAccessible() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "bob",
                                  "email": "bob@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated());

        Long bobId = userRepository.findByUsername("bob").orElseThrow().getId();

        mvc.perform(get("/api/users/{id}/profile", bobId))
                .andExpect(status().isNotFound());
    }

    @Test
    void thumbnailUpload_rejectsUnsupportedContentType() throws Exception {
        MockHttpSession session = registerAndLogin("alice", "alice@example.com", "password123");
        long scenarioId = createScenario(session, "Scenario image invalid");

        MockMultipartFile badImage = new MockMultipartFile(
                "image",
                "thumb.txt",
                "text/plain",
                "not-an-image".getBytes()
        );

        mvc.perform(multipart("/api/scenarios/{scenarioId}/thumbnails", scenarioId)
                        .file(badImage)
                        .param("title", "Bad thumb")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void audioUpload_rejectsUnsupportedContentType() throws Exception {
        MockHttpSession session = registerAndLogin("alice", "alice@example.com", "password123");
        long scenarioId = createScenario(session, "Scenario audio invalid");
        long thumbnailId = uploadThumbnail(session, scenarioId, "Intro");

        MockMultipartFile badAudio = new MockMultipartFile(
                "audio",
                "clip.txt",
                "text/plain",
                "not-audio".getBytes()
        );

        mvc.perform(multipart("/api/thumbnails/{thumbId}/audios", thumbnailId)
                        .file(badAudio)
                        .param("title", "Bad audio")
                        .param("idx", "1")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void audioUpload_rejectsMarkerWithOnlyOneCoordinate() throws Exception {
        MockHttpSession session = registerAndLogin("alice", "alice@example.com", "password123");
        long scenarioId = createScenario(session, "Scenario marker invalid");
        long thumbnailId = uploadThumbnail(session, scenarioId, "Intro");

        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "clip.webm",
                "audio/webm",
                new byte[]{9, 8, 7, 6}
        );

        mvc.perform(multipart("/api/thumbnails/{thumbId}/audios", thumbnailId)
                        .file(audio)
                        .param("title", "Broken marker")
                        .param("idx", "1")
                        .param("markerX", "10.0")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void audioUpload_rejectsMarkerOutsideRange() throws Exception {
        MockHttpSession session = registerAndLogin("alice", "alice@example.com", "password123");
        long scenarioId = createScenario(session, "Scenario marker out of range");
        long thumbnailId = uploadThumbnail(session, scenarioId, "Intro");

        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "clip.webm",
                "audio/webm",
                new byte[]{9, 8, 7, 6}
        );

        mvc.perform(multipart("/api/thumbnails/{thumbId}/audios", thumbnailId)
                        .file(audio)
                        .param("title", "Bad marker")
                        .param("idx", "1")
                        .param("markerX", "150.0")
                        .param("markerY", "20.0")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_requiresAuthentication() throws Exception {
        mvc.perform(put("/api/users/me/profile")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "displayName": "Dr Bob",
                                  "profilePublic": true
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_requiresCsrfForSessionAuth() throws Exception {
        MockHttpSession session = registerAndLogin("bob", "bob@example.com", "password123");

        mvc.perform(put("/api/users/me/profile")
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                {
                                  "displayName": "Dr Bob",
                                  "profilePublic": true
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession registerAndLogin(String username, String email, String password) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, email, password)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private long createScenario(MockHttpSession session, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/scenarios")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "Desc",
                                  "languageId": "chuj"
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(json)
                .get("id")
                .asLong();
    }

    private long uploadThumbnail(MockHttpSession session, long scenarioId, String title) throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "thumb.png",
                "image/png",
                new byte[]{1, 2, 3, 4, 5}
        );

        MvcResult result = mvc.perform(multipart("/api/scenarios/{scenarioId}/thumbnails", scenarioId)
                        .file(image)
                        .param("title", title)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(json)
                .get("id")
                .asLong();
    }

    private void cleanDatabase() {
        audioRepository.deleteAll();
        thumbnailRepository.deleteAll();
        scenarioRepository.deleteAll();
        userRepository.deleteAll();
        languageRepository.deleteAll();
    }

    private void cleanStorage() throws IOException {
        if (!Files.exists(storageRoot)) {
            return;
        }

        try (var walk = Files.walk(storageRoot)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}