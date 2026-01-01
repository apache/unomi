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
 * limitations under the License
 */
package org.apache.unomi.itests.tools;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Comprehensive unit tests for LogChecker substring matching functionality.
 * Tests validate the hierarchical prefix-based matching algorithm, multi-part substring matching,
 * edge cases, and performance characteristics.
 */
public class LogCheckerTest {

    private LogChecker logChecker;

    @Before
    public void setUp() {
        logChecker = LogChecker.builder()
            .withErrorContext(0, 0)
            .withWarningContext(0, 0)
            .build();
    }

    @Test
    public void testSingleSubstringMatch() {
        logChecker.addIgnoredSubstring("error occurred");

        assertFalse("Should ignore message with substring", shouldInclude("An error occurred in the system"));
        assertTrue("Should include message without substring", shouldInclude("This is a normal log message"));
    }

    @Test
    public void testSingleSubstringCaseInsensitive() {
        logChecker.addIgnoredSubstring("ERROR OCCURRED");

        assertFalse("Should match case-insensitively", shouldInclude("An error occurred in the system"));
        assertFalse("Should match case-insensitively", shouldInclude("An ERROR OCCURRED in the system"));
    }

    @Test
    public void testMultiPartSubstringMatch() {
        logChecker.addIgnoredMultiPart("Schema", "not found");

        assertFalse("Should match multi-part in sequence", shouldInclude("Schema not found for event type"));
        assertFalse("Should match with text between parts", shouldInclude("Schema validation not found"));
        assertTrue("Should not match if second part missing", shouldInclude("Schema validation found"));
        assertTrue("Should not match if order is wrong", shouldInclude("not found Schema"));
    }

    @Test
    public void testMultiPartSubstringThreeParts() {
        logChecker.addIgnoredMultiPart("Invalid", "parameter", "format");

        assertFalse("Should match all three parts in order", shouldInclude("Invalid parameter format detected"));
        assertFalse("Should match with text between", shouldInclude("Invalid request parameter format error"));
        assertTrue("Should not match if third part missing", shouldInclude("Invalid parameter"));
        assertTrue("Should not match if order is wrong", shouldInclude("Invalid format parameter"));
    }

    @Test
    public void testMultipleSubstrings() {
        logChecker.addIgnoredSubstring("specific error");
        logChecker.addIgnoredSubstring("warning message");
        logChecker.addIgnoredMultiPart("Schema", "not found");

        assertFalse("Should match first substring", shouldInclude("A specific error occurred"));
        assertFalse("Should match second substring", shouldInclude("A warning message was issued"));
        assertFalse("Should match multi-part", shouldInclude("Schema not found"));
        assertTrue("Should not match any pattern", shouldInclude("Normal log message"));
    }

    @Test
    public void testPrefixOptimization() {
        // Add multiple substrings with same prefix to test prefix grouping
        logChecker.addIgnoredSubstring("Schema not found");
        logChecker.addIgnoredSubstring("Schema validation");
        logChecker.addIgnoredSubstring("Schema error");

        assertFalse("Should match first", shouldInclude("Schema not found for event"));
        assertFalse("Should match second", shouldInclude("Schema validation failed"));
        assertFalse("Should match third", shouldInclude("Schema error occurred"));
        assertTrue("Should not match", shouldInclude("No schema issues"));
    }

    @Test
    public void testShortSubstrings() {
        logChecker.addIgnoredSubstring("err");
        logChecker.addIgnoredSubstring("warn");

        assertFalse("Should match short substring", shouldInclude("An error occurred"));
        assertFalse("Should match short substring", shouldInclude("A warning was issued"));
    }

    @Test
    public void testEmptySubstrings() {
        // Should handle empty/null gracefully - they are filtered out and don't match
        // Test with completely empty patterns only
        LogChecker emptyChecker = LogChecker.builder().withErrorContext(0, 0).withWarningContext(0, 0).build();
        emptyChecker.addIgnoredSubstring("");
        emptyChecker.addIgnoredSubstring(null);
        emptyChecker.addIgnoredMultiPart();

        LogChecker.LogEntry entry = emptyChecker.new LogEntry(
            "10:00:00.000", "ERROR", "test-thread", "TestLogger", "Any message", 1L
        );
        assertTrue("Completely empty patterns should not match", emptyChecker.shouldIncludeEntry(entry));

        // Test that filtering empty parts from multi-part works correctly
        logChecker.addIgnoredMultiPart("", "test");
        // Empty string is filtered, leaving just "test" as single-part
        assertFalse("Filtered multi-part leaves 'test' which matches", shouldInclude("test message"));
    }

    @Test
    public void testSubstringAtStart() {
        logChecker.addIgnoredSubstring("Start");

        assertFalse("Should match at start", shouldInclude("Start of message"));
        assertFalse("Should match anywhere (substring matching)", shouldInclude("Message Start here"));
    }

    @Test
    public void testSubstringAtEnd() {
        logChecker.addIgnoredSubstring("End");

        assertFalse("Should match at end", shouldInclude("Message ends with End"));
        assertFalse("Should match anywhere (substring matching)", shouldInclude("End is in the middle"));
    }

