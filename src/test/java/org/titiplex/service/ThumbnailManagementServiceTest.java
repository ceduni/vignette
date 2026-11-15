package org.titiplex.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.titiplex.api.dto.UpdateThumbnailLayoutRequest;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioHistoryAction;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.model.User;
import org.titiplex.service.storage.MediaContent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThumbnailManagementServiceTest {

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

    private ThumbnailManagementService service;

    private final Authentication auth = new UsernamePasswordAuthenticationToken("alice", "N/A", List.of());
    private Scenario scenario;
    private Thumbnail thumbnail;

    @BeforeEach
    void setUp() {
        service = new ThumbnailManagementService(thumbnailService, scenarioService, access, history, userService);

        scenario = new Scenario();
        scenario.setId(9L);
        thumbnail = new Thumbnail();
        thumbnail.setId(8L);
        thumbnail.setScenarioId(9L);
    }

    private void aliceIs(long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        when(userService.getUserByUsername("alice")).thenReturn(user);
    }

    private void thumbnailBelongsToScenario() {
        when(thumbnailService.getThumbnailById(8L)).thenReturn(thumbnail);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
    }

    // ── Reading ────────────────────────────────────────────────────────

    @Test
    void list_checksViewAccess() {
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        when(thumbnailService.listByScenarioId(9L)).thenReturn(List.of(thumbnail));

        assertEquals(List.of(thumbnail), service.list(9L, auth));

        verify(access).assertCanViewScenario(scenario, auth);
    }

    @Test
    void list_refusedWhenTheScenarioCannotBeViewed() {
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        doThrow(new java.util.NoSuchElementException("Scenario not found")).when(access).assertCanViewScenario(scenario, auth);

        assertThrows(java.util.NoSuchElementException.class, () -> service.list(9L, auth));
        verify(thumbnailService, never()).listByScenarioId(any());
    }

    @Test
    void loadContent_checksViewAccessOnTheThumbnailScenario() {
        MediaContent media = new MediaContent(null, "image/png", 4L, "\"e\"");
        thumbnailBelongsToScenario();
        when(thumbnailService.loadContent(8L)).thenReturn(media);

        assertSame(media, service.loadContent(8L, auth));

        verify(access).assertCanViewScenario(scenario, auth);
    }

    // ── Changing ───────────────────────────────────────────────────────

    @Test
    void upload_checksEditAccessSavesAndRecordsHistory() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "thumb.png", "image/png", new byte[]{1});
        Thumbnail saved = new Thumbnail();
        saved.setId(55L);
        saved.setTitle("Intro");
        aliceIs(12L);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        when(thumbnailService.save("Intro", image, scenario, userService.getUserByUsername("alice"))).thenReturn(saved);

        assertSame(saved, service.upload(9L, "Intro", image, auth));

        verify(access).assertCanEditScenario(scenario, auth);
        verify(history).record(9L, 12L, ScenarioHistoryAction.THUMBNAIL_ADDED, "Added thumbnail \"Intro\"");
    }

    @Test
    void upload_refusesAnEmptyImageBeforeAnyCheck() {
        MockMultipartFile empty = new MockMultipartFile("image", "thumb.png", "image/png", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> service.upload(9L, "Intro", empty, auth));
        verifyNoInteractions(access, thumbnailService);
    }

    @Test
    void upload_refusedWithoutEditAccess() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "thumb.png", "image/png", new byte[]{1});
        aliceIs(12L);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        doThrow(new AccessDeniedException("no")).when(access).assertCanEditScenario(scenario, auth);

        assertThrows(AccessDeniedException.class, () -> service.upload(9L, "Intro", image, auth));
        verify(thumbnailService, never()).save(any(), any(), any(), any());
        verifyNoInteractions(history);
    }

    @Test
    void updateLayout_checksEditAccessAndRecordsHistory() {
        UpdateThumbnailLayoutRequest request = new UpdateThumbnailLayoutRequest(2, 3, 2, 1);
        Thumbnail saved = new Thumbnail();
        thumbnailBelongsToScenario();
        aliceIs(12L);
        when(thumbnailService.updateLayout(8L, request)).thenReturn(saved);

        assertSame(saved, service.updateLayout(8L, request, auth));

        verify(access).assertCanEditScenario(scenario, auth);
        verify(history).record(9L, 12L, ScenarioHistoryAction.THUMBNAIL_UPDATED, "Repositioned a thumbnail");
    }

    @Test
    void updateTitle_checksEditAccessAndRecordsHistory() {
        Thumbnail saved = new Thumbnail();
        thumbnailBelongsToScenario();
        aliceIs(12L);
        when(thumbnailService.updateTitle(8L, "Arrival")).thenReturn(saved);

        assertSame(saved, service.updateTitle(8L, "Arrival", auth));

        verify(access).assertCanEditScenario(scenario, auth);
        verify(history).record(9L, 12L, ScenarioHistoryAction.THUMBNAIL_UPDATED, "Renamed a thumbnail");
    }

    @Test
    void updateTitle_refusedWithoutEditAccess() {
        thumbnailBelongsToScenario();
        doThrow(new AccessDeniedException("no")).when(access).assertCanEditScenario(scenario, auth);

        assertThrows(AccessDeniedException.class, () -> service.updateTitle(8L, "Arrival", auth));
        verify(thumbnailService, never()).updateTitle(any(), any());
    }

    @Test
    void reorder_checksEditAccessAndRecordsHistory() {
        List<Thumbnail> rows = List.of(thumbnail);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);
        aliceIs(12L);
        when(thumbnailService.reorder(9L, List.of(8L))).thenReturn(rows);

        assertSame(rows, service.reorder(9L, List.of(8L), auth));

        verify(access).assertCanEditScenario(scenario, auth);
        verify(history).record(9L, 12L, ScenarioHistoryAction.THUMBNAIL_UPDATED, "Reordered scenes");
    }

    @Test
    void delete_removesThumbnailAndRecordsHistory() {
        when(thumbnailService.getThumbnailById(8L)).thenReturn(thumbnail);
        aliceIs(12L);

        service.delete(8L, auth);

        verify(access).assertCanEditScenarioById(9L, auth);
        verify(thumbnailService).delete(8L);
        verify(history).record(9L, 12L, ScenarioHistoryAction.THUMBNAIL_DELETED, "Deleted a thumbnail");
    }

    @Test
    void delete_refusedWithoutEditAccess() {
        when(thumbnailService.getThumbnailById(8L)).thenReturn(thumbnail);
        doThrow(new AccessDeniedException("no")).when(access).assertCanEditScenarioById(9L, auth);

        assertThrows(AccessDeniedException.class, () -> service.delete(8L, auth));
        verify(thumbnailService, never()).delete(any());
        verifyNoInteractions(history);
    }
}
