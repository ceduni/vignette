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

    @Test
    void storeThumbnail_acceptsSvg_andStoresTheSanitizedVersion() throws Exception {
        MockMultipartFile svg = new MockMultipartFile("image", "scene.svg", "image/svg+xml; charset=utf-8",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script><rect width=\"4\" onclick=\"alert(2)\"/></svg>".getBytes());

        StoredFile stored = storage.storeThumbnail(svg, 1L);

        assertEquals("image/svg+xml", stored.contentType());
        assertTrue(stored.relativePath().endsWith(".svg"));
        String content = Files.readString(root.resolve(stored.relativePath()));
        assertTrue(content.contains("<rect"));
        assertFalse(content.contains("script"));
        assertFalse(content.contains("onclick"));
    }

    @Test
    void storeThumbnail_refusesMalformedSvg() {
        MockMultipartFile svg = new MockMultipartFile("image", "scene.svg", "image/svg+xml", "<svg><g>".getBytes());

        assertThrows(IllegalArgumentException.class, () -> storage.storeThumbnail(svg, 1L));
    }

    /** Plain XML is still refused; SVG is only accepted for images, not for audio. */
    @ParameterizedTest
    @ValueSource(strings = {"text/xml", "application/xml"})
    void storeThumbnail_refusesXml(String contentType) {
        MockMultipartFile xml = new MockMultipartFile("image", "scene.xml", contentType,
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes());

        assertThrows(IllegalArgumentException.class, () -> storage.storeThumbnail(xml, 1L));
    }

    @Test
    void storeAudio_refusesSvg() {
        MockMultipartFile svg = new MockMultipartFile("audio", "a.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes());

        assertThrows(IllegalArgumentException.class, () -> storage.storeAudio(svg, 1L, 2L));
    }
}
