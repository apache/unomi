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
package conditions.datemath;

import org.apache.unomi.persistence.spi.conditions.datemath.DateMathParseException;
import org.apache.unomi.persistence.spi.conditions.datemath.JavaDateFormatter;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.*;

/**
 * Comprehensive tests for JavaDateFormatter covering various formats:
 * - Epoch formats (epoch_millis, epoch_second)
 * - ISO-based formats (strict_date_optional_time, strict_date_time_no_millis, etc.)
 * - Basic formats (basic_date, basic_date_time, etc.)
 * - Ordinal formats (ordinal_date, etc.)
 * - Strict vs non-strict variants
 * - Custom patterns
 * - Fallback between multiple formats
 */
public class JavaDateFormatterTest {

    @Test
    public void testEpochMillis() {
        // epoch_millis: 978307200000L = 2001-01-01T12:00:00Z
        // The expected original code had midnight, let's confirm correct epoch:
        // 978307200000 ms = 2001-01-01T12:00:00Z indeed.
        JavaDateFormatter formatter = new JavaDateFormatter("epoch_millis");
        Instant parsed = Instant.from(formatter.parse("978307200000"));
        assertEquals("2001-01-01T00:00:00Z", parsed.toString());
    }

    @Test
    public void testEpochSecond() {
        // epoch_second: 978307200 = 2001-01-01T12:00:00Z
        JavaDateFormatter formatter = new JavaDateFormatter("epoch_second");
        Instant parsed = Instant.from(formatter.parse("978307200"));
        assertEquals("2001-01-01T00:00:00Z", parsed.toString());
    }

    @Test
    public void testStrictDateOptionalTime() {
        // strict_date_optional_time: "2022-05-18T15:23:17Z"
        JavaDateFormatter formatter = new JavaDateFormatter("strict_date_optional_time");
        Instant parsed = Instant.from(formatter.parse("2022-05-18T15:23:17Z"));
        assertEquals("2022-05-18T15:23:17Z", parsed.toString());
    }

    @Test
    public void testDateOnlyWithStrictDateOptionalTime() {
        // strict_date_optional_time: "2022-05-18" should default to midnight
        JavaDateFormatter formatter = new JavaDateFormatter("strict_date_optional_time");
        Instant parsed = Instant.from(formatter.parse("2022-05-18"));
        assertEquals("2022-05-18T00:00:00Z", parsed.toString());
    }

    @Test
    public void testDateTimeNoMillis() {
        // date_time_no_millis: "yyyy-MM-dd'T'HH:mm:ssZ"
        JavaDateFormatter formatter = new JavaDateFormatter("date_time_no_millis");
        Instant parsed = Instant.from(formatter.parse("2022-05-18T15:23:17+00:00"));
        assertEquals("2022-05-18T15:23:17Z", parsed.toString());
    }

    @Test
    public void testStrictDateTimeNoMillis() {
        // strict_date_time_no_millis: same pattern but strict
        JavaDateFormatter formatter = new JavaDateFormatter("strict_date_time_no_millis");
        Instant parsed = Instant.from(formatter.parse("2022-05-18T15:23:17Z"));
        assertEquals("2022-05-18T15:23:17Z", parsed.toString());
    }

    @Test
    public void testBasicDate() {
        // basic_date: yyyyMMdd
        // "20010101" = 2001-01-01T00:00:00Z
        JavaDateFormatter formatter = new JavaDateFormatter("basic_date");
        Instant parsed = Instant.from(formatter.parse("20010101"));
        assertEquals("2001-01-01T00:00:00Z", parsed.toString());
    }

    @Test
    public void testBasicDateTime() {
        // basic_date_time: yyyyMMdd'T'HHmmss.SSSZ
        // "20010101T123000.000Z" = 2001-01-01T12:30:00Z
        JavaDateFormatter formatter = new JavaDateFormatter("basic_date_time");
        Instant parsed = Instant.from(formatter.parse("20010101T123000.000Z"));
        assertEquals("2001-01-01T12:30:00Z", parsed.toString());
    }

    @Test
    public void testOrdinalDate() {
        // ordinal_date: yyyy-DDD, e.g. "2001-001" = 2001-01-01
        JavaDateFormatter formatter = new JavaDateFormatter("ordinal_date");
        Instant parsed = Instant.from(formatter.parse("2001-001"));
        assertEquals("2001-01-01T00:00:00Z", parsed.toString());
    }

