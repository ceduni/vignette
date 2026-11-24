package org.titiplex.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.titiplex.config.SecurityConfig;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.service.VignetteService;

import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VignetteApiController.class)
@Import(SecurityConfig.class)
class VignetteApiControllerWebMvcTest {

    private static final String DRAWING = "{\"people\":[],\"nextId\":1}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VignetteService vignettes;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final MockMultipartFile image = new MockMultipartFile("image", "v.svg", "image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"700\" height=\"500\"/>".getBytes());

    private static Thumbnail vignette() {
        Thumbnail t = new Thumbnail();
        t.setId(42L);
        t.setScenarioId(9L);
        t.setTitle("Market");
        t.setIdx(3);
        t.setVignetteSceneJson(DRAWING);
        return t;
    }

    @Test
    void create_requiresAuthentication() throws Exception {
        mvc.perform(multipart("/api/scenarios/9/vignettes").file(image).param("sceneJson", DRAWING).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_returnsCreatedVignette() throws Exception {
        when(vignettes.create(eq(9L), eq("Market"), any(), eq(DRAWING), any())).thenReturn(vignette());

        mvc.perform(multipart("/api/scenarios/9/vignettes")
                        .file(image)
                        .param("title", "Market")
                        .param("sceneJson", DRAWING)
                        .with(user("alice"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.thumbnailId").value(42))
                .andExpect(jsonPath("$.scenarioId").value(9))
                .andExpect(jsonPath("$.sceneJson").value(DRAWING));
    }

    @Test
    void create_badRequest_whenDrawingIsInvalid() throws Exception {
        when(vignettes.create(eq(9L), any(), any(), eq("nope"), any()))
                .thenThrow(new IllegalArgumentException("Scene data is not valid JSON"));

        mvc.perform(multipart("/api/scenarios/9/vignettes")
                        .file(image)
                        .param("sceneJson", "nope")
                        .with(user("alice"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_isPublic() throws Exception {
        when(vignettes.get(eq(42L), any())).thenReturn(vignette());

        mvc.perform(get("/api/thumbnails/42/vignette"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Market"))
                .andExpect(jsonPath("$.sceneJson").value(DRAWING));
    }

    @Test
    void get_notFound_forPlainImage() throws Exception {
        when(vignettes.get(eq(8L), any())).thenThrow(new NoSuchElementException("not a vignette"));

        mvc.perform(get("/api/thumbnails/8/vignette"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_replacesVignette() throws Exception {
        when(vignettes.update(eq(42L), any(), eq(DRAWING), any())).thenReturn(vignette());

        mvc.perform(multipart("/api/thumbnails/42/vignette")
                        .file(image)
                        .param("sceneJson", DRAWING)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user("alice"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thumbnailId").value(42));
    }

    @Test
    void update_requiresAuthentication() throws Exception {
        mvc.perform(multipart("/api/thumbnails/42/vignette")
                        .file(image)
                        .param("sceneJson", DRAWING)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
