package org.titiplex.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.titiplex.api.dto.GlottologUpdateJobStatusDto;
import org.titiplex.api.dto.GlottologUpdateResultDto;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class GlottologUpdateJobService {

    private static final int MAX_LOG_LINES = 12;

    private final GlottologUpdateService glottologUpdateService;
    private final GlottologAdminService glottologAdminService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "glottolog-update-job");
        thread.setDaemon(true);
        return thread;
    });

    private final Object monitor = new Object();
    private GlottologJob currentJob;

    public GlottologUpdateJobService(
            GlottologUpdateService glottologUpdateService,
            GlottologAdminService glottologAdminService
    ) {
        this.glottologUpdateService = glottologUpdateService;
        this.glottologAdminService = glottologAdminService;
    }

    public GlottologUpdateJobStatusDto startJob(String triggeredByUsername) {
        synchronized (monitor) {
            if (currentJob != null && currentJob.isRunning()) {
                return currentJob.toDto();
            }

            long historyId = glottologAdminService.recordUpdateStarted(triggeredByUsername);
            GlottologJob job = new GlottologJob(UUID.randomUUID().toString(), historyId);
            currentJob = job;
            executor.submit(() -> runJob(job));
            return job.toDto();
        }
    }

    public GlottologUpdateJobStatusDto currentStatus() {
        synchronized (monitor) {
            if (currentJob == null) {
                return new GlottologUpdateJobStatusDto(
                        null,
                        "IDLE",
                        "IDLE",
                        "No import running",
                        "No Glottolog import has been started yet.",
                        0,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null
                );
            }
            return currentJob.toDto();
        }
    }

    private void runJob(GlottologJob job) {
        try {
            GlottologUpdateResultDto result = glottologUpdateService.downloadAndSync(job);
            job.markSucceeded(result);
            glottologAdminService.recordUpdateSucceeded(job.historyId(), result);
        } catch (IOException | RuntimeException ex) {
            String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            job.markFailed(error);
            glottologAdminService.recordUpdateFailed(job.historyId(), error);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static final class GlottologJob implements GlottologUpdateProgressListener {
        private final String jobId;
        private final long historyId;
        private final Instant startedAt;
        private final Deque<String> recentLogLines = new ArrayDeque<>();

        private String state = "RUNNING";
        private String stage = "QUEUED";
        private String stageLabel = "Waiting";
        private String message = "Preparing the import job.";
        private int progressPercent = 0;
        private boolean estimatedProgress = false;
        private Integer sourceRows;
        private Integer selectedRows;
        private Integer processedRows;
        private Integer inserted;
        private Integer updated;
        private Integer unchanged;
        private Long databaseCount;
        private Instant finishedAt;
        private GlottologUpdateResultDto result;
        private String error;

        private GlottologJob(String jobId, long historyId) {
            this.jobId = jobId;
            this.historyId = historyId;
            this.startedAt = Instant.now();
        }

        long historyId() {
            return historyId;
        }

        synchronized boolean isRunning() {
            return "RUNNING".equals(state);
        }

        @Override
        public synchronized void onStageChanged(
                String stage,
                String stageLabel,
                String message,
                int progressPercent,
                boolean estimatedProgress
        ) {
            this.stage = stage;
            this.stageLabel = stageLabel;
            this.message = message;
            this.progressPercent = Math.max(0, Math.min(progressPercent, 100));
            this.estimatedProgress = estimatedProgress;
        }

        @Override
        public synchronized void onDownloadLogLine(String line) {
            if (line == null || line.isBlank()) {
                return;
            }
            if (recentLogLines.size() >= MAX_LOG_LINES) {
                recentLogLines.removeFirst();
            }
            recentLogLines.addLast(line.trim());
        }

        @Override
        public synchronized void onSyncPrepared(int sourceRows, int selectedRows, long databaseCount) {
            this.sourceRows = sourceRows;
            this.selectedRows = selectedRows;
            this.databaseCount = databaseCount;
            this.processedRows = 0;
            this.inserted = 0;
            this.updated = 0;
            this.unchanged = 0;
        }

        @Override
        public synchronized void onSyncProgress(
                int processedRows,
                int totalRows,
                int inserted,
                int updated,
                int unchanged
        ) {
            this.processedRows = processedRows;
            this.selectedRows = totalRows;
            this.inserted = inserted;
            this.updated = updated;
            this.unchanged = unchanged;

            int boundedTotal = Math.max(totalRows, 1);
            int percent = 45 + (int) Math.round((processedRows * 45.0) / boundedTotal);
            this.progressPercent = Math.max(this.progressPercent, Math.min(percent, 90));
            this.stage = "SYNCING_DATABASE";
            this.stageLabel = "Database sync";
            this.message = "Comparing and preparing database writes.";
            this.estimatedProgress = false;
        }

        synchronized void markSucceeded(GlottologUpdateResultDto result) {
            this.state = "SUCCEEDED";
            this.progressPercent = 100;
            this.estimatedProgress = false;
            this.finishedAt = Instant.now();
            this.result = result;
            this.error = null;
            if (result != null && result.sourceUnchanged()) {
                this.stage = "SOURCE_UNCHANGED";
                this.stageLabel = "No changes";
                this.message = "No changes since the last update.";
            } else {
                this.stage = "COMPLETED";
                this.stageLabel = "Import completed";
                this.message = "Glottolog download and sync are complete.";
            }
            if (result != null && result.sync() != null) {
                this.sourceRows = result.sync().sourceRows();
                this.selectedRows = result.sync().selectedRows();
                this.processedRows = result.sync().selectedRows();
                this.inserted = result.sync().inserted();
                this.updated = result.sync().updated();
                this.unchanged = result.sync().unchanged();
                this.databaseCount = result.sync().databaseCount();
            }
        }

        synchronized void markFailed(String error) {
            this.state = "FAILED";
            this.stage = "FAILED";
            this.stageLabel = "Import failed";
            this.message = "The Glottolog import stopped before completion.";
            this.finishedAt = Instant.now();
            this.error = error;
            this.estimatedProgress = false;
        }

        synchronized GlottologUpdateJobStatusDto toDto() {
            return new GlottologUpdateJobStatusDto(
                    jobId,
                    state,
                    stage,
                    stageLabel,
                    message,
                    progressPercent,
                    estimatedProgress,
                    sourceRows,
                    selectedRows,
                    processedRows,
                    inserted,
                    updated,
                    unchanged,
                    databaseCount,
                    startedAt,
                    finishedAt,
                    new ArrayList<>(recentLogLines),
                    result,
                    error
            );
        }
    }
}
