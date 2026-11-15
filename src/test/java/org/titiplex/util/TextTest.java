package org.titiplex.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextTest {

    @Test
    void blankToNull_trimsAndNullsBlank() {
        assertNull(Text.blankToNull(null));
        assertNull(Text.blankToNull("   "));
        assertEquals("a b", Text.blankToNull("  a b "));
    }

    @Test
    void trimOrDefault_usesFallbackForBlank() {
        assertEquals("x", Text.trimOrDefault(" ", "x"));
        assertEquals("y", Text.trimOrDefault(" y ", "x"));
    }

    @Test
    void optional_checksLengthAfterTrimming() {
        assertEquals("abc", Text.optional(" abc ", 3, "Field"));
        assertNull(Text.optional("", 3, "Field"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Text.optional("abcd", 3, "Field"));
        assertEquals("Field must be 3 characters or fewer", e.getMessage());
    }
}
