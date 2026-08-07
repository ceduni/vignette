package org.titiplex.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.titiplex.config.GlottologProperties;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Launches the external Python Glottolog worker once at startup when the catalogue is empty.
 */
@Service
public class GlottologPythonBootstrapService {

    private static final Pattern PG_JDBC = Pattern.compile(
            "^jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+)"
    );

    private final GlottologProperties properties;
    private final LanguageService languageService;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    public GlottologPythonBootstrapService(
            GlottologProperties properties,
            LanguageService languageService
    ) {
        this.properties = properties;
        this.languageService = languageService;
    }

    /**
     * @return true if languages are non-empty after the attempt
     */
    public boolean bootstrapIfEmpty() {
        if (languageService.count() > 0) {
            System.out.println("Startup Python bootstrap: languages already present — skip.");
            return true;
        }

        if (datasourceUrl == null || !datasourceUrl.startsWith("jdbc:postgresql:")) {
            System.out.println(
                    "Startup Python bootstrap: skipped (not PostgreSQL). "
                            + "Use profile pgdev + Postgres, or run: "
                            + "python3 -m glottolog_worker --bootstrap"
            );
            return languageService.count() > 0;
        }

        Path repoRoot = resolveRepoRoot();
        Path scriptsDir = repoRoot.resolve("scripts");
        if (!Files.isDirectory(scriptsDir.resolve("glottolog_worker"))) {
            System.out.println(
                    "Startup Python bootstrap: package scripts/glottolog_worker not found under "
                            + repoRoot
            );
            return false;
        }

        System.out.println(
                "Startup Python bootstrap: catalogue empty — launching "
                        + "python -m glottolog_worker --bootstrap …"
        );

        try {
            int exit = runWorkerBootstrap(scriptsDir);
            long count = languageService.count();
            if (exit == 0 && count > 0) {
                System.out.println(
                        "Startup Python bootstrap: OK — " + count + " languages in database."
                );
                return true;
            }
            System.out.println(
                    "Startup Python bootstrap: finished with exit=" + exit
                            + ", language count=" + count
            );
            return count > 0;
        } catch (Exception ex) {
            System.out.println("Startup Python bootstrap failed: " + ex.getMessage());
            return languageService.count() > 0;
        }
    }

    private int runWorkerBootstrap(Path scriptsDir) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(properties.getPythonExecutable());
        command.add("-m");
        command.add("glottolog_worker");
        command.add("--bootstrap");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(scriptsDir.toFile());
        processBuilder.redirectErrorStream(true);

        Map<String, String> env = processBuilder.environment();
        applyDatasourceEnv(env);

        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[glottolog_worker] " + line);
            }
        }

        boolean finished = process.waitFor(45, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Python bootstrap timed out after 45 minutes");
        }
        return process.exitValue();
    }

    private void applyDatasourceEnv(Map<String, String> env) {
        Matcher matcher = PG_JDBC.matcher(datasourceUrl.trim());
        if (!matcher.find()) {
            return;
        }
        env.put("PGHOST", matcher.group(1));
        env.put("PGPORT", matcher.group(2) != null ? matcher.group(2) : "5432");
        env.put("PGDATABASE", matcher.group(3));
        if (datasourceUsername != null && !datasourceUsername.isBlank()) {
            env.put("PGUSER", datasourceUsername);
        }
        if (datasourcePassword != null) {
            env.put("PGPASSWORD", datasourcePassword);
        }
    }

    private Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(cwd.resolve("scripts").resolve("glottolog_worker"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("scripts").resolve("glottolog_worker"))) {
            return parent;
        }
        return cwd;
    }
}
