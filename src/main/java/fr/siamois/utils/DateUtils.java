package fr.siamois.utils;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Utility class to format {@link OffsetDateTime} to a string representation.
 * The format used is `YYYY-MM-DD HH:MM` with the system time zone.
 *
 * @author Julien Linget
 */
@Slf4j
public class DateUtils {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DateUtils() {
    }

    /**
     * offsetDateTime to {@link String} representation with format `YYYY-MM-DD HH:MM` with the system time zone.
     *
     * @param offsetDateTime The {@link OffsetDateTime} to convert
     * @return The string representation of the offsetDateTime at the system time zone
     */
    public static String formatOffsetDateTime(OffsetDateTime offsetDateTime) {
        String msg = invalidMessageIfNull(offsetDateTime);
        if (msg != null)
            return msg;
        LocalDateTime dateTime = offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        return formatter.format(dateTime);
    }

    /**
     * Formats the given OffsetDateTime to a string in UTC timezone.
     *
     * @param dateTime the OffsetDateTime to format
     * @param showTime do we show the time?
     * @return the formatted date string, or an empty string if dateTime is null
     */
    public static String formatUtcDateTime(OffsetDateTime dateTime, boolean showTime) {
        if (dateTime == null) return "";
        String pattern = "dd/MM/yyyy HH:mm";
        if(!showTime) {
            pattern = "dd/MM/yyyy";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern).withZone(ZoneOffset.UTC);
        return formatter.format(dateTime);
    }

    /**
     * Formats the given OffsetDateTime to a string in UTC timezone.
     *
     * @param dateTime the OffsetDateTime to format
     * @return the formatted date string, or an empty string if dateTime is null
     */
    public static String formatUtcDateTime(OffsetDateTime dateTime) {
        return formatUtcDateTime(dateTime, false);
    }



    /**
     * offsetDateTime to {@link String} representation with format `YYYY-MM-DD HH:MM` with the specified time zone.
     *
     * @param offsetDateTime The {@link OffsetDateTime} to convert
     * @param zoneId         The {@link ZoneId} to use for conversion
     * @return The string representation of the offsetDateTime at the specified time zone
     */
    public static String formatOffsetDateTime(OffsetDateTime offsetDateTime, ZoneId zoneId) {
        String msg = invalidMessageIfNull(offsetDateTime);
        if (msg != null)
            return msg;
        LocalDateTime dateTime = offsetDateTime.atZoneSameInstant(zoneId).toLocalDateTime();
        return formatter.format(dateTime);
    }

    private static String invalidMessageIfNull(OffsetDateTime obj) {
        if (obj == null) {
            log.error("Invalid offsetDatetime");
            return "INVALID DATE";
        }
        return null;
    }

}
