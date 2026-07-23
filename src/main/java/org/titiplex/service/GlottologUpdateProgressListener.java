package org.titiplex.service;

public interface GlottologUpdateProgressListener {

    GlottologUpdateProgressListener NONE = new GlottologUpdateProgressListener() {
    };

    default void onStageChanged(
            String stage,
            String stageLabel,
            String message,
            int progressPercent,
            boolean estimatedProgress
    ) {
    }

    default void onDownloadLogLine(String line) {
    }

    default void onSyncPrepared(int sourceRows, int selectedRows, long databaseCount) {
    }

    default void onSyncProgress(
            int processedRows,
            int totalRows,
            int inserted,
            int updated,
            int unchanged
    ) {
    }
}
