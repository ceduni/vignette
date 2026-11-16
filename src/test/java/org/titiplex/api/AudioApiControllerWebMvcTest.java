package org.titiplex.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.titiplex.api.dto.AudioRowDto;
import org.titiplex.config.SecurityConfig;
import org.titiplex.service.AudioService;
import org.titiplex.service.UserService;
import org.titiplex.service.storage.MediaContent;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AudioApiController.class)
@Import(SecurityConfig.class)
class AudioApiControllerWebMvcTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AudioService audioService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private org.titiplex.service.AudioManagementService audioManagement;


    @Test
    void list_isPublic() throws Exception {
        when(audioManagement.listForThumbnail(eq(7L), any())).thenReturn(
                List.of(
                        new AudioRowDto(1L, "Audio 1", 1, "audio/webm", 10.0, 20.0, "speaker", null, null, null),
                        new AudioRowDto(2L, "Audio 2", 2, "audio/webm", null, null, null, null, null, null)
                )
        );
        mvc.perform(get("/api/thumbnails/7/audios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Audio 1"))
                .andExpect(jsonPath("$[0].mime").value("audio/webm"))
                .andExpect(jsonPath("$[0].markerLabel").value("speaker"))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void content_checksScenarioVisibilityAndReturnsHeaders() throws Exception {
        Resource resource = new ByteArrayResource(new byte[]{9, 8, 7});
        MediaContent media = new MediaContent(resource, "audio/webm", 3L, "\"audio-etag\"");

        when(audioManagement.loadContent(eq(4L), any())).thenReturn(media);

        mvc.perform(get("/api/audios/4/content"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/webm"))
                .andExpect(header().string("ETag", "\"audio-etag\""))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().longValue("Content-Length", 3L));
    }

    @Test
    void upload_requiresAuthentication() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "clip.webm",
                "audio/webm",
                new byte[]{1, 2, 3}
        );

        mvc.perform(multipart("/api/thumbnails/9/audios")
                        .file(audio)
                        .param("title", "Greeting")
                        .param("idx", "2")
                        .param("markerX", "10.0")
                        .param("markerY", "20.0")
                        .param("markerLabel", "speaker")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_requiresCsrfForSessionAuth() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "clip.webm",
                "audio/webm",
                new byte[]{1, 2, 3}
        );

        mvc.perform(multipart("/api/thumbnails/9/audios")
                        .file(audio)
                        .param("title", "Greeting")
                        .param("idx", "2")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_returnsCreatedJsonWhenAuthenticatedWithCsrf() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "clip.webm",
                "audio/webm",
                new byte[]{1, 2, 3}
        );

        when(audioManagement.upload(eq(9L), eq("Greeting"), eq(2), any(), eq(10.0), eq(20.0), eq("speaker"), any()))
                .thenReturn(42L);

        mvc.perform(multipart("/api/thumbnails/9/audios")
                        .file(audio)
                        .param("title", "Greeting")
                        .param("idx", "2")
                        .param("markerX", "10.0")
                        .param("markerY", "20.0")
                        .param("markerLabel", "speaker")
                        .with(user("alice").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42));

        verify(audioManagement).upload(eq(9L), eq("Greeting"), eq(2), any(), eq(10.0), eq(20.0), eq("speaker"), any());
    }

    @Test
    void replace_requiresAuthentication() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "replacement.webm",
                "audio/webm",
                new byte[]{4, 5, 6}
        );

        mvc.perform(multipart("/api/audios/5/content")
                        .file(audio)
                        .param("title", "New take")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void replace_callsServiceWhenAuthenticatedWithCsrf() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "replacement.webm",
                "audio/webm",
                new byte[]{4, 5, 6}
        );
        when(audioManagement.replace(eq(5L), eq("New take"), any(), any())).thenReturn(5L);

        mvc.perform(multipart("/api/audios/5/content")
                        .file(audio)
                        .param("title", "New take")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user("alice").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));

        verify(audioManagement).replace(eq(5L), eq("New take"), any(), any());
    }

    @Test
    void updateMarker_requiresAuthentication() throws Exception {
        mvc.perform(patch("/api/audios/5/marker")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "markerX": 11.0,
                                  "markerY": 22.0,
                                  "markerLabel": "target"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMarker_requiresCsrfForSessionAuth() throws Exception {
        mvc.perform(patch("/api/audios/5/marker")
                        .with(user("alice").roles("USER"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "markerX": 11.0,
                                  "markerY": 22.0,
                                  "markerLabel": "target"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMarker_callsServiceWhenAuthenticatedWithCsrf() throws Exception {
        mvc.perform(patch("/api/audios/5/marker")
                        .with(user("alice").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "markerX": 11.0,
                                  "markerY": 22.0,
                                  "markerLabel": "target"
                                }
                                """))
                .andExpect(status().isOk());

        verify(audioManagement).updateMarker(eq(5L), eq(11.0), eq(22.0), eq("target"), any());
    }

    @Test
    void selectBackground_callsServiceWhenAuthenticatedWithCsrf() throws Exception {
        mvc.perform(patch("/api/scenarios/12/background-audio")
                        .with(user("alice").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "audioId": 99
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(audioManagement).selectBackground(eq(12L), eq(99L), any());
    }

    @Test
    void delete_requiresAuthentication() throws Exception {
        mvc.perform(delete("/api/audios/6").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_requiresCsrfForSessionAuth() throws Exception {
        mvc.perform(delete("/api/audios/6")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_callsServiceWhenAuthenticatedWithCsrf() throws Exception {
        mvc.perform(delete("/api/audios/6")
                        .with(user("alice").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(audioManagement).delete(eq(6L), any());
    }
}
