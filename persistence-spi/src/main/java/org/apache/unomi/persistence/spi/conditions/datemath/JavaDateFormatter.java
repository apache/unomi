/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.unomi.persistence.spi.conditions.datemath;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.ArrayList;
import java.util.List;

public class JavaDateFormatter {
    private final List<FormatDefinition> formats;
    private final boolean allowEpochMillis;
    private final boolean allowEpochSecond;

    public JavaDateFormatter(String formatString) {
        this.formats = new ArrayList<>();
        boolean epochMillis = false;
        boolean epochSecond = false;

        String[] formatParts = formatString.split("\\|\\|");
        for (String f : formatParts) {
            f = f.trim();
            if (f.equals("epoch_millis")) {
                formats.add(new FormatDefinition("epoch_millis", f, null));
                epochMillis = true;
            } else if (f.equals("epoch_second")) {
                formats.add(new FormatDefinition("epoch_second", f, null));
                epochSecond = true;
            } else {
                formats.add(createFormatDefinition(f));
            }
        }

        this.allowEpochMillis = epochMillis;
        this.allowEpochSecond = epochSecond;
    }

    public TemporalAccessor parse(String input) {
        // Numeric check
        if (isNumeric(input)) {
            long value = Long.parseLong(input);
            if (allowEpochMillis && containsFormatName("epoch_millis")) {
                return Instant.ofEpochMilli(value);
            }
            if (allowEpochSecond && containsFormatName("epoch_second")) {
                return Instant.ofEpochSecond(value);
            }
        }

        for (FormatDefinition def : formats) {
            try {
                String adjusted = adjustForPattern(input, def.pattern);
                TemporalAccessor ta = def.formatter.parse(adjusted);
                return toInstant(ta, def.formatter.getZone() != null ? def.formatter.getZone() : ZoneOffset.UTC);
            } catch (Throwable t) {
                // try next
            }
        }

        throw new DateMathParseException("failed to parse date field [" + input + "] with provided formats");
    }

