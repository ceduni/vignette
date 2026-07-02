package org.titiplex.service.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.titiplex.config.StorageProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Service
public class FileStorageService {

    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/webp", ".webp"),
            Map.entry("image/gif", ".gif"),
            Map.entry("image/svg+xml", ".svg"),
            Map.entry("text/xml", ".xml"),
            Map.entry("application/xml", ".xml"),
            Map.entry("audio/webm", ".webm"),
            Map.entry("audio/ogg", ".ogg"),
            Map.entry("audio/mp4", ".mp4"),
            Map.entry("audio/aac", ".aac"),
            Map.entry("audio/mpeg", ".mp3"),
            Map.entry("audio/wav", ".wav"),
            Map.entry("audio/x-wav", ".wav")
    );

    private final StorageProperties properties;
    private Path root;

    public FileStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() throws IOException {
        this.root = properties.getRoot().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public StoredFile storeThumbnail(MultipartFile file, long scenarioId) throws Exception {
        return store(file, "thumbnails/scenario-" + scenarioId);
    }

    public StoredFile storeAudio(MultipartFile file, long scenarioId, long thumbnailId) throws Exception {
        return store(file, "audios/scenario-" + scenarioId + "/thumbnail-" + thumbnailId);
    }

    private StoredFile store(MultipartFile file, String logicalDir) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }

        Files.createDirectories(root);
        String contentType = normalizeContentType(file.getContentType());
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException("unsupported content type: " + contentType);
        }

        Files.createDirectories(root);
        Path tmp = Files.createTempFile(root, "upload-", ".tmp");

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long size;

        try (InputStream in = new DigestInputStream(file.getInputStream(), digest);
             OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.TRUNCATE_EXISTING)) {
            size = in.transferTo(out);
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }

        String sha256 = HexFormat.of().formatHex(digest.digest());
        String relativePath = logicalDir
                + "/" + sha256.substring(0, 2)
                + "/" + sha256.substring(2, 4)
                + "/" + sha256 + extension;

        Path finalPath = resolve(relativePath);
        Files.createDirectories(finalPath.getParent());

        if (Files.notExists(finalPath)) {
            try {
                Files.move(tmp, finalPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            Files.deleteIfExists(tmp);
        }

        return new StoredFile(
                relativePath.replace('\\', '/'),
                sha256,
                size,
                contentType,
                file.getOriginalFilename()
        );
    }

    public MediaContent load(String relativePath, String contentType, String sha256) {
        Path path = resolve(relativePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("stored file not found");
        }

        Resource resource = new FileSystemResource(path.toFile());
        try {
            return new MediaContent(resource, contentType, Files.size(path), "\"" + sha256 + "\"");
        } catch (IOException e) {
            throw new IllegalStateException("cannot read stored file metadata", e);
        }
    }

    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ignored) {
        }
    }

    private Path resolve(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("invalid storage path");
        }
        return resolved;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }

        String normalized = contentType.toLowerCase().trim();
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        return normalized;
    }
}