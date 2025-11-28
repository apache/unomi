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

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.LongSupplier;

import static org.junit.Assert.*;

/**
 * Tests for {@link DateMathParser} to ensure full compatibility with the semantics from
 * Elasticsearch/OpenSearch date math, confirming Unomi's internal implementation behaves as a
 * drop-in replacement without requiring the Elasticsearch dependency.
 */
public class DateMathParserTest {

    // Create the JavaDateFormatter with epoch millis support
    JavaDateFormatter formatter = new JavaDateFormatter("strict_date_optional_time||epoch_millis");

    // Round up formatter can be the same or similar:
    DateTimeFormatter roundUpFormatter = DateTimeFormatter.ISO_DATE_TIME;

    DateMathParser parser = new DateMathParser(formatter, roundUpFormatter);

    // A fixed "now" supplier returning a fixed timestamp: 2001-01-01T12:00:00Z in epoch millis
    private final LongSupplier fixedNow = () -> ZonedDateTime.of(2001, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();

    @Test
    public void testNowWithFixedSupplier() {
        Instant parsed = parser.parse("now", fixedNow, false, ZoneOffset.UTC);
        assertEquals("2001-01-01T12:00:00Z", parsed.toString());
    }

    @Test
    public void testNowPlus1HourWithFixedSupplier() {
        Instant parsed = parser.parse("now+1h", fixedNow, false, ZoneOffset.UTC);
        assertEquals("2001-01-01T13:00:00Z", parsed.toString());
    }

    @Test
    public void testNowMinus1HourWithFixedSupplier() {
        Instant parsed = parser.parse("now-1h", fixedNow, false, ZoneOffset.UTC);
        assertEquals("2001-01-01T11:00:00Z", parsed.toString());
    }

    @Test
    public void testRoundingWithRoundUpProperty() {
        // now = 2001-01-01T12:00:00Z
        // now/d with roundUp = true should round to the beginning of the day + 1 day -1 millisecond
        // Actually: rounding sets time to 2001-01-01T00:00:00Z, roundUp would move to 2001-01-02T00:00:00Z,
        // and subtract one millisecond → 2001-01-01T23:59:59.999Z
        Instant parsed = parser.parse("now/d", fixedNow, true, ZoneOffset.UTC);
        // Just before midnight of the next day
        assertEquals("2001-01-01T23:59:59.999Z", parsed.toString());
    }

    @Test
    public void testRoundingWithoutRoundUpProperty() {
        Instant parsed = parser.parse("now/d", fixedNow, false, ZoneOffset.UTC);
        // Rounding down to the start of the day: 2001-01-01T00:00:00Z
        assertEquals("2001-01-01T00:00:00Z", parsed.toString());
    }

    @Test
    public void testFixedDatePlusMonthAndRoundToDay() {
        // "2001-02-01||+1M/d"
        // start: 2001-02-01T00:00:00Z
        // +1M → 2001-03-01T00:00:00Z
        // /d rounds to start of day (same day)
        Instant parsed = parser.parse("2001-02-01||+1M/d", fixedNow, false, ZoneOffset.UTC);
        assertEquals("2001-03-01T00:00:00Z", parsed.toString());
    }

    @Test
    public void testInvalidUnit() {
        try {
            parser.parse("now+1X", fixedNow, false, ZoneOffset.UTC);
            fail("Expected an exception");
        } catch (DateMathParseException e) {
            // "Invalid unit: X"
            // Actually from code: "unit [X] not supported for date math [1X]"
            // The exact message:
            // "unit [X] not supported for date math [1X]" is expected
            // The code tries to parse "now+1X" → operator '+' recognized, num=1, unit='X'.
            // The mathString is "1X". We'll accept the default message.
            assertEquals("unit [X] not supported for date math [1X]", e.getMessage());
        }
    }

    @Test
    public void testInvalidOperator() {
        try {
            parser.parse("now*1d", fixedNow, false, ZoneOffset.UTC);
            fail("Expected an exception");
        } catch (DateMathParseException e) {
            // operator not supported
            assertEquals("operator not supported for date math [*1d]", e.getMessage());
        }
    }

    @Test
    public void testTruncatedMath() {
        try {
            parser.parse("now+", fixedNow, false, ZoneOffset.UTC);
            fail("Expected an exception");
        } catch (DateMathParseException e) {
            // truncated date math
            assertEquals("truncated date math [+]", e.getMessage());
        }
    }

    @Test
    public void testRoundSingleUnitOnly() {
        try {
            parser.parse("now/2d", fixedNow, false, ZoneOffset.UTC);
            fail("Expected an exception");
        } catch (DateMathParseException e) {
            // rounding `/` can only be used on single unit types
            assertEquals("rounding `/` can only be used on single unit types [/2d]", e.getMessage());
        }
    }

    @Test
    public void testTimeZoneAwareParsing() {
        // Parse a date with a different timezone
        // We'll pick a date in a different zone and ensure it adjusts if timeZone is provided
        Instant parsed = parser.parse("2001-01-01T12:00:00-02:00", fixedNow, false, ZoneOffset.UTC);
        // 12:00 at -02:00 is actually 14:00 UTC
        assertEquals("2001-01-01T14:00:00Z", parsed.toString());
    }

    @Test
    public void testNowWithMultipleOperationsAndRoundUp() {
        // now = 2001-01-01T12:00:00Z
        // now+1M-1d/d with roundUp = true
        // Steps:
        // +1M → 2001-02-01T12:00:00Z
        // -1d → 2001-01-31T12:00:00Z
        // /d with roundUp → round down: 2001-01-31T00:00:00Z plus 1 day = 2001-02-01T00:00:00Z minus 1 ms = 2001-01-31T23:59:59.999Z
        Instant parsed = parser.parse("now+1M-1d/d", fixedNow, true, ZoneOffset.UTC);
        assertEquals("2001-01-31T23:59:59.999Z", parsed.toString());
    }

    @Test
    public void testEmptyDate() {
        try {
            parser.parse("", fixedNow, false, ZoneOffset.UTC);
            fail("Expected an exception");
        } catch (DateMathParseException e) {
            assertEquals("cannot parse empty date", e.getMessage());
        }
    }

    @Test
    public void testInvalidExpression() {
        // Something like 2022-05-18||invalid
        try {
            parser.parse("2022-05-18||invalid", fixedNow, false, ZoneOffset.UTC);
            fail("Expected an exception");
        } catch (DateMathParseException e) {
            // operator not supported or truncated math. Actually, 'i' is not recognized as '+', '-', or '/'
            // This should fail as "operator not supported for date math [invalid]"
            assertEquals("operator not supported for date math [invalid]", e.getMessage());
        }
    }

    @Test
    public void testFailedToParseDate() {
        try {
            parser.parse("not-a-date", fixedNow, false, ZoneOffset.UTC);
            fail("Expected an exception");
        } catch (DateMathParseException e) {
            // "failed to parse date field [not-a-date] with format [strict_date_optional_time]: [Text 'not-a-date' could not be parsed...]"
            // Checking just the start of the message:
            assertTrue(e.getMessage().startsWith("failed to parse date field [not-a-date] with format"));
        }
    }

    @Test
    public void testInvalidLowercaseMathOperator() {
        try {
            parser.parse("now*1d", fixedNow, false, ZoneOffset.UTC); // Invalid operator
            fail("Expected an exception");
        } catch (DateMathParseException e) {
            assertEquals("operator not supported for date math [*1d]", e.getMessage());
        }
    }


    @Test
    public void testDateMathWithCaseInsensitiveParsing() {
        Instant parsed = parser.parse("2001-01-01t12:00:00z||+1d", fixedNow, false, ZoneOffset.UTC);
        assertEquals("2001-01-02T12:00:00Z", parsed.toString());

        parsed = parser.parse("now+1h/d", fixedNow, false, ZoneOffset.UTC);
        assertEquals("2001-01-01T00:00:00Z", parsed.toString());
    }

    @Test
    public void testMixedCaseDateMath() {
        Instant parsed = parser.parse("2001-01-01T12:00:00z||+1M/d", fixedNow, true, ZoneOffset.UTC); // Mixed case
        assertEquals("2001-02-01T23:59:59.999Z", parsed.toString());
    }

    @Test
    public void testInvalidMathWithCaseInsensitiveInput() {
        try {
            parser.parse("now*1d", fixedNow, false, ZoneOffset.UTC); // Invalid operator
            fail("Expected an exception");
        } catch (DateMathParseException e) {
            assertEquals("operator not supported for date math [*1d]", e.getMessage());
        }

        try {
            parser.parse("2001-01-01t12:00:00x||+1d", fixedNow, false, ZoneOffset.UTC); // Invalid separator
            fail("Expected an exception");
        } catch (DateMathParseException e) {
            assertTrue(e.getMessage().contains("failed to parse date field"));
        }
    }

    @Test
    public void testDateMathWithExtraSpaces() {
        Instant parsed = parser.parse("  2001-01-01T12:00:00Z  || +1d  ", fixedNow, false, ZoneOffset.UTC);
        assertEquals("2001-01-02T12:00:00Z", parsed.toString());
    }

}
