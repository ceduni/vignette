package org.titiplex.service.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.titiplex.config.StorageProperties;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path root;

    private FileStorageService storage;

    @BeforeEach
    void setUp() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setRoot(root);
        storage = new FileStorageService(properties);
        storage.init();
    }

    @Test
    void storeThumbnail_acceptsRasterImage() throws Exception {
        StoredFile stored = storage.storeThumbnail(
                new MockMultipartFile("image", "scene.png", "image/png", new byte[]{1, 2, 3}), 1L);

        assertEquals("image/png", stored.contentType());
        assertTrue(stored.relativePath().endsWith(".png"));
        assertTrue(Files.exists(root.resolve(stored.relativePath())));
    }

    /** SVG and XML can carry scripts and uploads are served back as-is, so they are refused. */
    @ParameterizedTest
    @ValueSource(strings = {"image/svg+xml", "text/xml", "application/xml", "image/svg+xml; charset=utf-8"})
    void storeThumbnail_refusesSvgAndXml(String contentType) {
        MockMultipartFile svg = new MockMultipartFile("image", "scene.svg", contentType,
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes());

        assertThrows(IllegalArgumentException.class, () -> storage.storeThumbnail(svg, 1L));
    }
}
