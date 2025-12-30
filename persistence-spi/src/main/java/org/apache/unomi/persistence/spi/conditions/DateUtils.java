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
import java.time.ZoneOffset;
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
 * This class is stateless and thread-safe.
 */
public class DateUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DateUtils.class.getName());

    /**
     * Resolves the provided value to a {@link Date}.
     * <ul>
     *     <li>If the value is {@code null}, returns {@code null}.</li>
     *     <li>If the value is already a {@link Date}, returns it unchanged.</li>
     *     <li>Otherwise, attempts to parse the value as a string using a date math parser
     *     that supports {@code strict_date_optional_time}, {@code epoch_millis}, ISO-8601
     *     formats, and Elasticsearch/OpenSearch date math expressions, all evaluated in UTC.</li>
     * </ul>
     *
     * @param value a date-like value (may be a {@link Date} or a parseable {@link String})
     * @return a {@link Date} if parsing succeeds; {@code null} otherwise
     */
    public static Date getDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        } else {
            JavaDateFormatter formatter = new JavaDateFormatter("strict_date_optional_time||epoch_millis");
            DateMathParser dateMathParser = new DateMathParser(formatter, DateTimeFormatter.ISO_DATE_TIME);
            try {
                Instant instant = dateMathParser.parse(value.toString(), System::currentTimeMillis, false, ZoneOffset.UTC);
                return Date.from(instant);
            } catch (DateMathParseException e) {
                LOGGER.warn("unable to parse date. See debug log level for full stacktrace");
                LOGGER.warn("unable to parse date {}", value, e);
            }
            return null;
        }
    }
}
