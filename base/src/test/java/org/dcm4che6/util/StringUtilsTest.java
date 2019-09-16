package org.dcm4che6.util;

import org.dcm4che6.util.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Aug 2018
 */
class StringUtilsTest {

    private static final char DELIM = '\\';
    private static final String JOINED = "A\\\\C";
    private static String[] ARRAY = { "A", "", "C" };
    private static String[] ONE = { "A" };

    @Test
    void cut() {
        assertEquals("", StringUtils.cut(JOINED, 0, DELIM, 0));
        for (int i = 0; i < ARRAY.length; i++) {
            assertEquals(ARRAY[i], StringUtils.cut(JOINED, JOINED.length(), DELIM, i));
        }
        assertEquals("", StringUtils.cut(JOINED, JOINED.length(), DELIM, ARRAY.length));
    }

    @Test
    void split() {
        assertArrayEquals(StringUtils.EMPTY_STRINGS, StringUtils.split(JOINED, 0, DELIM));
        assertArrayEquals(ONE, StringUtils.split(JOINED, 1, DELIM));
        assertArrayEquals(ARRAY, StringUtils.split(JOINED, JOINED.length(), DELIM));
    }

    @Test
    void join() {
        assertNull(StringUtils.join(null, 0, 0, DELIM));
        assertEquals("", StringUtils.join(StringUtils.EMPTY_STRINGS, 0, 0, DELIM));
        assertEquals("A", StringUtils.join(ONE, 0, 1, DELIM));
        assertEquals(JOINED, StringUtils.join(ARRAY, 0, ARRAY.length, DELIM));
    }

    @Test
    void trim() {
        assertEquals(" A", StringUtils.trim(" A ", StringUtils.Trim.TRAILING));
        assertEquals("A ", StringUtils.trim(" A ", StringUtils.Trim.LEADING));
        assertEquals("A", StringUtils.trim(" A ", StringUtils.Trim.LEADING_AND_TRAILING));
    }
}