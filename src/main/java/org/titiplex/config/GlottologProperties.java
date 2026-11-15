package org.titiplex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Glottolog settings read by the Java side. Downloading and importing languages is done
 * by the Python worker ({@code scripts/glottolog_worker}); Java only starts its bootstrap
 * on an empty database and exposes the admin settings.
 */
@Component
@ConfigurationProperties(prefix = "app.glottolog")
public class GlottologProperties {

    /** When true, Java runs Python worker --bootstrap if languages are empty at startup. */
    private boolean bootstrapImportEnabled = false;
    private int frequencyDaysMin = 1;
    private int frequencyDaysMax = 365;
    private String pythonExecutable = "python3";
    private String version = "5.3";

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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
