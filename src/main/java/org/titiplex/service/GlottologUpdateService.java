package org.titiplex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.titiplex.api.dto.GlottologImportPreviewDto;
import org.titiplex.api.dto.GlottologSyncResultDto;
import org.titiplex.api.dto.GlottologUpdateResultDto;
import org.titiplex.config.GlottologProperties;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class GlottologUpdateService {

    private static final int SCRIPT_OUTPUT_TAIL_CHARS = 4000;
    private static final String IMPORTED_HASH_SUFFIX = ".imported.sha256";
    private static final String ZENODO_FINGERPRINT_SUFFIX = ".zenodo.fingerprint";
    private static final Map<String, Integer> KNOWN_ZENODO_RECORDS = Map.of(
            "5.3", 18840967
    );

    private final GlottologProperties properties;
    private final LanguageImportService languageImportService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public GlottologUpdateService(
            GlottologProperties properties,
            LanguageImportService languageImportService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.languageImportService = languageImportService;
        this.objectMapper = objectMapper;
    }

    public GlottologImportPreviewDto preview() throws IOException {
        Path csvPath = resolveActiveCsvPath();
        if (csvPath != null) {
            return languageImportService.previewFromCsv(csvPath);
        }
        return languageImportService.previewFromClasspath();
    }

    public GlottologUpdateResultDto downloadAndSync() throws IOException {
        return downloadAndSync(GlottologUpdateProgressListener.NONE);
    }

    public GlottologUpdateResultDto downloadAndSync(GlottologUpdateProgressListener progressListener) throws IOException {
        assertUpdateEnabled();
        progressListener.onStageChanged(
                "PREPARING_DOWNLOAD",
                "Preparing download",
                "Preparing the script and working directories.",
                2,
                false
        );

        Path workingDirectory = Path.of("").toAbsolutePath();
        Path scriptPath = resolveScriptPath(workingDirectory);
        Path outputCsv = properties.getOutputCsv().toAbsolutePath();
        Path cacheDir = properties.getCacheDir().toAbsolutePath();

        Files.createDirectories(outputCsv.getParent());
        Files.createDirectories(cacheDir);

        progressListener.onStageChanged(
                "CHECKING_REMOTE",
                "Zenodo check",
                "Checking the remote Glottolog version before download.",
                5,
                false
        );

        ZenodoFingerprint remoteFingerprint = fetchRemoteFingerprintQuietly(properties.getVersion());
        ZenodoFingerprint localFingerprint = readZenodoFingerprint(outputCsv);

        // Same Zenodo release + local CSV present → skip remote download.
        // Still sync if the local CSV content changed (e.g. click orthography fix).
        boolean skipDownload = remoteFingerprint != null
                && remoteFingerprint.equals(localFingerprint)
                && Files.isRegularFile(outputCsv)
                && Files.size(outputCsv) > 0;

        long downloadStartedAt = System.currentTimeMillis();
        String scriptOutput = "";
        long downloadDurationMs = 0L;

        if (!skipDownload) {
            scriptOutput = runDownloadScript(workingDirectory, scriptPath, outputCsv, cacheDir, progressListener);
            downloadDurationMs = System.currentTimeMillis() - downloadStartedAt;
        }

        if (!Files.exists(outputCsv) || Files.size(outputCsv) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Glottolog download finished but output CSV is missing or empty: " + outputCsv
            );
        }

        GlottologImportPreviewDto preview = languageImportService.previewFromCsv(outputCsv);

        progressListener.onStageChanged(
                "COMPARING_SOURCE",
                "Compare with last import",
                "Checking whether the Glottolog CSV changed since the last sync.",
                42,
                false
        );

        String newHash = sha256Hex(outputCsv);
        String previousHash = readImportedHash(outputCsv);

        // Same CSV content + DB already populated → nothing to write.
        // Covers Zenodo-skip, script "source unchanged", and identical re-downloads.
        if (previousHash != null
                && Objects.equals(previousHash, newHash)
                && preview.databaseCount() > 0) {
            if (remoteFingerprint != null) {
                writeZenodoFingerprint(outputCsv, remoteFingerprint);
            }
            writeImportedHash(outputCsv, newHash);
            String skipMessage = skipDownload
                    ? "Zenodo release unchanged — download and sync skipped."
                    : (scriptOutput.contains("GLOTTOLOG_SOURCE_UNCHANGED=1")
                    ? tail(scriptOutput)
                    : "No changes since the last update.");
            return unchangedResult(
                    progressListener,
                    preview,
                    downloadDurationMs,
                    skipMessage
            );
        }

        progressListener.onStageChanged(
                "PREPARING_SYNC",
                "Preparing import",
                skipDownload
                        ? "Reading and filtering the local CSV."
                        : "Download finished; reading and filtering the CSV.",
                45,
                false
        );

        var sync = languageImportService.syncFromCsv(outputCsv, progressListener);
        writeImportedHash(outputCsv, newHash);
        if (remoteFingerprint != null) {
            writeZenodoFingerprint(outputCsv, remoteFingerprint);
        } else {
            // Si l'API Zenodo était indisponible avant le download, on retente après succès.
            ZenodoFingerprint fetchedAfter = fetchRemoteFingerprintQuietly(properties.getVersion());
            if (fetchedAfter != null) {
                writeZenodoFingerprint(outputCsv, fetchedAfter);
            }
        }
        progressListener.onStageChanged(
                "COMPLETED",
                "Import completed",
                "Glottolog download and sync are complete.",
                100,
                false
        );
        return new GlottologUpdateResultDto(
                properties.getVersion(),
                downloadDurationMs,
                tail(scriptOutput),
                sync,
                false
        );
    }

    public Path resolveActiveCsvPath() {
        Path outputCsv = properties.getOutputCsv();
        if (outputCsv != null && Files.isRegularFile(outputCsv)) {
            return outputCsv;
        }
        return null;
    }

    private GlottologUpdateResultDto unchangedResult(
            GlottologUpdateProgressListener progressListener,
            GlottologImportPreviewDto preview,
            long downloadDurationMs,
            String scriptOutput
    ) {
        progressListener.onSyncPrepared(preview.sourceRows(), preview.selectedRows(), preview.databaseCount());
        progressListener.onStageChanged(
                "SOURCE_UNCHANGED",
                "No changes",
                "No changes since the last update.",
                100,
                false
        );
        GlottologSyncResultDto sync = new GlottologSyncResultDto(
                preview.sourceRows(),
                preview.selectedRows(),
                0,
                0,
                preview.selectedRows(),
                preview.databaseCount(),
                0L
        );
        return new GlottologUpdateResultDto(
                properties.getVersion(),
                downloadDurationMs,
                scriptOutput == null ? "" : scriptOutput,
                sync,
                true
        );
    }

    private void assertUpdateEnabled() {
        if (!properties.isUpdateEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Glottolog update is disabled on this server."
            );
        }
    }

    private Path resolveScriptPath(Path workingDirectory) {
        Path scriptPath = properties.getScriptPath();
        Path resolved = scriptPath.isAbsolute()
                ? scriptPath
                : workingDirectory.resolve(scriptPath).normalize();

        if (!Files.isRegularFile(resolved)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Glottolog download script not found: " + resolved
            );
        }
        return resolved;
    }

    private String runDownloadScript(
            Path workingDirectory,
            Path scriptPath,
            Path outputCsv,
            Path cacheDir,
            GlottologUpdateProgressListener progressListener
    ) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(properties.getPythonExecutable());
        command.add(scriptPath.toString());
        command.add("--output");
        command.add(outputCsv.toString());
        command.add("--cache-dir");
        command.add(cacheDir.toString());
        command.add("--version");
        command.add(properties.getVersion());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            progressListener.onStageChanged(
                    "DOWNLOADING",
                    "Downloading from Glottolog",
                    "Fetching and converting CLDF data into a filtered CSV.",
                    8,
                    true
            );
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                    progressListener.onDownloadLogLine(line);
                    lineCount++;
                    int estimatedProgress = Math.min(40, 8 + Math.min(lineCount * 2, 32));
                    progressListener.onStageChanged(
                            "DOWNLOADING",
                            "Downloading from Glottolog",
                            line,
                            estimatedProgress,
                            true
                    );
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Glottolog download script failed (exit " + exitCode + "): " + tail(output.toString())
                );
            }
            return output.toString();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Glottolog download was interrupted."
            );
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not run Glottolog download script. Is Python 3 available? " + ex.getMessage()
            );
        }
    }

    private ZenodoFingerprint fetchRemoteFingerprintQuietly(String version) {
        try {
            return fetchRemoteFingerprint(version);
        } catch (Exception ex) {
            // Réseau / API indisponible : on continue avec le flux classique (download).
            return null;
        }
    }

    private ZenodoFingerprint fetchRemoteFingerprint(String version) throws IOException, InterruptedException {
        int recordId = resolveZenodoRecordId(version);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://zenodo.org/api/records/" + recordId))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Zenodo API returned HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode files = root.path("files");
        if (!files.isArray()) {
            throw new IOException("Zenodo record has no files array");
        }

        for (JsonNode file : files) {
            String key = file.path("key").asText("");
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".zip") && lower.contains("cldf")) {
                String checksum = file.path("checksum").asText(null);
                if (checksum == null || checksum.isBlank()) {
                    checksum = file.path("checksums").path("md5").asText(null);
                    if (checksum != null && !checksum.isBlank() && !checksum.startsWith("md5:")) {
                        checksum = "md5:" + checksum;
                    }
                }
                if (checksum == null || checksum.isBlank()) {
                    throw new IOException("Zenodo CLDF zip has no checksum: " + key);
                }
                return new ZenodoFingerprint(version, recordId, checksum.trim());
            }
        }
        throw new IOException("No CLDF zip found in Zenodo record " + recordId);
    }

    private int resolveZenodoRecordId(String version) throws IOException, InterruptedException {
        Integer known = KNOWN_ZENODO_RECORDS.get(version);
        if (known != null) {
            return known;
        }

        String query = URI.create(
                "https://zenodo.org/api/records/?q="
                        + java.net.URLEncoder.encode(
                        "metadata.title:\"Glottolog database " + version + " as CLDF\"",
                        StandardCharsets.UTF_8
                )
                        + "&size=1"
        ).toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(query))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Zenodo search returned HTTP " + response.statusCode());
        }
        JsonNode hits = objectMapper.readTree(response.body()).path("hits").path("hits");
        if (!hits.isArray() || hits.isEmpty()) {
            throw new IOException("Could not resolve Zenodo record for Glottolog " + version);
        }
        return hits.get(0).path("id").asInt();
    }

    private static Path zenodoFingerprintPath(Path csvPath) {
        return csvPath.resolveSibling(csvPath.getFileName() + ZENODO_FINGERPRINT_SUFFIX);
    }

    private static ZenodoFingerprint readZenodoFingerprint(Path csvPath) throws IOException {
        Path path = zenodoFingerprintPath(csvPath);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.contains("=")) {
                continue;
            }
            int idx = trimmed.indexOf('=');
            values.put(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim());
        }
        String version = values.get("version");
        String recordId = values.get("recordId");
        String checksum = values.get("checksum");
        if (version == null || recordId == null || checksum == null) {
            return null;
        }
        try {
            return new ZenodoFingerprint(version, Integer.parseInt(recordId), checksum);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void writeZenodoFingerprint(Path csvPath, ZenodoFingerprint fingerprint) throws IOException {
        String content = """
                version=%s
                recordId=%d
                checksum=%s
                """.formatted(fingerprint.version(), fingerprint.recordId(), fingerprint.checksum());
        Files.writeString(zenodoFingerprintPath(csvPath), content, StandardCharsets.UTF_8);
    }

    private static String tail(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= SCRIPT_OUTPUT_TAIL_CHARS) {
            return value.trim();
        }
        return value.substring(value.length() - SCRIPT_OUTPUT_TAIL_CHARS).trim();
    }

    private static Path importedHashPath(Path csvPath) {
        return csvPath.resolveSibling(csvPath.getFileName() + IMPORTED_HASH_SUFFIX);
    }

    private static String readImportedHash(Path csvPath) throws IOException {
        Path hashPath = importedHashPath(csvPath);
        if (!Files.isRegularFile(hashPath)) {
            return null;
        }
        String value = Files.readString(hashPath, StandardCharsets.UTF_8).trim();
        return value.isEmpty() ? null : value;
    }

    private static void writeImportedHash(Path csvPath, String hash) throws IOException {
        Files.writeString(importedHashPath(csvPath), hash + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(file);
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                digestInputStream.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for Glottolog CSV comparison", ex);
        }
    }

    private record ZenodoFingerprint(String version, int recordId, String checksum) {
    }
}
