package org.dcm4che.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Apr 2019
 */
class DateTimeUtilsTest {

    private static final LocalDate LOCAL_DATE = LocalDate.of(2007, 4, 19);
    private static final LocalTime LOCAL_TIME = LocalTime.of(10, 20, 30, 456789000);
    private static final LocalDateTime LOCAL_DATE_TIME = LocalDateTime.of(LOCAL_DATE, LOCAL_TIME);
    private static final ZonedDateTime ZONED_DATE_TIME = ZonedDateTime.of(LOCAL_DATE_TIME, ZoneOffset.ofHours(2));

    @Test
    void parseDA() {
        assertEquals(LOCAL_DATE, DateTimeUtils.parseDA("20070419"));
        assertEquals(LOCAL_DATE, DateTimeUtils.parseDA("2007.04.19"));
    }

    @Test
    void formatDA() {
        assertEquals("20070419", DateTimeUtils.formatDA(LOCAL_DATE));
    }

    @ParameterizedTest
    @CsvSource({
            "10:00, 10",
            "10:20, 1020",
            "10:20:30, 102030",
            "10:20:30, 10:20:30",
            "10:20:30.456, 102030.456"
    })
    void parseTM(LocalTime expected, String value) {
        assertEquals(expected, DateTimeUtils.parseTM(value));
    }

    @ParameterizedTest
    @CsvSource({
            "10:59:59.999999999, 10",
            "10:20:59.999999999, 1020",
            "10:20:30.999999999, 102030",
            "10:20:30.999999999, 10:20:30",
            "10:20:30.456999999, 102030.456"
    })
    void parseTMMax(LocalTime expected, String value) {
        assertEquals(expected, DateTimeUtils.parseTMMax(value));
    }

    @Test
    void formatTM() {
        assertEquals("102030.456789", DateTimeUtils.formatTM(LOCAL_TIME));
    }

    @ParameterizedTest
    @CsvSource({
            "10, 2",
            "10, 3",
            "1020, 4",
            "1020, 5",
            "102030, 6",
            "102030, 7",
            "102030.456, 10"
    })
    void truncateTM(String expected, int maxLength) {
        assertEquals(expected, DateTimeUtils.truncateTM("102030.456789", maxLength));
    }

    @ParameterizedTest
    @CsvSource({
            "2007-01-01T00:00, 2007",
            "2007-04-01T00:00, 200704",
            "2007-04-19T00:00, 20070419",
            "2007-04-19T10:00, 2007041910",
            "2007-04-19T10:20, 200704191020",
            "2007-04-19T10:20:30, 20070419102030",
            "2007-04-19T10:20:30.456, 20070419102030.456"
    })
    void parseDT(LocalDateTime expected, String value) {
        assertEquals(expected, DateTimeUtils.parseDT(value));
    }

    @ParameterizedTest
    @CsvSource({
            "2007-01-01T00:00+02:00, 2007+0200",
            "2007-04-01T00:00+02:00, 200704+0200",
            "2007-04-19T00:00+02:00, 20070419+0200",
            "2007-04-19T10:00+02:00, 2007041910+0200",
            "2007-04-19T10:20+02:00, 200704191020+0200",
            "2007-04-19T10:20:30+02:00, 20070419102030+0200",
            "2007-04-19T10:20:30.456+02:00, 20070419102030.456+0200"
    })
    void parseZonedDT(ZonedDateTime expected, String value) {
        assertEquals(expected, DateTimeUtils.parseDT(value));
    }

    @ParameterizedTest
    @CsvSource({
            "2007-12-31T23:59:59.999999999, 2007",
            "2007-04-30T23:59:59.999999999, 200704",
            "2007-04-19T23:59:59.999999999, 20070419",
            "2007-04-19T10:59:59.999999999, 2007041910",
            "2007-04-19T10:20:59.999999999, 200704191020",
            "2007-04-19T10:20:30.999999999, 20070419102030",
            "2007-04-19T10:20:30.456999999, 20070419102030.456"
    })
    void parseDTMax(LocalDateTime expected, String value) {
        assertEquals(expected, DateTimeUtils.parseDTMax(value));
    }

    @ParameterizedTest
    @CsvSource({
            "2007-12-31T23:59:59.999999999+02:00, 2007+0200",
            "2007-04-30T23:59:59.999999999+02:00, 200704+0200",
            "2007-04-19T23:59:59.999999999+02:00, 20070419+0200",
            "2007-04-19T10:59:59.999999999+02:00, 2007041910+0200",
            "2007-04-19T10:20:59.999999999+02:00, 200704191020+0200",
            "2007-04-19T10:20:30.999999999+02:00, 20070419102030+0200",
            "2007-04-19T10:20:30.456999999+02:00, 20070419102030.456+0200"
    })
    void parseZonedDTMax(ZonedDateTime expected, String value) {
        assertEquals(expected, DateTimeUtils.parseDTMax(value));
    }

    @Test
    void formatDT() {
        assertEquals("20070419102030.456789", DateTimeUtils.formatDT(LOCAL_DATE_TIME));
        assertEquals("20070419102030.456789+0200", DateTimeUtils.formatDT(ZONED_DATE_TIME));
    }

    @ParameterizedTest
    @CsvSource({
            "2007, 4",
            "2007, 5",
            "200704, 6",
            "200704, 7",
            "20070419, 8",
            "20070419, 9",
            "2007041910, 10",
            "2007041910, 11",
            "200704191020, 12",
            "200704191020, 13",
            "20070419102030, 14",
            "20070419102030, 15",
            "20070419102030.456, 18"
    })
    void truncateDT(String expected, int maxLength) {
        assertEquals(expected, DateTimeUtils.truncateDT("20070419102030.456789", maxLength));
    }

    @ParameterizedTest
    @CsvSource({
            "2007+0200, 4",
            "2007+0200, 5",
            "200704+0200, 6",
            "200704+0200, 7",
            "20070419+0200, 8",
            "20070419+0200, 9",
            "2007041910+0200, 10",
            "2007041910+0200, 11",
            "200704191020+0200, 12",
            "200704191020+0200, 13",
            "20070419102030+0200, 14",
            "20070419102030+0200, 15",
            "20070419102030.456+0200, 18"
    })
    void truncateZonedDT(String expected, int maxLength) {
        assertEquals(expected, DateTimeUtils.truncateDT("20070419102030.456789+0200", maxLength));
    }

}