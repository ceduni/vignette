package org.titiplex.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.titiplex.config.SecurityConfig;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.model.User;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.ThumbnailService;
import org.titiplex.service.UserService;
import org.titiplex.service.storage.MediaContent;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ThumbnailApiController.class)
@Import({SecurityConfig.class, ThumbnailApiControllerWebMvcTest.TestBeans.class})
class ThumbnailApiControllerWebMvcTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ThumbnailService thumbnailService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private ScenarioService scenarioService;

    @MockitoBean
    private org.titiplex.service.ScenarioHistoryService scenarioHistoryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @TestConfiguration
    static class TestBeans {
        @Bean("scenarioSecurity")
        ScenarioSecurityStub scenarioSecurity() {
            return new ScenarioSecurityStub();
        }
    }

    static class ScenarioSecurityStub {
        public boolean isOwner(Long scenarioId, String username) {
            return true;
        }

        public boolean isOwnerByThumbnailId(Long thumbnailId, String username) {
            return true;
        }

        public boolean isOwnerByAudioId(Long audioId, String username, Object audioService) {
            return true;
        }
    }

    @Test
    void list_isPublic() throws Exception {
        Thumbnail t1 = new Thumbnail();
        t1.setId(1L);
        t1.setTitle("Scene 1");
        t1.setIdx(1);

        Thumbnail t2 = new Thumbnail();
        t2.setId(2L);
        t2.setTitle("Scene 2");
        t2.setIdx(2);

        when(thumbnailService.listByScenarioId(9L)).thenReturn(List.of(t1, t2));

        mvc.perform(get("/api/scenarios/9/thumbnails"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Scene 1"))
                .andExpect(jsonPath("$[0].idx").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void content_checksScenarioVisibilityAndReturnsHeaders() throws Exception {
        Resource resource = new ByteArrayResource(new byte[]{1, 2, 3, 4});
        MediaContent media = new MediaContent(resource, "image/png", 4L, "\"thumb-etag\"");

        Thumbnail thumbnail = new Thumbnail();
        thumbnail.setScenarioId(9L);
        when(thumbnailService.getThumbnailById(8L)).thenReturn(thumbnail);
        when(thumbnailService.loadContent(8L)).thenReturn(media);

        mvc.perform(get("/api/thumbnails/8/content"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("ETag", "\"thumb-etag\""))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().longValue("Content-Length", 4L));
    }

    @Test
    void upload_requiresAuthentication() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "thumb.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        mvc.perform(multipart("/api/scenarios/9/thumbnails")
                        .file(image)
                        .param("title", "Intro")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_requiresCsrfForSessionAuth() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "thumb.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        mvc.perform(multipart("/api/scenarios/9/thumbnails")
                        .file(image)
                        .param("title", "Intro")
                        .with(user("alice").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_allowsAdminAndReturnsCreated() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "thumb.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        User user = new User();
        user.setId(12L);
        user.setUsername("alice");

        Scenario scenario = new Scenario();
        scenario.setId(9L);

        Thumbnail saved = new Thumbnail();
        saved.setId(55L);

        when(userService.getUserByUsername("alice")).thenReturn(user);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        when(thumbnailService.save("Intro", image, scenario, user)).thenReturn(saved);

        mvc.perform(multipart("/api/scenarios/9/thumbnails")
                        .file(image)
                        .param("title", "Intro")
                        .with(user("alice").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(55));

        verify(thumbnailService).save("Intro", image, scenario, user);
    }

    @Test
    void delete_requiresAuthentication() throws Exception {
        mvc.perform(delete("/api/thumbnails/8").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_allowsOwnerAndReturnsNoContent() throws Exception {
        Thumbnail existing = new Thumbnail();
        existing.setId(8L);
        existing.setScenarioId(9L);

        User user = new User();
        user.setId(12L);
        user.setUsername("alice");

        when(thumbnailService.getThumbnailById(8L)).thenReturn(existing);
        when(userService.getUserByUsername("alice")).thenReturn(user);

        mvc.perform(delete("/api/thumbnails/8")
                        .with(user("alice").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(thumbnailService).delete(8L);
    }
}