    @Test
    public void testSubstringInMiddle() {
        logChecker.addIgnoredSubstring("middle");

        assertFalse("Should match in middle", shouldInclude("Start middle end"));
        assertFalse("Should match at start", shouldInclude("middle end"));
        assertFalse("Should match at end", shouldInclude("Start middle"));
    }

    @Test
    public void testOverlappingSubstrings() {
        logChecker.addIgnoredSubstring("abc");
        logChecker.addIgnoredSubstring("bcd");
        logChecker.addIgnoredSubstring("cde");

        assertFalse("Should match first", shouldInclude("abc found"));
        assertFalse("Should match second", shouldInclude("bcd found"));
        assertFalse("Should match third", shouldInclude("cde found"));
        assertFalse("Should match overlapping", shouldInclude("abcde found"));
    }

    @Test
    public void testVeryLongSubstring() {
        StringBuilder longPattern = new StringBuilder(200);
        for (int i = 0; i < 50; i++) {
            longPattern.append("word").append(i).append(" ");
        }
        logChecker.addIgnoredSubstring(longPattern.toString().trim());

        assertFalse("Should match long substring", shouldInclude("Prefix " + longPattern.toString().trim() + " suffix"));
        assertTrue("Should not match partial", shouldInclude("word1 word2 word3"));
    }

    @Test
    public void testMultiPartWithOverlapping() {
        logChecker.addIgnoredMultiPart("abc", "def", "ghi");

        assertFalse("Should match all parts", shouldInclude("abc then def then ghi"));
        assertFalse("Should match with text between", shouldInclude("abc def ghi"));
        assertTrue("Should not match if parts missing (ghi missing)", shouldInclude("abc def"));
        assertTrue("Should not match if order wrong", shouldInclude("def abc ghi"));
        assertTrue("Should not match if only first part", shouldInclude("abc only"));
    }

    @Test
    public void testMultiPartWithSamePart() {
        // Use a more specific pattern to avoid matching "test" in "TestLogger"
        logChecker.addIgnoredMultiPart("part", "part", "part");

        assertFalse("Should match all three parts", shouldInclude("part part part"));
        assertFalse("Should match all three parts with extra", shouldInclude("part part part extra"));

        // "part part" should NOT match "part part part" pattern (missing third part)
        LogChecker.LogEntry entry1 = logChecker.new LogEntry(
            "10:00:00.000", "ERROR", "test-thread", "TestLogger", "part part", 1L
        );
        assertTrue("Entry with only two 'part' should not match three-part pattern",
            logChecker.shouldIncludeEntry(entry1));

        assertTrue("Should not match if only one part", shouldInclude("part only"));
    }

    @Test
    public void testMultiPartWithManyParts() {
        logChecker.addIgnoredMultiPart("part1", "part2", "part3", "part4", "part5");

        assertFalse("Should match all parts in sequence",
            shouldInclude("part1 then part2 then part3 then part4 then part5"));
        assertTrue("Should not match if not all parts present",
            shouldInclude("part1 then part2 then part3"));
    }

    @Test
    public void testCaseSensitivity() {
        logChecker.addIgnoredSubstring("CaseSensitive");

        assertFalse("Should match exact case", shouldInclude("CaseSensitive match"));
        assertFalse("Should match lowercase", shouldInclude("casesensitive match"));
        assertFalse("Should match uppercase", shouldInclude("CASESENSITIVE match"));
        assertFalse("Should match mixed case", shouldInclude("CaSeSeNsItIvE match"));
    }

    @Test
    public void testSpecialCharacters() {
        logChecker.addIgnoredSubstring("test@example.com");
        logChecker.addIgnoredSubstring("path/to/file");
        logChecker.addIgnoredSubstring("value=123");

        assertFalse("Should match email", shouldInclude("Contact test@example.com for help"));
        assertFalse("Should match path", shouldInclude("File at path/to/file found"));
        assertFalse("Should match equals", shouldInclude("Setting value=123"));
    }

    @Test
    public void testUnicodeCharacters() {
        logChecker.addIgnoredSubstring("café");
        logChecker.addIgnoredSubstring("naïve");

        assertFalse("Should match unicode", shouldInclude("Visit the café"));
        assertFalse("Should match unicode", shouldInclude("A naïve approach"));
    }

    @Test
    public void testWhitespaceHandling() {
        logChecker.addIgnoredSubstring("test message");
        logChecker.addIgnoredSubstring("  spaced  ");

        assertFalse("Should match with single space", shouldInclude("This is a test message here"));
        assertFalse("Should match with multiple spaces", shouldInclude("This has   spaced   in it"));
    }

    @Test
    public void testNoSubstringsConfigured() {
        // With no substrings, all entries should be included
        assertTrue("Should include when no substrings configured", shouldInclude("Any message"));
        assertTrue("Should include error messages", shouldInclude("ERROR occurred"));
    }

