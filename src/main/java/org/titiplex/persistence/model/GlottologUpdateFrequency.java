package org.titiplex.persistence.model;

public enum GlottologUpdateFrequency {
    MANUAL(0),
    TWO_WEEKS(14),
    ONE_MONTH(30),
    THREE_MONTHS(90),
    SIX_MONTHS(180);

    private final long intervalDays;

    GlottologUpdateFrequency(long intervalDays) {
        this.intervalDays = intervalDays;
    }

    public long intervalDays() {
        return intervalDays;
    }

    public boolean isScheduled() {
        return this != MANUAL;
    }
}
