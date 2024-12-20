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
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalQueries;
import java.util.function.Function;
import java.util.function.LongSupplier;

public class DateMathParser {

    public static boolean isNullOrEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public static class DateFormatters {
        public static ZonedDateTime from(TemporalAccessor accessor) {
            // Default to UTC if no zone or offset is present
            ZoneId zone = accessor.query(TemporalQueries.zone());
            if (zone == null) {
                ZoneOffset offset = accessor.query(TemporalQueries.offset());
                zone = (offset != null) ? offset : ZoneOffset.UTC;
            }

            // If the accessor supports INSTANT_SECONDS, construct from Instant
            if (accessor.isSupported(ChronoField.INSTANT_SECONDS)) {
                return ZonedDateTime.ofInstant(Instant.from(accessor), zone);
            }

            // Handle LocalDate and LocalTime explicitly
            LocalDate date = accessor.query(TemporalQueries.localDate());
            LocalTime time = accessor.query(TemporalQueries.localTime());

            // Ensure missing components are handled gracefully
            if (date == null && time == null) {
                throw new DateTimeException("Cannot extract LocalDate or LocalTime from TemporalAccessor");
            }

            if (date == null) {
                date = LocalDate.ofEpochDay(0); // Default to 1970-01-01
            }

            if (time == null) {
                time = LocalTime.MIDNIGHT; // Default to 00:00
            }

            // Combine LocalDate and LocalTime with ZoneId
            return ZonedDateTime.of(date, time, zone);
        }
    }

    private final JavaDateFormatter formatter;
    private final DateTimeFormatter roundUpFormatter;

    public DateMathParser(JavaDateFormatter formatter, DateTimeFormatter roundUpFormatter) {
        this.formatter = formatter;
        this.roundUpFormatter = roundUpFormatter;
    }

    public Instant parse(String text, LongSupplier now, boolean roundUpProperty, ZoneId timeZone) {
        Instant time;
        String mathString;
        if (text.startsWith("now")) {
            try {
                time = Instant.ofEpochMilli(now.getAsLong());
            } catch (Exception e) {
                throw new DateMathParseException("could not read the current timestamp", e);
            }
            mathString = text.substring("now".length());
        } else {
            int index = text.indexOf("||");
            if (index == -1) {
                // no math, just parse date
                return parseDateTime(text, timeZone, roundUpProperty);
            }
            time = parseDateTime(text.substring(0, index), timeZone, false);
            mathString = text.substring(index + 2);
        }

        return parseMath(mathString, time, roundUpProperty, timeZone);
    }

    private Instant parseMath(final String mathString, final Instant time, final boolean roundUpProperty,
                              ZoneId timeZone) throws DateMathParseException {
        if (timeZone == null) {
            timeZone = ZoneOffset.UTC;
        }
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(time, timeZone);
        int i = 0;
        while (i < mathString.length()) {
            char c = mathString.charAt(i++);
            final boolean round;
            final int sign;
            if (c == '/') {
                round = true;
                sign = 1;
            } else {
                round = false;
                if (c == '+') {
                    sign = 1;
                } else if (c == '-') {
                    sign = -1;
                } else {
                    throw new DateMathParseException("operator not supported for date math [%s]", mathString);
                }
            }

            if (i >= mathString.length()) {
                throw new DateMathParseException("truncated date math [%s]", mathString);
            }

            final int num;
            int numStart = i;
            if (!Character.isDigit(mathString.charAt(i))) {
                num = 1;
            } else {
                while (i < mathString.length() && Character.isDigit(mathString.charAt(i))) {
                    i++;
                }
                if (i >= mathString.length()) {
                    throw new DateMathParseException("truncated date math [%s]", mathString);
                }
                num = Integer.parseInt(mathString.substring(numStart, i));
            }
            if (round && num != 1) {
                throw new DateMathParseException("rounding `/` can only be used on single unit types [%s]", mathString);
            }
            char unit = mathString.charAt(i++);
            switch (unit) {
                case 'y':
                    if (round) {
                        dateTime = dateTime.withDayOfYear(1).with(LocalTime.MIN);
                        if (roundUpProperty) {
                            dateTime = dateTime.plusYears(1);
                        }
                    } else {
                        dateTime = dateTime.plusYears(sign * num);
                    }
                    break;
                case 'M':
                    if (round) {
                        dateTime = dateTime.withDayOfMonth(1).with(LocalTime.MIN);
                        if (roundUpProperty) {
                            dateTime = dateTime.plusMonths(1);
                        }
                    } else {
                        dateTime = dateTime.plusMonths(sign * num);
                    }
                    break;
                case 'w':
                    if (round) {
                        dateTime = dateTime.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).with(LocalTime.MIN);
                        if (roundUpProperty) {
                            dateTime = dateTime.plusWeeks(1);
                        }
                    } else {
                        dateTime = dateTime.plusWeeks(sign * num);
                    }
                    break;
                case 'd':
                    if (round) {
                        dateTime = dateTime.with(LocalTime.MIN);
                        if (roundUpProperty) {
                            dateTime = dateTime.plusDays(1);
                        }
                    } else {
                        dateTime = dateTime.plusDays(sign * num);
                    }
                    break;
                case 'h':
                case 'H':
                    if (round) {
                        dateTime = dateTime.withMinute(0).withSecond(0).withNano(0);
                        if (roundUpProperty) {
                            dateTime = dateTime.plusHours(1);
                        }
                    } else {
                        dateTime = dateTime.plusHours(sign * num);
                    }
                    break;
                case 'm':
                    if (round) {
                        dateTime = dateTime.withSecond(0).withNano(0);
                        if (roundUpProperty) {
                            dateTime = dateTime.plusMinutes(1);
                        }
                    } else {
                        dateTime = dateTime.plusMinutes(sign * num);
                    }
                    break;
                case 's':
                    if (round) {
                        dateTime = dateTime.withNano(0);
                        if (roundUpProperty) {
                            dateTime = dateTime.plusSeconds(1);
                        }
                    } else {
                        dateTime = dateTime.plusSeconds(sign * num);
                    }
                    break;
                default:
                    // Adjust error message to remove the operator sign from the substring
                    // We know substring from numStart to current i is the "1X" part
                    // Operator was c, num was parsed, unit is unit
                    String unitString = mathString.substring(numStart, numStart + Integer.toString(num).length()) + unit;
                    throw new DateMathParseException("unit [%s] not supported for date math [%s]", unit, unitString);
            }
            if (round && roundUpProperty) {
                // subtract 1 millisecond
                dateTime = dateTime.minus(1, ChronoField.MILLI_OF_SECOND.getBaseUnit());
            }
        }
        return dateTime.toInstant();
    }

    private Instant parseDateTime(String value, ZoneId timeZone, boolean roundUpIfNoTime) {
        if (isNullOrEmpty(value)) {
            throw new DateMathParseException("cannot parse empty date");
        }

        Function<String, TemporalAccessor> parser = roundUpIfNoTime ? roundUpFormatter::parse : formatter::parse;
        try {
            TemporalAccessor accessor = parser.apply(value);

            // Convert to ZonedDateTime from accessor
            ZonedDateTime zdt = DateFormatters.from(accessor);
            if (timeZone != null) {
                // Convert to the same instant in the given timeZone
                zdt = zdt.withZoneSameInstant(timeZone);
            }
            return zdt.toInstant();
        } catch (Throwable t) {
            throw new DateMathParseException(
                    "failed to parse date field [%s] with format [%s]: [%s]",
                    value, formatter, t.getMessage());
        }
    }

}