    private Instant toInstant(TemporalAccessor ta, ZoneId zone) {
        boolean hasYear = ta.isSupported(ChronoField.YEAR);
        boolean hasMonth = ta.isSupported(ChronoField.MONTH_OF_YEAR);
        boolean hasDay = ta.isSupported(ChronoField.DAY_OF_MONTH);
        boolean hasDoY = ta.isSupported(ChronoField.DAY_OF_YEAR);
        boolean hasHour = ta.isSupported(ChronoField.HOUR_OF_DAY);
        boolean hasMinute = ta.isSupported(ChronoField.MINUTE_OF_HOUR);
        boolean hasSecond = ta.isSupported(ChronoField.SECOND_OF_MINUTE);

        LocalDate date;
        if (hasYear && hasMonth && hasDay) {
            // Normal date
            date = LocalDate.from(ta);
        } else if (hasYear && hasDoY) {
            // Ordinal date: year + dayOfYear
            int year = ta.get(ChronoField.YEAR);
            int dayOfYear = ta.get(ChronoField.DAY_OF_YEAR);
            date = LocalDate.ofYearDay(year, dayOfYear);
        } else if (!hasYear && (hasHour || hasMinute || hasSecond)) {
            // Time only → 1970-01-01
            date = LocalDate.ofEpochDay(0);
        } else if (hasYear && !hasMonth && !hasDay && !hasDoY) {
            // Year only → yyyy means yyyy-01-01
            int year = ta.get(ChronoField.YEAR);
            date = LocalDate.of(year, 1, 1);
        } else {
            // Maybe week-based fields or partial fields:
            // Attempt ZonedDateTime directly if possible
            try {
                return ZonedDateTime.from(ta).toInstant();
            } catch (DateTimeException e) {
                // If fails, handle week-based or incomplete dates
                if (ta.isSupported(ChronoField.YEAR_OF_ERA)) {
                    throw new DateMathParseException("Week-based date formats need additional logic.");
                }
                // Default to epoch day
                date = LocalDate.ofEpochDay(0);
            }
        }

        int hour = hasHour ? ta.get(ChronoField.HOUR_OF_DAY) : 0;
        int minute = hasMinute ? ta.get(ChronoField.MINUTE_OF_HOUR) : 0;
        int second = hasSecond ? ta.get(ChronoField.SECOND_OF_MINUTE) : 0;
        int nano = ta.isSupported(ChronoField.NANO_OF_SECOND) ? ta.get(ChronoField.NANO_OF_SECOND) : 0;

        LocalTime time = LocalTime.of(hour, minute, second, nano);

        // Handle zone and offset explicitly
        ZoneOffset offset = ta.query(TemporalQueries.offset());
        if (offset != null) {
            return ZonedDateTime.of(date, time, offset).toInstant();
        }

        // Fall back to provided zone
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    private String adjustForPattern(String input, String pattern) {
        // If pattern is strict_date_* and only date is given, append midnight
        if (pattern.contains("strict_date") && input.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return input + "T00:00:00Z";
        }
        return input;
    }

    private boolean isNumeric(String s) {
        if (s.isEmpty()) return false;
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }

    private boolean containsFormatName(String name) {
        return formats.stream().anyMatch(def -> def.name.equals(name));
    }

    private FormatDefinition createFormatDefinition(String f) {
        // Known patterns from documentation:
        // We'll define exact patterns for each built-in format:
        switch (f) {
            // Already handled: epoch_millis, epoch_second
            case "strict_date_optional_time":
            case "date_optional_time":
                // yyyy-MM-dd or yyyy-MM-dd'T'HH:mm:ss.SSSX
                return fmt(f, "yyyy-MM-dd['T'HH:mm:ss[.SSS][XXX]]");

            case "strict_date_optional_time_nanos":
                // Nanosecond resolution: allow up to 9 fractional digits: .SSSSSSSSS
                return fmt(f, "yyyy-MM-dd['T'HH:mm:ss[.SSSSSSSSS]][X]");

            case "basic_date":
                return fmt(f, "yyyyMMdd");
            case "basic_date_time":
                return fmt(f, "yyyyMMdd'T'HHmmss.SSSX");
            case "basic_date_time_no_millis":
                return fmt(f, "yyyyMMdd'T'HHmmssX");
            case "basic_ordinal_date":
                return fmt(f, "yyyyDDD");
            case "basic_ordinal_date_time":
                return fmt(f, "yyyyDDD'T'HHmmss.SSSX");
            case "basic_ordinal_date_time_no_millis":
                return fmt(f, "yyyyDDD'T'HHmmssX");
            case "basic_time":
                return fmt(f, "HHmmss.SSSX");
            case "basic_time_no_millis":
                return fmt(f, "HHmmssX");
            case "basic_t_time":
                return fmt(f, "'T'HHmmss.SSSX");
            case "basic_t_time_no_millis":
                return fmt(f, "'T'HHmmssX");

            // Week-based formats:
            // Week dates require 'YYYY' for weekyear, 'ww' for week of year, and 'e' for day of week.
            // Example: basic_week_date: xxxx'W'wwe
            // We'll assume ISO week date parsing works with pattern:
            case "basic_week_date":
            case "strict_basic_week_date":
                return fmt(f, "xxxx'W'wwe");
            case "basic_week_date_time":
            case "strict_basic_week_date_time":
                return fmt(f, "xxxx'W'wwe'T'HHmmss.SSSX");
            case "basic_week_date_time_no_millis":
            case "strict_basic_week_date_time_no_millis":
                return fmt(f, "xxxx'W'wwe'T'HHmmssX");

            case "date":
            case "strict_date":
                return fmt(f, "yyyy-MM-dd");
            case "date_hour":
            case "strict_date_hour":
                return fmt(f, "yyyy-MM-dd'T'HH");
            case "date_hour_minute":
            case "strict_date_hour_minute":
                return fmt(f, "yyyy-MM-dd'T'HH:mm");
            case "date_hour_minute_second":
            case "strict_date_hour_minute_second":
                return fmt(f, "yyyy-MM-dd'T'HH:mm:ss");
            case "date_hour_minute_second_fraction":
            case "strict_date_hour_minute_second_fraction":
                return fmt(f, "yyyy-MM-dd'T'HH:mm:ss.SSS");
            case "date_hour_minute_second_millis":
            case "strict_date_hour_minute_second_millis":
                // same as fraction?
                return fmt(f, "yyyy-MM-dd'T'HH:mm:ss.SSS");
            case "date_time":
            case "strict_date_time":
                return fmt(f, "yyyy-MM-dd'T'HH:mm:ss.SSSX");
            case "date_time_no_millis":
            case "strict_date_time_no_millis":
                return fmt(f, "yyyy-MM-dd'T'HH:mm:ssXXX");

            case "hour":
            case "strict_hour":
                return fmt(f, "HH");
            case "hour_minute":
            case "strict_hour_minute":
                return fmt(f, "HH:mm");
            case "hour_minute_second":
            case "strict_hour_minute_second":
                return fmt(f, "HH:mm:ss");
            case "hour_minute_second_fraction":
            case "strict_hour_minute_second_fraction":
                return fmt(f, "HH:mm:ss.SSS");
            case "hour_minute_second_millis":
            case "strict_hour_minute_second_millis":
                return fmt(f, "HH:mm:ss.SSS");
            case "ordinal_date":
            case "strict_ordinal_date":
                return fmt(f, "yyyy-DDD");
            case "ordinal_date_time":
            case "strict_ordinal_date_time":
                return fmt(f, "yyyy-DDD'T'HH:mm:ss.SSSX");
            case "ordinal_date_time_no_millis":
            case "strict_ordinal_date_time_no_millis":
                return fmt(f, "yyyy-DDD'T'HH:mm:ssX");
            case "time":
            case "strict_time":
                return fmt(f, "HH:mm:ss.SSSX");
            case "time_no_millis":
            case "strict_time_no_millis":
                return fmt(f, "HH:mm:ssX");
            case "t_time":
            case "strict_t_time":
                return fmt(f, "'T'HH:mm:ss.SSSX");
            case "t_time_no_millis":
            case "strict_t_time_no_millis":
                return fmt(f, "'T'HH:mm:ssX");
            case "week_date":
            case "strict_week_date":
                return fmt(f, "YYYY-'W'ww-e");
            case "week_date_time":
            case "strict_week_date_time":
                return fmt(f, "YYYY-'W'ww-e'T'HH:mm:ss.SSSX");
            case "week_date_time_no_millis":
            case "strict_week_date_time_no_millis":
                return fmt(f, "YYYY-'W'ww-e'T'HH:mm:ssX");
            case "weekyear":
            case "strict_weekyear":
                return fmt(f, "YYYY");
            case "weekyear_week":
            case "strict_weekyear_week":
                return fmt(f, "YYYY-'W'ww");
            case "weekyear_week_day":
            case "strict_weekyear_week_day":
                return fmt(f, "YYYY-'W'ww-e");
            case "year":
            case "strict_year":
                return fmt(f, "yyyy");
            case "year_month":
            case "strict_year_month":
                return fmt(f, "yyyy-MM");
            case "year_month_day":
            case "strict_year_month_day":
                return fmt(f, "yyyy-MM-dd");

            default:
                // Custom pattern
                return fmt(f, f);
        }
    }

    private FormatDefinition fmt(String name, String pattern) {
        // Apply UTC zone to all and consider using strict resolver if needed
        DateTimeFormatter dtf = new DateTimeFormatterBuilder()
                .appendPattern(pattern)
                .toFormatter()
                .withZone(ZoneOffset.UTC);
        return new FormatDefinition(name, pattern, dtf);
    }

    public static class FormatDefinition {
        final String name;
        final String pattern;
        final DateTimeFormatter formatter;

        public FormatDefinition(String name, String pattern, DateTimeFormatter formatter) {
            this.name = name;
            this.pattern = pattern;
            this.formatter = formatter;
        }

        @Override
        public String toString() {
            return "FormatDefinition{" +
                    "name='" + name + '\'' +
                    ", pattern='" + pattern + '\'' +
                    '}';
        }
    }


    @Override
    public String toString() {
        return "JavaDateFormatter{" +
                "formats=" + formats +
                '}';
    }
}