    @Test
    public void testBundleWatcherFastPath() {
        // BundleWatcher warnings are handled by fast path (no substring matching needed)
        LogChecker.LogEntry warnEntry = logChecker.new LogEntry(
            "10:00:00.000", "WARN", "test-thread",
            "org.apache.unomi.lifecycle.BundleWatcher", "Some warning", 1L
        );

        assertFalse("BundleWatcher warnings should be ignored", logChecker.shouldIncludeEntry(warnEntry));
    }

    @Test
    public void testCandidateStringIncludesLevelAndLogger() {
        // Verify that matching works across level + logger + message
        logChecker.addIgnoredSubstring("ERROR");

        // ERROR appears in level, should match
        assertFalse("Should match ERROR in level", shouldInclude("Some message"));

        // Reset and test logger
        logChecker = LogChecker.builder().withErrorContext(0, 0).withWarningContext(0, 0).build();
        logChecker.addIgnoredSubstring("TestLogger");

        assertFalse("Should match logger name", shouldInclude("Some message"));
    }

    @Test
    public void testPerformanceWithManySubstrings() {
        // Add many substrings to test performance
        for (int i = 0; i < 100; i++) {
            logChecker.addIgnoredSubstring("pattern" + i);
        }

        // Should still match quickly
        long start = System.nanoTime();
        assertFalse("Should match pattern50", shouldInclude("This message contains pattern50 in it"));
        long duration = System.nanoTime() - start;

        // Should complete in reasonable time (< 1ms for this test)
        assertTrue("Matching should be fast: " + duration + " ns", duration < 1_000_000);
    }

    @Test
    public void testPerformanceWithLongString() {
        logChecker.addIgnoredSubstring("target");

        // Create a long string (simulating a log entry with stack trace)
        // Put target near the beginning to ensure it's within MAX_CANDIDATE_LENGTH
        StringBuilder longString = new StringBuilder(10000);
        longString.append("target "); // Put target at start
        for (int i = 0; i < 1000; i++) {
            longString.append("This is line ").append(i).append(" of a very long log message. ");
        }

        long start = System.nanoTime();
        assertFalse("Should match target in long string", shouldInclude(longString.toString()));
        long duration = System.nanoTime() - start;

        // Should complete quickly even with long string (< 10ms)
        assertTrue("Matching should be fast even with long strings: " + duration + " ns", duration < 10_000_000);
    }

    @Test
    public void testPerformanceStressTest() {
        // Comprehensive performance test with multiple patterns and long strings
        // Should complete in under 2 seconds
        long overallStart = System.currentTimeMillis();

        // Add many diverse patterns
        for (int i = 0; i < 50; i++) {
            logChecker.addIgnoredSubstring("pattern" + i);
            logChecker.addIgnoredSubstring("error" + i);
            logChecker.addIgnoredMultiPart("part" + i, "sub" + i);
        }

        // Test many candidate strings
        for (int i = 0; i < 1000; i++) {
            String candidate = "Test message " + i + " with pattern" + (i % 50) + " in it";
            logChecker.shouldIncludeEntry(logChecker.new LogEntry(
                "10:00:00.000", "ERROR", "test-thread", "TestLogger", candidate, 1L
            ));
        }

        long overallDuration = System.currentTimeMillis() - overallStart;

        // Should complete in under 2 seconds
        assertTrue("Performance stress test should complete quickly: " + overallDuration + " ms",
            overallDuration < 2000);
    }

    @Test
    public void testTruncatedCandidateString() {
        // Test that matching works even when candidate is truncated to MAX_CANDIDATE_LENGTH
        logChecker.addIgnoredSubstring("early");

        // Create a very long message that will be truncated
        StringBuilder veryLongMessage = new StringBuilder(20000);
        veryLongMessage.append("early "); // Put target at start
        for (int i = 0; i < 2000; i++) {
            veryLongMessage.append("This is a very long line ").append(i).append(". ");
        }

        assertFalse("Should match even in truncated string", shouldInclude(veryLongMessage.toString()));
    }

    @Test
    public void testPrefixLengthBoundary() {
        // Test patterns at the PREFIX_LENGTH boundary (4 characters)
        logChecker.addIgnoredSubstring("test"); // Exactly 4 chars
        logChecker.addIgnoredSubstring("tes");  // 3 chars (short)
        logChecker.addIgnoredSubstring("test1"); // 5 chars (prefix-based)

        assertFalse("Should match 4-char pattern", shouldInclude("This is a test message"));
        assertFalse("Should match 3-char pattern", shouldInclude("This has tes in it"));
        assertFalse("Should match 5-char pattern", shouldInclude("This has test1 in it"));
    }

    /**
     * Helper method to test if a message should be included (not ignored)
     */
    private boolean shouldInclude(String message) {
        // Create a minimal log entry for testing
        LogChecker.LogEntry entry = logChecker.new LogEntry(
            "10:00:00.000", "ERROR", "test-thread",
            "TestLogger", message, 1L
        );

        // shouldIncludeEntry is package-private, so we can call it directly
        return logChecker.shouldIncludeEntry(entry);
    }
}
