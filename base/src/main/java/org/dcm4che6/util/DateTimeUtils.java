package org.dcm4che6.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;

import static java.time.temporal.ChronoField.*;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Apr 2019
 */
public class DateTimeUtils {
    private static final DateTimeFormatter DA_PARSER = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4)
            .optionalStart()
            .appendLiteral('.')
            .optionalEnd()
            .appendValue(MONTH_OF_YEAR, 2)
            .optionalStart()
            .appendLiteral('.')
            .optionalEnd()
            .appendValue(DAY_OF_MONTH, 2)
            .toFormatter();

    private static final DateTimeFormatter DA_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4)
            .appendValue(MONTH_OF_YEAR, 2)
            .appendValue(DAY_OF_MONTH, 2)
            .toFormatter();

    private static final DateTimeFormatter TM_PARSER = new DateTimeFormatterBuilder()
            .appendValue(HOUR_OF_DAY, 2)
            .optionalStart()
            .optionalStart()
            .appendLiteral(':')
            .optionalEnd()
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .optionalStart()
            .appendLiteral(':')
            .optionalEnd()
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(NANO_OF_SECOND, 0, 6, true)
            .toFormatter();

    private static final DateTimeFormatter TM_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(HOUR_OF_DAY, 2)
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendValue(SECOND_OF_MINUTE, 2)
            .appendFraction(NANO_OF_SECOND, 6, 6, true)
            .toFormatter();


    private static final DateTimeFormatter DT_PARSER = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4)
            .optionalStart()
            .appendValue(MONTH_OF_YEAR, 2)
            .optionalStart()
            .appendValue(DAY_OF_MONTH, 2)
            .optionalStart()
            .appendValue(HOUR_OF_DAY, 2)
            .optionalStart()
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(NANO_OF_SECOND, 0, 6, true)
            .optionalEnd()
            .optionalEnd()
            .optionalEnd()
            .optionalEnd()
            .optionalEnd()
            .optionalEnd()
            .optionalStart()
            .appendOffset("+HHMM", "+0000")
            .toFormatter();

    private static final DateTimeFormatter DT_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4)
            .appendValue(MONTH_OF_YEAR, 2)
            .appendValue(DAY_OF_MONTH, 2)
            .appendValue(HOUR_OF_DAY, 2)
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendValue(SECOND_OF_MINUTE, 2)
            .appendFraction(NANO_OF_SECOND, 6, 6, true)
            .optionalStart()
            .appendOffset("+HHMM", "+0000")
            .toFormatter();


    public static LocalDate parseDA(String value) {
        return LocalDate.from(DA_PARSER.parse(value));
    }

    public static String formatDA(Temporal value) {
        return DA_FORMATTER.format(value);
    }

    public static LocalTime parseTM(String value) {
        return LocalTime.from(TM_PARSER.parse(value));
    }

    public static LocalTime parseTMMax(String value) {
        return parseTM(value).plusNanos(nanosToAdd(value));
    }

    public static String formatTM(Temporal value) {
        return TM_FORMATTER.format(value);
    }

    public static Temporal parseDT(String value) {
        TemporalAccessor temporal = DT_PARSER.parse(value);
        LocalDate date = temporal.isSupported(DAY_OF_MONTH)
                ? LocalDate.from(temporal)
                : LocalDate.of(temporal.get(YEAR), getMonth(temporal), 1);
        LocalTime time = temporal.isSupported(HOUR_OF_DAY)
                ? LocalTime.from(temporal)
                : LocalTime.MIN;
        LocalDateTime dateTime = LocalDateTime.of(date, time);
        return temporal.isSupported(OFFSET_SECONDS)
                ? ZonedDateTime.of(dateTime, ZoneOffset.ofTotalSeconds(temporal.get(OFFSET_SECONDS)))
                : dateTime;
    }

    private static int getMonth(TemporalAccessor temporal) {
        return temporal.isSupported(MONTH_OF_YEAR) ? temporal.get(MONTH_OF_YEAR) : 1;
    }

    public static Temporal parseDTMax(String value) {
        int length = lengthWithoutZone(value);
        return length > 8
                ? parseDT(value).plus(nanosToAdd(length - 8), ChronoUnit.NANOS)
                : parseDT(value)
                    .plus(1, yearsMonthsDays(length))
                    .minus(1, ChronoUnit.NANOS);
    }

    public static String formatDT(Temporal value) {
        return DT_FORMATTER.format(value);
    }

    public static String truncateTM(String value, int maxLength) {
        if (maxLength < 2)
            throw new IllegalArgumentException("maxLength %d < 2" + maxLength);

        return truncate(value, value.length(), maxLength, 8);
    }

    public static String truncateDT(String value, int maxLength) {
        if (maxLength < 4)
            throw new IllegalArgumentException("maxLength %d < 4" + maxLength);

        int index = indexOfZone(value);
        return index < 0
                ? truncate(value, value.length(), maxLength, 16)
                : truncate(value, index, maxLength, 16) + value.substring(index);

    }

    private static long nanosToAdd(String tm) {
        int length = tm.length();
        int index = tm.lastIndexOf(':');
        if (index > 0) {
            length--;
            if (index > 4)
                length--;
        }
        return nanosToAdd(length);
    }

    private static long nanosToAdd(int length) {
        switch (length) {
            case 2:
                return 3599999999999L;
            case 4:
                return 59999999999L;
            case 6:
            case 7:
                return 999999999L;
            case 8:
                return 99999999L;
            case 9:
                return 9999999L;
            case 10:
                return 999999L;
            case 11:
                return 99999L;
            case 12:
                return 9999L;
            case 13:
                return 999L;
        }
        throw new IllegalArgumentException("length: " + length);
    }

    private static ChronoUnit yearsMonthsDays(int length) {
        switch (length) {
            case 4:
                return ChronoUnit.YEARS;
            case 6:
                return ChronoUnit.MONTHS;
            case 8:
                return ChronoUnit.DAYS;
        }
        throw new IllegalArgumentException("length: " + length);
    }

    private static int lengthWithoutZone(String value) {
        int index = indexOfZone(value);
        return index < 0 ? value.length() : index;
    }

    private static int indexOfZone(String value) {
        int index = value.length() - 5;
        return index >= 4 && isSign(value.charAt(index)) ? index : -1;
    }

    private static boolean isSign(char ch) {
        return ch == '+' || ch == '-';
    }

    private static String truncate(String value, int length, int maxLength, int fractionPos) {
        return value.substring(0, adjustMaxLength(Math.min(length, maxLength), fractionPos));
    }

    private static int adjustMaxLength(int maxLength, int fractionPos) {
        return maxLength < fractionPos ? maxLength & ~1 : maxLength;
    }
}
