package org.titiplex.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.titiplex.api.dto.ThumbnailRowDto;
import org.titiplex.api.dto.UpdateThumbnailLayoutRequest;
import org.titiplex.api.dto.UpdateThumbnailTitleRequest;
import org.titiplex.api.dto.UploadResponse;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.model.User;
import org.titiplex.service.ScenarioHistoryService;
import org.titiplex.service.ScenarioService;
import org.titiplex.service.ThumbnailService;
import org.titiplex.service.UserService;
import org.titiplex.service.storage.MediaContent;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("SameParameterValue")
@ExtendWith(MockitoExtension.class)
class ThumbnailApiControllerTest {

    @Mock
    private ThumbnailService thumbnailService;

    @Mock
    private UserService userService;

    @Mock
    private ScenarioService scenarioService;

    @Mock
    private ScenarioHistoryService scenarioHistoryService;

    @InjectMocks
    private ThumbnailApiController controller;

    @Test
    void list_checksScenarioVisibilityAndMapsThumbnailsToRows() {
        Authentication auth = auth("alice", "ROLE_USER");

        Scenario scenario = new Scenario();
        scenario.setId(9L);

        Thumbnail t1 = new Thumbnail();
        t1.setId(1L);
        t1.setTitle("Scene 1");
        t1.setIdx(1);
        t1.setGridColumn(1);
        t1.setGridRow(1);
        t1.setGridColumnSpan(1);
        t1.setGridRowSpan(1);
        t1.setImageWidth(800);
        t1.setImageHeight(600);

        Thumbnail t2 = new Thumbnail();
        t2.setId(2L);
        t2.setTitle("Scene 2");
        t2.setIdx(2);
        t2.setGridColumn(2);
        t2.setGridRow(1);
        t2.setGridColumnSpan(2);
        t2.setGridRowSpan(1);
        t2.setImageWidth(1280);
        t2.setImageHeight(720);

        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        when(thumbnailService.listByScenarioId(9L)).thenReturn(List.of(t1, t2));

        List<ThumbnailRowDto> result = controller.list(9L, auth);

        verify(scenarioService).assertCanViewScenario(scenario, auth);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("Scene 1", result.get(0).title());
        assertEquals(2, result.get(1).idx());
        assertEquals(1280, result.get(1).imageWidth());
    }

    @Test
    void upload_usesAuthenticatedUserAndScenario() throws Exception {
        Authentication auth = auth("alice", "ROLE_USER");

        User user = new User();
        user.setId(12L);
        user.setUsername("alice");

        Scenario scenario = new Scenario();
        scenario.setId(9L);

        Thumbnail saved = new Thumbnail();
        saved.setId(55L);

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "thumb.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        when(userService.getUserByUsername("alice")).thenReturn(user);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        when(thumbnailService.save("Intro", image, scenario, user)).thenReturn(saved);

        UploadResponse response = controller.upload(9L, "Intro", image, auth);

        verify(scenarioService).assertCanEditScenario(scenario, auth);
        assertEquals(55L, response.id());
        verify(thumbnailService).save("Intro", image, scenario, user);
    }

    @Test
    void content_buildsResponseEntityWithHeaders() {
        Resource resource = new ByteArrayResource(new byte[]{1, 2, 3, 4});
        MediaContent media = new MediaContent(resource, "image/png", 4L, "\"etag-1\"");

        when(thumbnailService.loadContent(8L)).thenReturn(media);

        ResponseEntity<Resource> response = controller.content(8L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("image/png", Objects.requireNonNull(response.getHeaders().getContentType()).toString());
        assertEquals(4L, response.getHeaders().getContentLength());
        assertEquals("\"etag-1\"", response.getHeaders().getETag());
        assertEquals("public, max-age=3600", response.getHeaders().getFirst("Cache-Control"));
        assertEquals(resource, response.getBody());
    }

    @Test
    void updateLayout_checksEditAccessAndMapsResponse() {
        Authentication auth = auth("alice", "ROLE_USER");

        Thumbnail existing = new Thumbnail();
        existing.setId(8L);
        existing.setScenarioId(9L);

        Scenario scenario = new Scenario();
        scenario.setId(9L);

        Thumbnail saved = new Thumbnail();
        saved.setId(8L);
        saved.setTitle("Scene 1");
        saved.setIdx(1);
        saved.setGridColumn(2);
        saved.setGridRow(3);
        saved.setGridColumnSpan(2);
        saved.setGridRowSpan(1);
        saved.setImageWidth(640);
        saved.setImageHeight(480);

        UpdateThumbnailLayoutRequest request = new UpdateThumbnailLayoutRequest(2, 3, 2, 1);

        User user = new User();
        user.setId(12L);
        user.setUsername("alice");

        when(thumbnailService.getThumbnailById(8L)).thenReturn(existing);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        when(thumbnailService.updateLayout(8L, request)).thenReturn(saved);
        when(userService.getUserByUsername("alice")).thenReturn(user);

        ThumbnailRowDto result = controller.updateLayout(8L, request, auth);

        verify(scenarioService).assertCanEditScenario(scenario, auth);
        assertEquals(8L, result.id());
        assertEquals(2, result.gridColumn());
        assertEquals(3, result.gridRow());
        assertEquals(2, result.gridColumnSpan());
        assertEquals(1, result.gridRowSpan());
    }

    @Test
    void updateTitle_checksEditAccessAndRecordsHistory() {
        Authentication auth = auth("alice", "ROLE_USER");
        Thumbnail existing = new Thumbnail();
        existing.setId(8L);
        existing.setScenarioId(9L);
        Scenario scenario = new Scenario();
        scenario.setId(9L);
        Thumbnail saved = new Thumbnail();
        saved.setId(8L);
        saved.setTitle("Arrival");
        saved.setIdx(1);
        User user = new User();
        user.setId(12L);
        user.setUsername("alice");
        UpdateThumbnailTitleRequest request = new UpdateThumbnailTitleRequest("Arrival");

        when(thumbnailService.getThumbnailById(8L)).thenReturn(existing);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        when(thumbnailService.updateTitle(8L, "Arrival")).thenReturn(saved);
        when(userService.getUserByUsername("alice")).thenReturn(user);

        ThumbnailRowDto result = controller.updateTitle(8L, request, auth);

        verify(scenarioService).assertCanEditScenario(scenario, auth);
        verify(scenarioHistoryService).record(9L, 12L, org.titiplex.persistence.model.ScenarioHistoryAction.THUMBNAIL_UPDATED, "Renamed a thumbnail");
        assertEquals("Arrival", result.title());
    }

    @Test
    void delete_removesThumbnailAndRecordsHistory() {
        Authentication auth = auth("alice", "ROLE_USER");

        Thumbnail existing = new Thumbnail();
        existing.setId(8L);
        existing.setScenarioId(9L);

        User user = new User();
        user.setId(12L);
        user.setUsername("alice");

        when(thumbnailService.getThumbnailById(8L)).thenReturn(existing);
        when(userService.getUserByUsername("alice")).thenReturn(user);

        controller.delete(8L, auth);

        verify(thumbnailService).delete(8L);
        verify(scenarioHistoryService).record(9L, 12L, org.titiplex.persistence.model.ScenarioHistoryAction.THUMBNAIL_DELETED, "Deleted a thumbnail");
    }

    private Authentication auth(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "N/A",
                Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
    }
}
