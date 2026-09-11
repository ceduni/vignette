package org.titiplex.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.titiplex.Application;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.datasource.url=jdbc:h2:mem:vignette-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                "spring.datasource.driverClassName=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "app.storage.root=target/test-storage-it"
        }
)
@AutoConfigureMockMvc
class ApiFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private final Path storageRoot = Path.of("target/test-storage-it");

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
    void fullFlow_register_login_createScenario_uploadThumbnail_uploadAudio_andReadBackPublicly() throws Exception {
        // 1) Register
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
                .andExpect(jsonPath("$.username").value("alice"));

        // 2) Login and keep session
        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-jwt-token"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // 3) Create scenario
        Assertions.assertNotNull(session);
        MvcResult createScenarioResult = mvc.perform(post("/api/scenarios")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "My scenario",
                                  "description": "Integration test scenario",
                                  "languageId": "chuj",
                                  "tags": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        long scenarioId = readId(createScenarioResult);

        // Story locations must persist independently from the language reference point.
        mvc.perform(patch("/api/scenarios/{id}/metadata", scenarioId).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"location":{"name":"Dakar, Senegal","latitude":14.7167,"longitude":-17.4677}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location.name").value("Dakar, Senegal"));
        mvc.perform(get("/api/scenarios/{id}", scenarioId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location.latitude").value(14.7167))
                .andExpect(jsonPath("$.location.longitude").value(-17.4677));
        for (String invalid : new String[]{
                "{\"latitude\":91,\"longitude\":0}",
                "{\"latitude\":0,\"longitude\":181}",
                "{\"latitude\":10}"}) {
            mvc.perform(patch("/api/scenarios/{id}/metadata", scenarioId).session(session).with(csrf())
                            .contentType("application/json").content("{\"location\":" + invalid + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/scenarios/{id}/metadata", scenarioId).session(session).with(csrf())
                        .contentType("application/json").content("{\"location\":{}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.location").isEmpty());

        // 4) Upload thumbnail
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "thumb.png",
                "image/png",
                new byte[]{1, 2, 3, 4, 5}
        );

        MvcResult uploadThumbResult = mvc.perform(multipart("/api/scenarios/{scenarioId}/thumbnails", scenarioId)
                        .file(image)
                        .param("title", "Intro scene")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        long thumbnailId = readId(uploadThumbResult);

        // 5) Upload audio
        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "clip.webm",
                "audio/webm",
                new byte[]{9, 8, 7, 6}
        );

        MvcResult uploadAudioResult = mvc.perform(multipart("/api/thumbnails/{thumbId}/audios", thumbnailId)
                        .file(audio)
                        .param("title", "Greeting")
                        .param("idx", "1")
                        .param("markerX", "10.0")
                        .param("markerY", "20.0")
                        .param("markerLabel", "speaker")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        long audioId = readId(uploadAudioResult);

        mvc.perform(patch("/api/audios/{id}/gloss", audioId)
                        .session(session).with(csrf()).contentType("application/json")
                        .content("""
                                {"transcription":"Hola", "gloss":"hello", "freeTranslation":"Good morning"}
                                """))
                .andExpect(status().isNoContent());
        Assertions.assertEquals("Hola", audioRepository.findById(audioId).orElseThrow().getTranscription());

        long backgroundId = readId(mvc.perform(multipart("/api/scenarios/{id}/background-audios", scenarioId)
                        .file(audio).param("title", "Forest").session(session).with(csrf()))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(patch("/api/audios/{id}/ambience", backgroundId)
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"volume\":35,\"loop\":false}"))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/audios/{id}/ambience", backgroundId)
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"volume\":101,\"loop\":true}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                        .content("""
                                {"username":"outsider","email":"outsider@example.test","password":"Test-pass-42","name":"Outside","surname":"Tester"}
                                """))
                .andExpect(status().isCreated());

        // Private metadata and media must share the same visibility boundary.
        for (String path : new String[]{"/api/thumbnails/" + thumbnailId + "/content",
                "/api/audios/" + audioId + "/content", "/api/audios/" + backgroundId + "/content"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
            mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("outsider").roles("USER")))
                    .andExpect(status().isNotFound());
            mvc.perform(get(path).session(session)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "private, no-store"));
        }

        // 6) Publish scenario before public readback
        mvc.perform(post("/api/scenarios/{id}/publish", scenarioId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(scenarioId))
                .andExpect(jsonPath("$.visibilityStatus").value("PUBLISHED"));

        // 7) Public scenario readback
        mvc.perform(get("/api/scenarios/{id}", scenarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(scenarioId))
                .andExpect(jsonPath("$.title").value("My scenario"))
                .andExpect(jsonPath("$.languageId").value("chuj"))
                .andExpect(jsonPath("$.authorUsername").value("alice"))
                .andExpect(jsonPath("$.visibilityStatus").value("PUBLISHED"));

        // 8) Public thumbnail listing
        mvc.perform(get("/api/scenarios/{scenarioId}/thumbnails", scenarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(thumbnailId))
                .andExpect(jsonPath("$[0].title").value("Intro scene"))
                .andExpect(jsonPath("$[0].idx").value(0));

        // 9) Public thumbnail content
        mvc.perform(get("/api/thumbnails/{id}/content", thumbnailId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4, 5}));

        // 10) Public audio listing
        mvc.perform(get("/api/thumbnails/{thumbId}/audios", thumbnailId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(audioId))
                .andExpect(jsonPath("$[0].title").value("Greeting"))
                .andExpect(jsonPath("$[0].idx").value(1))
                .andExpect(jsonPath("$[0].mime").value("audio/webm"))
                .andExpect(jsonPath("$[0].markerX").value(10.0))
                .andExpect(jsonPath("$[0].markerY").value(20.0))
                .andExpect(jsonPath("$[0].markerLabel").value("speaker"));

        mvc.perform(get("/api/thumbnails/{id}/audios", thumbnailId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transcription").value("Hola"))
                .andExpect(jsonPath("$[0].gloss").value("hello"))
                .andExpect(jsonPath("$[0].freeTranslation").value("Good morning"));
        mvc.perform(get("/api/scenarios/{id}/background-audios", scenarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(backgroundId))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].volume").value(35))
                .andExpect(jsonPath("$[0].loop").value(false));
        mvc.perform(patch("/api/audios/{id}/gloss", audioId).with(csrf())
                        .contentType("application/json").content("{\"gloss\":\"unauthorized\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/audios/{id}/gloss", audioId)
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"transcription\":null,\"gloss\":\"\",\"freeTranslation\":null}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/thumbnails/{id}/audios", thumbnailId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].gloss").isEmpty());

        // 11) Public audio content
        mvc.perform(get("/api/audios/{id}/content", audioId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/webm"))
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().bytes(new byte[]{9, 8, 7, 6}));
    }

    @Test
    void profileFlow_login_updateMyProfile_andReadPublicProfile() throws Exception {
        // 1) Register
        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "bob",
                                  "email": "bob@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("bob"));

        Long bobId = userRepository.findByUsername("bob").orElseThrow().getId();

        // 2) Login
        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "bob",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // 3) Update my profile
        Assertions.assertNotNull(session);
        mvc.perform(put("/api/users/me/profile")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "displayName": "Dr Bob",
                                  "bio": "Updated bio",
                                  "institution": "UdeM",
                                  "researchInterests": "Syntax",
                                  "profilePublic": true,
                                  "academyAffiliations": ["Academy A", " Academy B "]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.displayName").value("Dr Bob"))
                .andExpect(jsonPath("$.profilePublic").value(true));

        // 4) Public profile becomes readable
        mvc.perform(get("/api/users/{id}/profile", bobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bobId))
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.displayName").value("Dr Bob"))
                .andExpect(jsonPath("$.institution").value("UdeM"));
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
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