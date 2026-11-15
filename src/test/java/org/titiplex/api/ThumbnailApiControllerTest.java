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
import org.titiplex.api.dto.ThumbnailRowDto;
import org.titiplex.api.dto.UpdateThumbnailLayoutRequest;
import org.titiplex.api.dto.UpdateThumbnailTitleRequest;
import org.titiplex.api.dto.UploadResponse;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.service.ThumbnailManagementService;
import org.titiplex.service.storage.MediaContent;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The controller only turns requests into service calls and results into responses; permissions are tested in the service. */
@ExtendWith(MockitoExtension.class)
class ThumbnailApiControllerTest {

    @Mock
    private ThumbnailManagementService thumbnails;

    @InjectMocks
    private ThumbnailApiController controller;

    private final Authentication auth = new UsernamePasswordAuthenticationToken("alice", "N/A", List.of());

    private static Thumbnail thumbnail(long id, String title, int idx, int width) {
        Thumbnail t = new Thumbnail();
        t.setId(id);
        t.setTitle(title);
        t.setIdx(idx);
        t.setGridColumn(idx);
        t.setGridRow(1);
        t.setGridColumnSpan(idx);
        t.setGridRowSpan(1);
        t.setImageWidth(width);
        t.setImageHeight(600);
        return t;
    }

    @Test
    void list_mapsThumbnailsToRows() {
        when(thumbnails.list(9L, auth)).thenReturn(List.of(thumbnail(1, "Scene 1", 1, 800), thumbnail(2, "Scene 2", 2, 1280)));

        List<ThumbnailRowDto> result = controller.list(9L, auth);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("Scene 1", result.get(0).title());
        assertEquals(2, result.get(1).idx());
        assertEquals(1280, result.get(1).imageWidth());
    }

    @Test
    void upload_returnsTheNewThumbnailId() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "thumb.png", "image/png", new byte[]{1, 2, 3});
        when(thumbnails.upload(9L, "Intro", image, auth)).thenReturn(thumbnail(55, "Intro", 1, 800));

        UploadResponse response = controller.upload(9L, "Intro", image, auth);

        assertEquals(55L, response.id());
    }

    @Test
    void content_buildsResponseEntityWithHeaders() {
        Resource resource = new ByteArrayResource(new byte[]{1, 2, 3, 4});
        when(thumbnails.loadContent(8L, auth)).thenReturn(new MediaContent(resource, "image/png", 4L, "\"etag-1\""));

        ResponseEntity<Resource> response = controller.content(8L, auth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("image/png", Objects.requireNonNull(response.getHeaders().getContentType()).toString());
        assertEquals(4L, response.getHeaders().getContentLength());
        assertEquals("\"etag-1\"", response.getHeaders().getETag());
        assertEquals("private, no-store", response.getHeaders().getFirst("Cache-Control"));
        assertEquals("default-src 'none'; style-src 'unsafe-inline'; sandbox",
                response.getHeaders().getFirst("Content-Security-Policy"));
        assertEquals(resource, response.getBody());
    }

    @Test
    void updateLayout_mapsTheResult() {
        UpdateThumbnailLayoutRequest request = new UpdateThumbnailLayoutRequest(2, 3, 2, 1);
        when(thumbnails.updateLayout(8L, request, auth)).thenReturn(thumbnail(8, "Scene 1", 2, 640));

        ThumbnailRowDto result = controller.updateLayout(8L, request, auth);

        assertEquals(8L, result.id());
        assertEquals(2, result.gridColumn());
        assertEquals(640, result.imageWidth());
    }

    @Test
    void updateTitle_passesTheNewTitle() {
        when(thumbnails.updateTitle(8L, "Arrival", auth)).thenReturn(thumbnail(8, "Arrival", 1, 640));

        ThumbnailRowDto result = controller.updateTitle(8L, new UpdateThumbnailTitleRequest("Arrival"), auth);

        assertEquals("Arrival", result.title());
    }

    @Test
    void reorder_mapsTheReorderedRows() {
        when(thumbnails.reorder(9L, List.of(2L, 1L), auth))
                .thenReturn(List.of(thumbnail(2, "B", 1, 100), thumbnail(1, "A", 2, 100)));

        List<ThumbnailRowDto> result = controller.reorder(9L, new ThumbnailApiController.ReorderRequest(List.of(2L, 1L)), auth);

        assertEquals(List.of(2L, 1L), result.stream().map(ThumbnailRowDto::id).toList());
    }

    @Test
    void delete_delegates() {
        controller.delete(8L, auth);

        verify(thumbnails).delete(8L, auth);
    }
}
