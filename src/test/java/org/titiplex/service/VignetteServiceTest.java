package org.titiplex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioHistoryAction;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.model.User;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VignetteServiceTest {

    private static final String DRAWING = "{\"people\":[],\"nextId\":1}";

    @Mock
    private ThumbnailService thumbnailService;
    @Mock
    private ScenarioService scenarioService;
    @Mock
    private ScenarioAccessService access;
    @Mock
    private ScenarioHistoryService history;
    @Mock
    private UserService userService;

    private VignetteService service;

    private final Authentication auth = new UsernamePasswordAuthenticationToken("alice", "N/A", List.of());
    private final MockMultipartFile image = new MockMultipartFile("image", "v.svg", "image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"700\" height=\"500\"/>".getBytes());
    private Scenario scenario;
    private Thumbnail thumbnail;
    private User alice;

    @BeforeEach
    void setUp() {
        service = new VignetteService(thumbnailService, scenarioService, access, history, userService, new ObjectMapper());
        scenario = new Scenario();
        scenario.setId(9L);
        thumbnail = new Thumbnail();
        thumbnail.setId(8L);
        thumbnail.setScenarioId(9L);
        thumbnail.setTitle("Market");
        alice = new User();
        alice.setId(3L);
        alice.setUsername("alice");
    }

    @Test
    void create_savesImageWithDrawing_andRecordsHistory() throws Exception {
        when(userService.getUserByUsername("alice")).thenReturn(alice);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        when(thumbnailService.save("Market", image, scenario, alice, DRAWING)).thenReturn(thumbnail);

        assertSame(thumbnail, service.create(9L, "Market", image, DRAWING, auth));

        verify(access).assertCanEditScenario(scenario, auth);
        verify(history).record(9L, 3L, ScenarioHistoryAction.THUMBNAIL_ADDED, "Added vignette \"Market\"");
    }

    @Test
    void create_refusedWhenUserCannotEdit() throws Exception {
        when(userService.getUserByUsername("alice")).thenReturn(alice);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        doThrow(new AccessDeniedException("no")).when(access).assertCanEditScenario(scenario, auth);

        assertThrows(AccessDeniedException.class, () -> service.create(9L, "", image, DRAWING, auth));
        verify(thumbnailService, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    void create_requiresImage() {
        MockMultipartFile empty = new MockMultipartFile("image", "v.svg", "image/svg+xml", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> service.create(9L, "", empty, DRAWING, auth));
        verifyNoInteractions(thumbnailService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "image/jpeg", "image/gif", "application/xml", "text/html"})
    void create_refusesOtherImageTypes(String contentType) {
        MockMultipartFile other = new MockMultipartFile("image", "v.bin", contentType, new byte[]{1, 2, 3});

        assertThrows(IllegalArgumentException.class, () -> service.create(9L, "", other, DRAWING, auth));
        verifyNoInteractions(thumbnailService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not json", "[1,2]", "42", "\"text\""})
    void create_requiresJsonObjectDrawing(String sceneJson) {
        assertThrows(IllegalArgumentException.class, () -> service.create(9L, "", image, sceneJson, auth));
        verifyNoInteractions(thumbnailService);
    }

    @Test
    void create_refusesHugeDrawing() {
        String huge = "{\"x\":\"" + "a".repeat(VignetteService.SCENE_JSON_MAX_LENGTH) + "\"}";

        assertThrows(IllegalArgumentException.class, () -> service.create(9L, "", image, huge, auth));
    }

    @Test
    void get_returnsVignette_whenViewable() {
        thumbnail.setVignetteSceneJson(DRAWING);
        when(thumbnailService.getThumbnailById(8L)).thenReturn(thumbnail);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);

        assertSame(thumbnail, service.get(8L, auth));
        verify(access).assertCanViewScenario(scenario, auth);
    }

    @Test
    void get_notFound_forPlainUploadedImage() {
        when(thumbnailService.getThumbnailById(8L)).thenReturn(thumbnail);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);

        assertThrows(NoSuchElementException.class, () -> service.get(8L, auth));
    }

    @Test
    void update_replacesImageAndDrawing() throws Exception {
        thumbnail.setVignetteSceneJson("{}");
        when(thumbnailService.getThumbnailById(8L)).thenReturn(thumbnail);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        when(userService.getUserByUsername("alice")).thenReturn(alice);
        when(thumbnailService.replaceVignette(8L, image, DRAWING)).thenReturn(thumbnail);

        assertSame(thumbnail, service.update(8L, image, DRAWING, auth));

        verify(access).assertCanEditScenario(scenario, auth);
        verify(history).record(9L, 3L, ScenarioHistoryAction.THUMBNAIL_UPDATED, "Edited vignette \"Market\"");
    }

    @Test
    void update_refusedForPlainUploadedImage() throws Exception {
        when(thumbnailService.getThumbnailById(8L)).thenReturn(thumbnail);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);

        assertThrows(IllegalArgumentException.class, () -> service.update(8L, image, DRAWING, auth));
        verify(thumbnailService, never()).replaceVignette(any(), any(), anyString());
    }

    @Test
    void update_refusedWhenUserCannotEdit() throws Exception {
        thumbnail.setVignetteSceneJson("{}");
        when(thumbnailService.getThumbnailById(8L)).thenReturn(thumbnail);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        doThrow(new AccessDeniedException("no")).when(access).assertCanEditScenario(scenario, auth);

        assertThrows(AccessDeniedException.class, () -> service.update(8L, image, DRAWING, auth));
        verify(thumbnailService, never()).replaceVignette(eq(8L), any(), any());
    }
}