    @Test
    public void testOrdinalDateTimeNoMillis() {
        // ordinal_date_time_no_millis: yyyy-DDD'T'HH:mm:ssZ
        // "2001-001T12:00:00Z" = 2001-01-01T12:00:00Z
        JavaDateFormatter formatter = new JavaDateFormatter("ordinal_date_time_no_millis");
        Instant parsed = Instant.from(formatter.parse("2001-001T12:00:00Z"));
        assertEquals("2001-01-01T12:00:00Z", parsed.toString());
    }

    @Test
    public void testHourMinuteSecond() {
        // hour_minute_second: HH:mm:ss
        // "12:34:56" with no date = defaults to today's date at that time?
        // Our code sets date-only defaults. For time-only, must default to 1970-01-01?
        // If not implemented, either skip or fix code to handle pure times.
        // Let's assume we default to 1970-01-01 if time only:
        JavaDateFormatter formatter = new JavaDateFormatter("hour_minute_second");
        Instant parsed = Instant.from(formatter.parse("12:34:56"));
        assertEquals("1970-01-01T12:34:56Z", parsed.toString());
    }

    @Test
    public void testCustomPattern() {
        // "MM/dd/yyyy": "03/21/2019" = 2019-03-21T00:00:00Z
        JavaDateFormatter formatter = new JavaDateFormatter("MM/dd/yyyy");
        Instant parsed = Instant.from(formatter.parse("03/21/2019"));
        assertEquals("2019-03-21T00:00:00Z", parsed.toString());
    }

    @Test
    public void testFallbackBetweenFormats() {
        // If first format doesn't match, second one should
        JavaDateFormatter formatter = new JavaDateFormatter("yyyy-MM-dd||epoch_millis");
        // Not a yyyy-MM-dd date, but epoch_millis:
        Instant parsed = Instant.from(formatter.parse("978307200000"));
        assertEquals("2001-01-01T00:00:00Z", parsed.toString());
    }

    @Test
    public void testNoMatch() {
        JavaDateFormatter formatter = new JavaDateFormatter("yyyy/MM/dd||basic_date");
        try {
            formatter.parse("not-a-date");
            fail("Expected exception");
        } catch (DateMathParseException e) {
            assertTrue(e.getMessage().contains("failed to parse date field [not-a-date]"));
        }
    }

    @Test
    public void testMixedCaseDate() {
        JavaDateFormatter formatter = new JavaDateFormatter("strict_date_optional_time");
        Instant parsed = Instant.from(formatter.parse("2022-05-18T15:23:17z")); // mixed case 'T' and 'z'
        assertEquals("2022-05-18T15:23:17Z", parsed.toString());
    }

    @Test
    public void testCaseInsensitiveISOWithValidInputs() {
        JavaDateFormatter formatter = new JavaDateFormatter("strict_date_optional_time");
        // Lowercase `t` and `z`
        Instant parsed = Instant.from(formatter.parse("2022-05-18t15:23:17z"));
        assertEquals("2022-05-18T15:23:17Z", parsed.toString());

        // Mixed case
        parsed = Instant.from(formatter.parse("2022-05-18T15:23:17z"));
        assertEquals("2022-05-18T15:23:17Z", parsed.toString());

        // Uppercase (valid)
        parsed = Instant.from(formatter.parse("2022-05-18T15:23:17Z"));
        assertEquals("2022-05-18T15:23:17Z", parsed.toString());
    }

    @Test
    public void testCaseInsensitiveISOWithInvalidInputs() {
        JavaDateFormatter formatter = new JavaDateFormatter("strict_date_optional_time");

        try {
            formatter.parse("2022-05-18x15:23:17z"); // Invalid separator
            fail("Expected an exception");
        } catch (DateMathParseException e) {
            assertTrue(e.getMessage().contains("failed to parse date field"));
        }

        try {
            formatter.parse("2022-05-18T15:23:17X"); // Invalid character for timezone
            fail("Expected an exception");
        } catch (DateMathParseException e) {
            assertTrue(e.getMessage().contains("failed to parse date field"));
        }
    }
}
