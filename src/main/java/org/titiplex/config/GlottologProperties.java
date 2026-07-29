package org.titiplex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "app.glottolog")
public class GlottologProperties {

    private boolean updateEnabled = true;
    /** When true, Java still runs ProcessBuilder + CSV import (legacy). */
    private boolean legacyPipelineEnabled = false;
    /** When true, BootstrapData imports languages from classpath if empty. Default false. */
    private boolean bootstrapImportEnabled = false;
    private int frequencyDaysMin = 1;
    private int frequencyDaysMax = 365;
    private String pythonExecutable = "python3";
    private Path scriptPath = Path.of("scripts/download_glottolog_languoid.py");
    private Path cacheDir = Path.of("./data/glottolog-cache");
    private Path outputCsv = Path.of("./data/glottolog/languoid.csv");
    private String version = "5.3";

    public boolean isUpdateEnabled() {
        return updateEnabled;
    }

    public void setUpdateEnabled(boolean updateEnabled) {
        this.updateEnabled = updateEnabled;
    }

    public boolean isLegacyPipelineEnabled() {
        return legacyPipelineEnabled;
    }

    public void setLegacyPipelineEnabled(boolean legacyPipelineEnabled) {
        this.legacyPipelineEnabled = legacyPipelineEnabled;
    }

    public boolean isBootstrapImportEnabled() {
        return bootstrapImportEnabled;
    }

    public void setBootstrapImportEnabled(boolean bootstrapImportEnabled) {
        this.bootstrapImportEnabled = bootstrapImportEnabled;
    }

    public int getFrequencyDaysMin() {
        return frequencyDaysMin;
    }

    public void setFrequencyDaysMin(int frequencyDaysMin) {
        this.frequencyDaysMin = frequencyDaysMin;
    }

    public int getFrequencyDaysMax() {
        return frequencyDaysMax;
    }

    public void setFrequencyDaysMax(int frequencyDaysMax) {
        this.frequencyDaysMax = frequencyDaysMax;
    }

    public String getPythonExecutable() {
        return pythonExecutable;
    }

    public void setPythonExecutable(String pythonExecutable) {
        this.pythonExecutable = pythonExecutable;
    }

    public Path getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(Path scriptPath) {
        this.scriptPath = scriptPath;
    }

    public Path getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    public Path getOutputCsv() {
        return outputCsv;
    }

    public void setOutputCsv(Path outputCsv) {
        this.outputCsv = outputCsv;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
