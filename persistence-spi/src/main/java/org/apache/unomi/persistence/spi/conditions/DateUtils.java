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
package org.apache.unomi.persistence.spi.conditions;

import org.apache.unomi.persistence.spi.conditions.datemath.DateMathParseException;
import org.apache.unomi.persistence.spi.conditions.datemath.DateMathParser;
import org.apache.unomi.persistence.spi.conditions.datemath.JavaDateFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Utility methods for working with dates in Unomi's persistence condition layer.
 * <p>
 * Provides a helper to convert various date representations (for example a
 * {@link java.util.Date} instance, an ISO-8601 timestamp, epoch milliseconds,
 * or an Elasticsearch/OpenSearch date math expression) into a {@link java.util.Date}
 * in UTC.
 * <p>
 * This class and the associated classes in the {@code datemath} and {@code geo} packages are
 * 100% compatible replacements for classes that used to be provided by Elasticsearch. They were
 * introduced to remove the direct dependency on Elasticsearch after it stopped exposing those
 * utility classes. Keeping them here ensures backward-compatible behavior across our persistence
 * implementations, including OpenSearch.
 * <p>
 * <strong>Backward Compatibility:</strong> This implementation is designed to work with datasets
 * migrated from older versions of Unomi and Elasticsearch. It supports:
 * <ul>
 *   <li>Standard Elasticsearch date formats: {@code strict_date_optional_time} and {@code epoch_millis}</li>
 *   <li>Legacy formats: {@code date_optional_time} (non-strict, more lenient parsing)</li>
 *   <li>Epoch timestamps: both milliseconds and seconds</li>
 *   <li>Case-insensitive parsing: handles lowercase 't' and 'z' in ISO dates</li>
 *   <li>Modern Java date/time types: {@code Instant}, {@code OffsetDateTime}, {@code ZonedDateTime}, {@code LocalDateTime}</li>
 * </ul>
 * <p>
 * This class is stateless and thread-safe.
 */
public class DateUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DateUtils.class.getName());

    /**
     * Resolves the provided value to a {@link Date}.
     * <ul>
     *     <li>If the value is {@code null}, returns {@code null}.</li>
     *     <li>If the value is already a {@link Date}, returns it unchanged.</li>
     *     <li>If the value is an {@link Instant}, converts it to {@link Date}.</li>
     *     <li>If the value is an {@link OffsetDateTime}, converts it to {@link Date}.</li>
     *     <li>If the value is a {@link ZonedDateTime}, converts it to {@link Date}.</li>
     *     <li>If the value is a {@link LocalDateTime}, converts it to {@link Date} (using system default timezone).</li>
     *     <li>Otherwise, attempts to parse the value as a string using a date math parser
     *     that supports {@code strict_date_optional_time}, {@code epoch_millis}, ISO-8601
     *     formats, and Elasticsearch/OpenSearch date math expressions, all evaluated in UTC.</li>
     * </ul>
     *
     * @param value a date-like value (may be a {@link Date}, modern date/time type, or a parseable {@link String})
     * @return a {@link Date} if parsing succeeds; {@code null} otherwise
     */
    public static Date getDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        // Handle modern Java date/time API types
        if (value instanceof Instant) {
            return Date.from((Instant) value);
        }
        if (value instanceof OffsetDateTime) {
            return Date.from(((OffsetDateTime) value).toInstant());
        }
        if (value instanceof ZonedDateTime) {
            return Date.from(((ZonedDateTime) value).toInstant());
        }
        if (value instanceof LocalDateTime) {
            return Date.from(((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant());
        }
        // Fall back to string parsing for other types
        // Use the same format pattern as Elasticsearch/OpenSearch default date format
        // This supports: strict_date_optional_time (ISO-8601) and epoch_millis
        // For backward compatibility with migrated datasets, we also support:
        // - date_optional_time (non-strict, more lenient - used in older Elasticsearch versions)
        // - epoch_second (for timestamps stored as seconds instead of milliseconds)
        String dateString = value.toString();
        JavaDateFormatter formatter = new JavaDateFormatter("strict_date_optional_time||epoch_millis");
        DateMathParser dateMathParser = new DateMathParser(formatter, DateTimeFormatter.ISO_DATE_TIME);
        try {
            // Parse in UTC to match Elasticsearch/OpenSearch behavior
            Instant instant = dateMathParser.parse(dateString, System::currentTimeMillis, false, ZoneOffset.UTC);
            return Date.from(instant);
        } catch (DateMathParseException e) {
            // Fallback for legacy formats that might exist in migrated datasets
            // Try with more lenient date_optional_time format (non-strict)
            try {
                JavaDateFormatter fallbackFormatter = new JavaDateFormatter("date_optional_time||epoch_millis||epoch_second");
                DateMathParser fallbackParser = new DateMathParser(fallbackFormatter, DateTimeFormatter.ISO_DATE_TIME);
                Instant instant = fallbackParser.parse(dateString, System::currentTimeMillis, false, ZoneOffset.UTC);
                LOGGER.debug("Successfully parsed date using fallback formatter: {}", dateString);
                return Date.from(instant);
            } catch (DateMathParseException e2) {
                LOGGER.warn("unable to parse date with primary and fallback formatters. See debug log level for full stacktrace");
                LOGGER.warn("unable to parse date {} - primary error: {}, fallback error: {}", dateString, e.getMessage(), e2.getMessage());
            }
        }
        return null;
    }
}
