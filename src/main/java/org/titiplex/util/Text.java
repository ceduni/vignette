package org.titiplex.util;

/** Cleaning and length checks for user-supplied text, shared by the services. */
public final class Text {

    private Text() {
    }

    /** Trimmed text, or null when the value is null or blank. */
    public static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Trimmed text, or the fallback when the value is null or blank. */
    public static String trimOrDefault(String value, String fallback) {
        String trimmed = blankToNull(value);
        return trimmed != null ? trimmed : fallback;
    }

    /** Throws "{field} must be {max} characters or fewer" when the text is too long; returns it unchanged otherwise. */
    public static String requireMaxLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " must be " + max + " characters or fewer");
        }
        return value;
    }

    /** Trimmed, null when blank, and checked against the maximum length. */
    public static String optional(String value, int max, String field) {
        return requireMaxLength(blankToNull(value), max, field);
    }

    /** Trimmed text, or an empty string when the value is null. */
    public static String trimmedOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** First value that is neither null nor blank, or an empty string when there is none. */
    public static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
