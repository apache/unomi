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

import org.apache.unomi.extensions.log4j.InMemoryLogAppender;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility class to check logs for unexpected errors and warnings using an in-memory appender.
 * This replaces the file-based log checker and works with PaxExam/Karaf integration tests.
 *
 * PERFORMANCE: To avoid checking 43,000+ log entries against many patterns, each test class
 * should add only the patterns it needs. Prefer literal strings over regex for better performance.
 *
 * Example usage in a test class:
 * <pre>
 * {@literal @}Override
 * protected LogChecker createLogChecker() {
 *     return LogChecker.builder()
 *         .addIgnoredSubstring("Response status code: 400")                // Single substring (fast)
 *         .addIgnoredMultiPart("Schema", "not found")                     // Multi-part: "Schema" then "not found"
 *         .addIgnoredMultiPart("Invalid", "parameter", "format")          // Multi-part: all must appear in order
 *         .build();
 * }
 * </pre>
 *
 * IMPORTANT: All substrings are literal (no regex). Uses fast hierarchical prefix-based matching
 * with tree structure for multi-part patterns. Only checks subsequent parts if first part matches,
 * avoiding backtracking and multiple passes. Optimized for processing 43,000+ log entries.
 */
public class LogChecker {

    private int checkpointIndex = 0;
    private final LiteralPatternMatcher literalSubstringMatcher; // Hierarchical prefix-based matcher for literal substrings
    private final int errorContextLinesBefore;
    private final int errorContextLinesAfter;
    private final int warningContextLinesBefore;
    private final int warningContextLinesAfter;

    // Maximum length of candidate string for pattern matching to prevent processing extremely long strings
    private static final int MAX_CANDIDATE_LENGTH = 10000; // 10KB limit

    // Prefix length for hierarchical matching - balances between selectivity and overhead
    private static final int PREFIX_LENGTH = 4;

    /**
     * Simple data class to hold context event information (avoids storing Log4j2 core classes)
     */
    private static class ContextEvent {
        final String timestamp;
        final String level;
        final String thread;
        final String logger;
        final String message;

        ContextEvent(String timestamp, String level, String thread, String logger, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.thread = thread;
            this.logger = logger;
            this.message = message;
        }

        String format(LogChecker checker) {
            return String.format("%s [%s] %s - %s",
                checker.formatTimestamp(timestamp), level, checker.shortenLogger(logger), checker.truncateMessage(message, 100));
        }
    }

    /**
     * Represents a log entry with its details including context
     */
    public class LogEntry {
        private final String timestamp;
        private final String level;
        private final String thread;
        private final String logger;
        private final String message;
        private final long lineNumber;
        private final List<String> stacktrace;
        private final List<ContextEvent> contextBefore;
        private final List<ContextEvent> contextAfter;

        public LogEntry(String timestamp, String level, String thread, String logger, String message, long lineNumber) {
            this.timestamp = timestamp;
            this.level = level;
            this.thread = thread;
            this.logger = logger;
            this.message = message;
            this.lineNumber = lineNumber;
            this.stacktrace = new ArrayList<>();
            this.contextBefore = new ArrayList<>();
            this.contextAfter = new ArrayList<>();
        }

        public String getTimestamp() { return timestamp; }
        public String getLevel() { return level; }
        public String getThread() { return thread; }
        public String getLogger() { return logger; }
        public String getMessage() { return message; }
        public long getLineNumber() { return lineNumber; }
        public List<String> getStacktrace() { return stacktrace; }
        public List<ContextEvent> getContextBefore() { return contextBefore; }
        public List<ContextEvent> getContextAfter() { return contextAfter; }

        public void addStacktraceLine(String line) {
            stacktrace.add(line);
        }

        public void addContextBefore(ContextEvent event) {
            contextBefore.add(event);
        }

        public void addContextAfter(ContextEvent event) {
            contextAfter.add(event);
        }

        public String getFullMessage() {
            if (stacktrace.isEmpty()) {
                return message;
            }
            return message + "\n" + String.join("\n", stacktrace);
        }

        public String getFullContext() {
            StringBuilder sb = new StringBuilder();
            appendContextBefore(sb);
            appendIssueLine(sb);
            appendStackTrace(sb);
            appendContextAfter(sb);
            return sb.toString();
        }

        private void appendContextBefore(StringBuilder sb) {
            if (!contextBefore.isEmpty()) {
                sb.append("--- Context before (")
                  .append(contextBefore.size()).append(" lines) ---");
                for (ContextEvent event : contextBefore) {
                    sb.append("\n").append(event.format(LogChecker.this));
                }
            }
        }

        private void appendIssueLine(StringBuilder sb) {
            String headerLevel = (level != null) ? level : "LOG";
            LogChecker checker = LogChecker.this;

            // Extract source location from stack trace
            String sourceLocation = checker.extractSourceLocation(stacktrace);

            // Compact format: time [level] thread L{logLine} -> sourceLocation: message
            String time = checker.formatTimestamp(timestamp);
            String shortThread = checker.shortenThread(thread);
            String shortLogger = checker.shortenLogger(logger);
            String truncatedMsg = checker.truncateMessage(message, 200);

            // Format: time [level] thread L{logLine} -> ClassName:line: message
            if (sourceLocation != null && !sourceLocation.isEmpty()) {
                sb.append(String.format("%s [%s] %s L%d -> %s: %s",
                    time, headerLevel, shortThread, lineNumber, sourceLocation, truncatedMsg));
            } else {
                sb.append(String.format("%s [%s] %s L%d -> %s: %s",
                    time, headerLevel, shortThread, lineNumber, shortLogger, truncatedMsg));
            }
        }

        private void appendStackTrace(StringBuilder sb) {
            if (!stacktrace.isEmpty()) {
                sb.append("\n");
                for (String line : stacktrace) {
                    sb.append(line).append("\n");
                }
            }
        }

        private void appendContextAfter(StringBuilder sb) {
            if (!contextAfter.isEmpty()) {
                sb.append("\n--- Context after (")
                  .append(contextAfter.size()).append(" lines) ---");
                for (ContextEvent event : contextAfter) {
                    sb.append("\n").append(event.format(LogChecker.this));
                }
            }
        }

        @Override
        public String toString() {
            return String.format("[%s] %s [%s] %s - %s (line %d)",
                timestamp, level, thread, logger, message, lineNumber);
        }
    }

    /**
     * Result of a log check
     */
    public static class LogCheckResult {
        private final List<LogEntry> errors;
        private final List<LogEntry> warnings;
        private final boolean hasUnexpectedIssues;

        public LogCheckResult(List<LogEntry> errors, List<LogEntry> warnings) {
            this.errors = errors != null ? errors : Collections.emptyList();
            this.warnings = warnings != null ? warnings : Collections.emptyList();
            this.hasUnexpectedIssues = !this.errors.isEmpty() || !this.warnings.isEmpty();
        }

        public List<LogEntry> getErrors() { return errors; }
        public List<LogEntry> getWarnings() { return warnings; }
        public boolean hasUnexpectedIssues() { return hasUnexpectedIssues; }

        public String getSummary() {
            if (!hasUnexpectedIssues) {
                return "No unexpected errors or warnings found in logs.";
            }
            StringBuilder sb = new StringBuilder();
            appendErrorsSummary(sb);
            appendWarningsSummary(sb);
            return sb.toString();
        }

        private void appendErrorsSummary(StringBuilder sb) {
            if (!errors.isEmpty()) {
                sb.append(String.format("Found %d error(s):", errors.size()));
                // Limit to first 50 errors to avoid extremely long strings that slow down regex matching
                int maxErrors = Math.min(50, errors.size());
                for (int i = 0; i < maxErrors; i++) {
                    sb.append("\n").append(errors.get(i).getFullContext());
                }
                if (errors.size() > maxErrors) {
                    sb.append(String.format("\n... and %d more error(s) (truncated)", errors.size() - maxErrors));
                }
            }
        }

        private void appendWarningsSummary(StringBuilder sb) {
            if (!warnings.isEmpty()) {
                sb.append(String.format("\nFound %d warning(s):", warnings.size()));
                // Limit to first 50 warnings to avoid extremely long strings that slow down regex matching
                int maxWarnings = Math.min(50, warnings.size());
                for (int i = 0; i < maxWarnings; i++) {
                    sb.append("\n").append(warnings.get(i).getFullContext());
                }
                if (warnings.size() > maxWarnings) {
                    sb.append(String.format("\n... and %d more warning(s) (truncated)", warnings.size() - maxWarnings));
                }
            }
        }
    }

    /**
     * Create a new LogChecker with default context lines:
     * - Errors: 10 lines before and after
     * - Warnings: 0 lines before and after (no context)
     */
    public LogChecker() {
        this(10, 10, 0, 0);
    }

    /**
     * Create a new LogChecker with custom context line settings.
     * Only includes truly global patterns that occur in all tests.
     *
     * @param errorContextLinesBefore Number of lines to capture before each error
     * @param errorContextLinesAfter Number of lines to capture after each error
     * @param warningContextLinesBefore Number of lines to capture before each warning
     * @param warningContextLinesAfter Number of lines to capture after each warning
     */
    public LogChecker(int errorContextLinesBefore, int errorContextLinesAfter,
                      int warningContextLinesBefore, int warningContextLinesAfter) {
        this.literalSubstringMatcher = new LiteralPatternMatcher();
        this.errorContextLinesBefore = errorContextLinesBefore;
        this.errorContextLinesAfter = errorContextLinesAfter;
        this.warningContextLinesBefore = warningContextLinesBefore;
        this.warningContextLinesAfter = warningContextLinesAfter;
        // No global substrings needed - BundleWatcher is handled by fast path check
    }

    /**
     * Hierarchical prefix-based matcher for literal substrings with support for multi-part matching.
     *
     * Supports both:
     * - Single substrings: "Schema not found"
     * - Multi-part substrings: ["Schema", "not found"] - must appear in sequence
     *
     * Strategy:
     * 1. Group by first substring's prefix (first PREFIX_LENGTH chars, or full string if shorter)
     * 2. Build tree: first substring -> list of remaining parts
     * 3. When matching: only check subsequent parts if first part matches
     * 4. Single pass through candidate string, no backtracking
     *
     * This avoids checking every pattern against every string position,
     * and avoids checking subsequent parts unless the first part matches.
     */
    private static class LiteralPatternMatcher {
        /**
         * Represents a multi-part substring match requirement.
         * First part must match, then subsequent parts must appear in order after it.
         */
        private static class MultiPartMatch {
            final String firstPart;           // First substring to match
            final List<String> remainingParts; // Subsequent substrings (in order, after first)

            MultiPartMatch(String firstPart, List<String> remainingParts) {
                this.firstPart = firstPart;
                this.remainingParts = remainingParts != null ? remainingParts : Collections.emptyList();
            }
        }

        // Map from prefix to list of multi-part matches
        // For patterns with first part >= PREFIX_LENGTH: prefix is first PREFIX_LENGTH chars
        // For patterns with first part < PREFIX_LENGTH: prefix is the entire first part
        private final Map<String, List<MultiPartMatch>> matchesByPrefix = new HashMap<>();
        // Set of first characters of all prefixes (for quick filtering to skip most positions)
        private final Set<Character> prefixFirstChars = new HashSet<>();

        /**
         * Add a single substring to match
         */
        void addPattern(String substring) {
            addMultiPartPattern(Collections.singletonList(substring));
        }

        /**
         * Add a multi-part substring pattern (substrings must appear in sequence).
         *
         * @param parts List of substrings that must appear in order
         */
        void addMultiPartPattern(List<String> parts) {
            if (parts == null || parts.isEmpty()) {
                return;
            }

            // Convert all parts to lowercase for case-insensitive matching
            List<String> lowerParts = new ArrayList<>(parts.size());
            for (String part : parts) {
                if (part != null && !part.isEmpty()) {
                    lowerParts.add(part.toLowerCase());
                }
            }

            if (lowerParts.isEmpty()) {
                return;
            }

            String firstPart = lowerParts.get(0);
            List<String> remainingParts = lowerParts.size() > 1
                ? lowerParts.subList(1, lowerParts.size())
                : Collections.emptyList();

            MultiPartMatch match = new MultiPartMatch(firstPart, remainingParts);

            // Always use prefix-based structure, even for short first parts
            // This ensures multi-part patterns are handled correctly
            if (firstPart.length() < PREFIX_LENGTH) {
                // Short first part - use entire first part as prefix for grouping
                String prefix = firstPart; // Use full first part as prefix
                matchesByPrefix.computeIfAbsent(prefix, k -> {
                    // Track first character for quick filtering
                    if (prefix.length() > 0) {
                        prefixFirstChars.add(prefix.charAt(0));
                    }
                    return new ArrayList<>();
                }).add(match);
            } else {
                // Group by prefix of first part
                String prefix = firstPart.substring(0, PREFIX_LENGTH);
                matchesByPrefix.computeIfAbsent(prefix, k -> {
                    // Track first character for quick filtering
                    prefixFirstChars.add(prefix.charAt(0));
                    return new ArrayList<>();
                }).add(match);
            }
        }

        /**
         * Check if candidate string contains any of the patterns.
         * Optimized with character-by-character comparison to avoid substring creation.
         *
         * Strategy:
         * 1. First-character filtering: O(1) HashSet lookup skips ~95%+ of positions
         * 2. Character-by-character prefix matching: avoids substring allocation
         * 3. Only check subsequent parts if first part matches (tree pruning)
         * 4. Early exit on first match
         *
         * @param candidateLower Lowercase candidate string to check
         * @return true if any pattern matches (should be ignored)
         */
        boolean containsAny(String candidateLower) {
            int candidateLen = candidateLower.length();
            if (candidateLen == 0) {
                return false;
            }

            // For prefix-based patterns: check all possible positions
            // Handle both standard PREFIX_LENGTH prefixes and shorter prefixes (for multi-part patterns)
            int maxCheckPos = candidateLen - 1;
            if (maxCheckPos < 0) {
                return false; // Candidate too short
            }

            // Prefix-based matching with first-character filtering
            // Strategy: filter by first character to skip most positions, then use character-by-character comparison
            for (int i = 0; i <= maxCheckPos; i++) {
                char c0 = candidateLower.charAt(i);

                // Quick filter: skip if first character doesn't match any prefix
                if (!prefixFirstChars.contains(c0)) {
                    continue;
                }

                // Character-by-character prefix matching to avoid substring creation
                // Try to find matching prefix - check all possible prefix lengths
                List<MultiPartMatch> matchesWithPrefix = null;
                String matchedPrefix = null;
                int maxPrefixLen = Math.min(PREFIX_LENGTH, candidateLen - i);

                // Iterate through all prefixes and compare character-by-character
                for (Map.Entry<String, List<MultiPartMatch>> entry : matchesByPrefix.entrySet()) {
                    String prefix = entry.getKey();
                    int prefixLen = prefix.length();

                    // Skip if prefix doesn't start with matching character or is too long
                    if (prefixLen > maxPrefixLen || prefix.charAt(0) != c0) {
                        continue;
                    }

                    // Check if we have enough characters remaining
                    if (i + prefixLen > candidateLen) {
                        continue;
                    }

                    // Character-by-character comparison (avoids substring creation)
                    boolean prefixMatches = true;
                    for (int j = 1; j < prefixLen; j++) {
                        if (candidateLower.charAt(i + j) != prefix.charAt(j)) {
                            prefixMatches = false;
                            break;
                        }
                    }

                    if (prefixMatches) {
                        matchesWithPrefix = entry.getValue();
                        matchedPrefix = prefix;
                        break; // Found match, no need to check others
                    }
                }

                if (matchesWithPrefix != null && matchedPrefix != null) {
                    int prefixLen = matchedPrefix.length();
                    // Prefix matches - check multi-part matches (only this subset)
                    for (MultiPartMatch match : matchesWithPrefix) {
                        // Find first part - prefix matches at position i, so pattern could start at i or before
                        int patternLen = match.firstPart.length();
                        int firstPartPos = -1;

                        // Fast path: check if pattern starts at position i (most common case)
                        // Since prefix is at the start of pattern, pattern most likely starts at i
                        if (i + patternLen <= candidateLen) {
                            boolean matchesAtI = true;
                            // Only need to check characters after the prefix (already matched)
                            int checkStart = Math.min(prefixLen, patternLen);
                            for (int j = checkStart; j < patternLen; j++) {
                                if (candidateLower.charAt(i + j) != match.firstPart.charAt(j)) {
                                    matchesAtI = false;
                                    break;
                                }
                            }
                            if (matchesAtI) {
                                firstPartPos = i;
                            }
                        }

                        // If fast path didn't match, use indexOf to search backwards
                        // (pattern could start before i if prefix appears elsewhere in pattern)
                        if (firstPartPos < 0) {
                            int searchStart = Math.max(0, i - patternLen + Math.min(patternLen, PREFIX_LENGTH));
                            firstPartPos = candidateLower.indexOf(match.firstPart, searchStart);
                            // Pattern can't start after position i (prefix is at start of pattern)
                            if (firstPartPos > i) {
                                firstPartPos = -1;
                            }
                        }

                        if (firstPartPos >= 0) {
                            // First part found - now check remaining parts in sequence
                            if (match.remainingParts.isEmpty()) {
                                // Single-part match - we're done
                                return true;
                            }

                            // Check remaining parts appear in order after first part
                            int currentPos = firstPartPos + patternLen;
                            boolean allPartsMatch = true;

                            for (String remainingPart : match.remainingParts) {
                                int nextPos = candidateLower.indexOf(remainingPart, currentPos);
                                if (nextPos < 0) {
                                    // This part not found after previous part - prune this branch
                                    allPartsMatch = false;
                                    break;
                                }
                                // Move position forward for next part
                                currentPos = nextPos + remainingPart.length();
                            }

                            if (allPartsMatch) {
                                return true; // All parts matched in sequence
                            }
                        }
                    }
                }
            }

            return false;
        }

        /**
         * Check if any patterns are configured
         */
        boolean isEmpty() {
            return matchesByPrefix.isEmpty();
        }
    }

    /**
     * Create a builder for configuring LogChecker with specific patterns.
     * This is the recommended way to create LogChecker instances for better performance.
     *
     * Example:
     * <pre>
     * LogChecker checker = LogChecker.builder()
     *     .addIgnoredSubstring("Response status code: 400")                // Single substring
     *     .addIgnoredMultiPart("Schema", "not found")                     // Multi-part: sequential matching
     *     .build();
     * </pre>
     *
     * IMPORTANT: All substrings are literal (no regex). Uses hierarchical prefix-based matching with
     * tree structure. Multi-part patterns only check subsequent parts if first part matches.
     *
     * @return A LogCheckerBuilder instance
     */
    public static LogCheckerBuilder builder() {
        return new LogCheckerBuilder();
    }

    /**
     * Builder for creating LogChecker instances with specific substrings to ignore.
     * This allows tests to only add the substrings they need, significantly improving performance.
     */
    public static class LogCheckerBuilder {
        private int errorContextLinesBefore = 10;
        private int errorContextLinesAfter = 10;
        private int warningContextLinesBefore = 0;
        private int warningContextLinesAfter = 0;
        private final List<Object> substrings = new ArrayList<>(); // Can be String or MultiPartSubstring

        /**
         * Set context lines for errors
         */
        public LogCheckerBuilder withErrorContext(int before, int after) {
            this.errorContextLinesBefore = before;
            this.errorContextLinesAfter = after;
            return this;
        }

        /**
         * Set context lines for warnings
         */
        public LogCheckerBuilder withWarningContext(int before, int after) {
            this.warningContextLinesBefore = before;
            this.warningContextLinesAfter = after;
            return this;
        }

        /**
         * Add a single substring to ignore.
         *
         * @param substring Literal substring to match (case-insensitive)
         * @return This builder for method chaining
         */
        public LogCheckerBuilder addIgnoredSubstring(String substring) {
            this.substrings.add(substring);
            return this;
        }

        /**
         * Add a multi-part substring pattern (substrings must appear in sequence).
         * This allows matching complex patterns without regex.
         *
         * Example: addIgnoredMultiPart("Schema", "not found") matches "Schema" followed by "not found"
         *
         * @param parts Substrings that must appear in order
         * @return This builder for method chaining
         */
        public LogCheckerBuilder addIgnoredMultiPart(String... parts) {
            if (parts != null && parts.length > 0) {
                this.substrings.add(new MultiPartSubstring(Arrays.asList(parts)));
            }
            return this;
        }

        /**
         * Add multiple substrings to ignore
         *
         * @param substrings Array of substrings to add
         * @return This builder for method chaining
         */
        public LogCheckerBuilder addIgnoredSubstrings(String... substrings) {
            Collections.addAll(this.substrings, substrings);
            return this;
        }

        /**
         * Add multiple substrings to ignore
         *
         * @param substrings List of substrings to add
         * @return This builder for method chaining
         */
        public LogCheckerBuilder addIgnoredSubstrings(List<String> substrings) {
            if (substrings != null) {
                this.substrings.addAll(substrings);
            }
            return this;
        }

        /**
         * Marker class to distinguish multi-part substrings from single substrings
         */
        private static class MultiPartSubstring {
            final List<String> parts;
            MultiPartSubstring(List<String> parts) {
                this.parts = parts;
            }
        }

        /**
         * Build the LogChecker instance
         */
        public LogChecker build() {
            LogChecker checker = new LogChecker(
                errorContextLinesBefore, errorContextLinesAfter,
                warningContextLinesBefore, warningContextLinesAfter
            );
            // Add all substrings specified by the builder
            for (Object substring : substrings) {
                if (substring instanceof MultiPartSubstring) {
                    checker.addIgnoredMultiPart(((MultiPartSubstring) substring).parts);
                } else if (substring instanceof String) {
                    checker.addIgnoredSubstring((String) substring);
                }
            }
            return checker;
        }
    }

    /**
     * Add a single literal substring to ignore (expected errors/warnings).
     *
     * @param substring Literal substring to match against log messages (case-insensitive)
     *
     * IMPORTANT: All substrings are literal (no regex). This uses fast hierarchical prefix-based matching
     * for optimal performance.
     */
    public void addIgnoredSubstring(String substring) {
        if (substring != null && !substring.isEmpty()) {
            literalSubstringMatcher.addPattern(substring);
        }
    }

    /**
     * Add a multi-part substring pattern to ignore (substrings must appear in sequence).
     * This allows matching complex patterns without regex or backtracking.
     *
     * Example: addIgnoredMultiPart("Schema", "not found") will match "Schema" followed by "not found"
     * anywhere in the log message, but only checks "not found" if "Schema" is found first.
     *
     * @param parts List of substrings that must appear in order (case-insensitive)
     */
    public void addIgnoredMultiPart(List<String> parts) {
        if (parts != null && !parts.isEmpty()) {
            literalSubstringMatcher.addMultiPartPattern(parts);
        }
    }

    /**
     * Add a multi-part substring pattern to ignore (substrings must appear in sequence).
     *
     * @param parts Array of substrings that must appear in order (case-insensitive)
     */
    public void addIgnoredMultiPart(String... parts) {
        if (parts != null && parts.length > 0) {
            literalSubstringMatcher.addMultiPartPattern(Arrays.asList(parts));
        }
    }

    /**
     * Add multiple substrings to ignore
     * @param substrings List of literal substrings
     */
    public void addIgnoredSubstrings(List<String> substrings) {
        if (substrings != null) {
            for (String substring : substrings) {
                addIgnoredSubstring(substring);
            }
        }
    }

    /**
     * Mark the current log position as the starting point for the next check
     */
    public void markCheckpoint() {
        checkpointIndex = InMemoryLogAppender.getEventCount();
    }

    /**
     * Check logs since the last checkpoint for errors and warnings
     * @return LogCheckResult containing any errors/warnings found
     */
    public LogCheckResult checkLogsSinceLastCheckpoint() {
        // Use reflection to access LogEvent from InMemoryLogAppender to avoid classpath issues
        List<Object> events = getEventsSince(checkpointIndex);
        return processEvents(events, checkpointIndex);
    }

    /**
     * Get events since checkpoint using reflection to avoid direct LogEvent dependency
     * Converts List<LogEvent> to List<Object> by copying elements
     */
    private List<Object> getEventsSince(int checkpointIndex) {
        try {
            // Get the list from InMemoryLogAppender (returns List<LogEvent>)
            // We need to convert it to List<Object> to avoid importing LogEvent
            Object eventsList = InMemoryLogAppender.getEventsSince(checkpointIndex);
            if (eventsList == null) {
                return Collections.emptyList();
            }

            // Create a new ArrayList<Object> and copy all elements
            List<Object> result = new ArrayList<>();
            if (eventsList instanceof List) {
                for (Object event : (List<?>) eventsList) {
                    result.add(event);
                }
            }
            return result;
        } catch (Exception e) {
            // Use System.err to avoid creating logs that would be captured by InMemoryLogAppender
            System.err.println("LogChecker: Failed to get events from InMemoryLogAppender: " + e.getMessage());
            e.printStackTrace(System.err);
            return Collections.emptyList();
        }
    }

    /**
     * Process log events and extract errors/warnings with context
     * Uses reflection to extract data from LogEvent objects without importing Log4j2 core classes
     */
    private LogCheckResult processEvents(List<Object> events, int baseIndex) {
        List<LogEntry> errors = new ArrayList<>();
        List<LogEntry> warnings = new ArrayList<>();

        for (int i = 0; i < events.size(); i++) {
            Object event = events.get(i);
            EventData eventData = extractEventData(event);

            if (eventData == null) {
                continue;
            }

            // Only process ERROR, WARN, and FATAL levels
            if (isErrorOrWarningLevel(eventData.level)) {
                LogEntry entry = createLogEntry(eventData, baseIndex + i + 1);

                if (shouldIncludeEntry(entry)) {
                    // Determine context lengths based on log level
                    boolean isError = isErrorLevel(eventData.level);
                    int contextBefore = isError ? errorContextLinesBefore : warningContextLinesBefore;
                    int contextAfter = isError ? errorContextLinesAfter : warningContextLinesAfter;

                    // Capture context before
                    int startBefore = Math.max(0, i - contextBefore);
                    for (int j = startBefore; j < i; j++) {
                        EventData contextData = extractEventData(events.get(j));
                        if (contextData != null) {
                            entry.addContextBefore(new ContextEvent(
                                contextData.timestamp, contextData.level,
                                contextData.thread, contextData.logger, contextData.message));
                        }
                    }

                    // Capture context after
                    int endAfter = Math.min(events.size(), i + 1 + contextAfter);
                    for (int j = i + 1; j < endAfter; j++) {
                        EventData contextData = extractEventData(events.get(j));
                        if (contextData != null) {
                            entry.addContextAfter(new ContextEvent(
                                contextData.timestamp, contextData.level,
                                contextData.thread, contextData.logger, contextData.message));
                        }
                    }

                    // Add stack trace if present
                    if (eventData.throwable != null) {
                        String[] stackTrace = getStackTrace(eventData.throwable);
                        for (String line : stackTrace) {
                            entry.addStacktraceLine(line);
                        }
                    }

                    addEntryToResults(entry, errors, warnings);
                }
            }
        }

        return new LogCheckResult(errors, warnings);
    }

    /**
     * Data extracted from a LogEvent (avoids storing LogEvent directly)
     */
    private static class EventData {
        final String timestamp;
        final String level;
        final String thread;
        final String logger;
        final String message;
        final Throwable throwable;

        EventData(String timestamp, String level, String thread, String logger, String message, Throwable throwable) {
            this.timestamp = timestamp;
            this.level = level;
            this.thread = thread;
            this.logger = logger;
            this.message = message;
            this.throwable = throwable;
        }
    }

    /**
     * Extract data from a LogEvent using reflection to avoid direct dependency
     */
    private EventData extractEventData(Object event) {
        try {
            // Use reflection to access LogEvent methods without importing the class
            Class<?> eventClass = event.getClass();

            // Get level
            Object levelObj = eventClass.getMethod("getLevel").invoke(event);
            String level = levelObj != null ? levelObj.toString() : "UNKNOWN";

            // Get instant/timestamp and format it
            Object instantObj = eventClass.getMethod("getInstant").invoke(event);
            String timestamp = formatInstant(instantObj);

            // Get thread name
            String thread = (String) eventClass.getMethod("getThreadName").invoke(event);
            if (thread == null) thread = "";

            // Get logger name
            String logger = (String) eventClass.getMethod("getLoggerName").invoke(event);
            if (logger == null) logger = "";

            // Get message
            Object messageObj = eventClass.getMethod("getMessage").invoke(event);
            String message = "";
            if (messageObj != null) {
                Object formattedMsg = messageObj.getClass().getMethod("getFormattedMessage").invoke(messageObj);
                if (formattedMsg != null) {
                    message = formattedMsg.toString();
                }
            }

            // Get throwable
            Throwable throwable = (Throwable) eventClass.getMethod("getThrown").invoke(event);

            return new EventData(timestamp, level, thread, logger, message, throwable);
        } catch (Exception e) {
            // Use System.err to avoid creating logs that would be captured by InMemoryLogAppender
            System.err.println("LogChecker: Failed to extract data from log event: " + e.getMessage());
            e.printStackTrace(System.err);
            return null;
        }
    }

    /**
     * Check if level is ERROR, WARN, or FATAL
     */
    private boolean isErrorOrWarningLevel(String level) {
        return "ERROR".equals(level) || "WARN".equals(level) || "FATAL".equals(level);
    }

    /**
     * Create a LogEntry from extracted event data
     */
    private LogEntry createLogEntry(EventData eventData, long lineNumber) {
        return new LogEntry(eventData.timestamp, eventData.level, eventData.thread,
                           eventData.logger, eventData.message, lineNumber);
    }

    /**
     * Get stack trace as array of strings
     */
    private String[] getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return new String[0];
        }
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString().split("\n");
    }

    /**
     * Add a log entry to the appropriate result list (errors or warnings)
     */
    private void addEntryToResults(LogEntry entry, List<LogEntry> errors, List<LogEntry> warnings) {
        String level = entry.getLevel();
        if (isErrorLevel(level)) {
            errors.add(entry);
        } else if ("WARN".equals(level)) {
            warnings.add(entry);
        }
    }

    /**
     * Check if a log level represents an error
     */
    private boolean isErrorLevel(String level) {
        return "ERROR".equals(level) || "FATAL".equals(level);
    }

    /**
     * Check if a log entry should be included (not ignored)
     *
     * CRITICAL PERFORMANCE: This method is called for every ERROR/WARN/FATAL log entry (43,000+).
     * Optimized for minimal operations and single-pass processing:
     * - Early exit if no patterns configured
     * - Avoids expensive operations (getFullMessage, toLowerCase) unless needed
     * - Single-pass string building with length limit
     * - Early exit on first substring match
     * - No regex: uses fast hierarchical prefix-based matching
     *
     * Package-private for testing purposes.
     */
    boolean shouldIncludeEntry(LogEntry entry) {
        // Fast path: default ignores based on level/logger (no string building needed)
        if ("WARN".equals(entry.getLevel()) && entry.getLogger() != null && entry.getLogger().contains("BundleWatcher")) {
            return false;
        }

        // Early exit: if no substrings configured, include all entries
        if (literalSubstringMatcher.isEmpty()) {
            return true;
        }

        // Build candidate string in single pass with length limit
        // Prefer message over fullMessage (which includes stack trace) for performance
        String level = entry.getLevel() != null ? entry.getLevel() : "";
        String logger = entry.getLogger() != null ? entry.getLogger() : "";
        String message = entry.getMessage() != null ? entry.getMessage() : "";

        // Build candidate: level + logger + message (most common case)
        // No need to include fullMessage since we only use literal substrings
        StringBuilder candidateBuilder = new StringBuilder(Math.min(level.length() + logger.length() + message.length() + 10, MAX_CANDIDATE_LENGTH));
        candidateBuilder.append(level).append(' ').append(logger).append(' ').append(message);

        // Ensure we don't exceed the limit (safety check)
        String candidate = candidateBuilder.toString();
        if (candidate.length() > MAX_CANDIDATE_LENGTH) {
            candidate = candidate.substring(0, MAX_CANDIDATE_LENGTH);
        }

        // Check literal substrings using hierarchical prefix-based matching
        // This minimizes character comparisons by checking prefixes first
        String candidateLower = candidate.toLowerCase();
        if (literalSubstringMatcher.containsAny(candidateLower)) {
            return false; // Early exit on first match
        }

        return true;
    }

    /**
     * Format an Instant object to a compact timecode (HH:mm:ss.SSS)
     */
    private String formatInstant(Object instantObj) {
        if (instantObj == null) {
            return "";
        }
        try {
            Instant instant = null;

            // If it's already an Instant, use it directly
            if (instantObj instanceof Instant) {
                instant = (Instant) instantObj;
            } else {
                // Try to extract epoch seconds and nanos using reflection
                // MutableInstant has getEpochSecond() and getNanoOfSecond() or getNanoOfMillisecond()
                try {
                    Class<?> instantClass = instantObj.getClass();
                    long epochSeconds = ((Number) instantClass.getMethod("getEpochSecond").invoke(instantObj)).longValue();
                    int nanos = 0;
                    try {
                        nanos = ((Number) instantClass.getMethod("getNanoOfSecond").invoke(instantObj)).intValue();
                    } catch (NoSuchMethodException e) {
                        // Try getNanoOfMillisecond and convert to nanoseconds
                        long nanoOfMilli = ((Number) instantClass.getMethod("getNanoOfMillisecond").invoke(instantObj)).longValue();
                        nanos = (int) (nanoOfMilli * 1_000_000);
                    }
                    instant = Instant.ofEpochSecond(epochSeconds, nanos);
                } catch (Exception e) {
                    // If reflection fails, try toString parsing as last resort
                    String instantStr = instantObj.toString();
                    Pattern epochPattern = Pattern.compile("epochSecond=(\\d+)");
                    Pattern nanoPattern = Pattern.compile("nano=(\\d+)");
                    java.util.regex.Matcher epochMatcher = epochPattern.matcher(instantStr);
                    java.util.regex.Matcher nanoMatcher = nanoPattern.matcher(instantStr);

                    if (epochMatcher.find()) {
                        long epochSeconds = Long.parseLong(epochMatcher.group(1));
                        long nanos = 0;
                        if (nanoMatcher.find()) {
                            nanos = Long.parseLong(nanoMatcher.group(1));
                        }
                        instant = Instant.ofEpochSecond(epochSeconds, nanos);
                    }
                }
            }

            if (instant != null) {
                // Format as compact timecode: HH:mm:ss.SSS
                return DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                    .format(instant.atZone(ZoneId.systemDefault()));
            }

            // Fallback to original string if we can't parse it
            return instantObj.toString();
        } catch (Exception e) {
            // Fallback to toString if formatting fails
            return instantObj.toString();
        }
    }

    /**
     * Format a timestamp string (already extracted) to compact timecode format (HH:mm:ss.SSS)
     * This is only called for ContextEvent timestamps which are already strings from formatInstant()
     */
    private String formatTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return "";
        }
        // If it's already in HH:mm:ss.SSS format (from formatInstant), return as-is
        if (timestamp.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")) {
            return timestamp;
        }
        // If it contains MutableInstant format, try to parse it (shouldn't happen, but handle it)
        if (timestamp.contains("epochSecond")) {
            try {
                Pattern epochPattern = Pattern.compile("epochSecond=(\\d+)");
                Pattern nanoPattern = Pattern.compile("nano=(\\d+)");
                java.util.regex.Matcher epochMatcher = epochPattern.matcher(timestamp);
                java.util.regex.Matcher nanoMatcher = nanoPattern.matcher(timestamp);

                if (epochMatcher.find()) {
                    long epochSeconds = Long.parseLong(epochMatcher.group(1));
                    long nanos = 0;
                    if (nanoMatcher.find()) {
                        nanos = Long.parseLong(nanoMatcher.group(1));
                    }
                    Instant instant = Instant.ofEpochSecond(epochSeconds, nanos);
                    return DateTimeFormatter.ofPattern("HH:mm:ss.SSS").format(instant);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        // Return as-is for any other format
        return timestamp;
    }

    /**
     * Shorten logger name to just the class name (remove package)
     */
    private String shortenLogger(String logger) {
        if (logger == null || logger.isEmpty()) {
            return "";
        }
        int lastDot = logger.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < logger.length() - 1) {
            return logger.substring(lastDot + 1);
        }
        return logger;
    }

    /**
     * Shorten thread name for compact display (keep last part if it contains useful info)
     */
    private String shortenThread(String thread) {
        if (thread == null || thread.isEmpty()) {
            return "main";
        }
        // If thread name is long, try to extract meaningful part
        // For Karaf threads like "Karaf-1", "pool-1-thread-2", keep as-is
        // For very long names, truncate
        if (thread.length() > 20) {
            return thread.substring(0, 17) + "...";
        }
        return thread;
    }

    /**
     * Truncate message if it's too long
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) {
            return "";
        }
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength - 3) + "...";
    }

    /**
     * Extract source location (class:line) from stack trace, skipping logging framework classes
     */
    private String extractSourceLocation(List<String> stacktrace) {
        if (stacktrace == null || stacktrace.isEmpty()) {
            return null;
        }

        // Patterns to skip (logging framework classes)
        Pattern skipPattern = Pattern.compile(
            ".*(org\\.apache\\.logging|org\\.slf4j|ch\\.qos\\.logback|org\\.log4j|" +
            "java\\.util\\.logging|sun\\.reflect|jdk\\.internal\\.reflect).*"
        );

        // Pattern to match stack trace lines: at package.ClassName.methodName(FileName.java:lineNumber)
        // Group 1: full qualified name (package.ClassName.methodName)
        // Group 2: line number
        Pattern stackTracePattern = Pattern.compile(
            "\\s*at\\s+([\\w.$<>]+)\\([\\w.]+\\.java:(\\d+)\\)"
        );

        for (String line : stacktrace) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            // Skip logging framework classes
            if (skipPattern.matcher(line).matches()) {
                continue;
            }

            // Try to match stack trace pattern
            java.util.regex.Matcher matcher = stackTracePattern.matcher(line);
            if (matcher.find()) {
                String fullQualifiedName = matcher.group(1);
                String lineNumber = matcher.group(2);

                // Extract class name from full qualified name (package.ClassName.methodName)
                // Remove method name by finding the last dot before method name
                // For inner classes, we want the outer class name
                String className = fullQualifiedName;

                // Remove generic type parameters if present
                int genericStart = className.indexOf('<');
                if (genericStart > 0) {
                    className = className.substring(0, genericStart);
                }

                // Extract class name (everything up to the last dot before method name)
                // Method names typically start with lowercase, but we'll use a simpler approach:
                // Take the part before the last dot that contains the class
                int lastDot = className.lastIndexOf('.');
                if (lastDot > 0) {
                    // Check if the part after last dot looks like a method (starts with lowercase or is a common method pattern)
                    String afterDot = className.substring(lastDot + 1);
                    // If it's all uppercase or contains $, it might be a class, otherwise assume it's a method
                    if (afterDot.length() > 0 && Character.isLowerCase(afterDot.charAt(0)) &&
                        !afterDot.contains("$")) {
                        // Likely a method name, get the class name before it
                        className = className.substring(0, lastDot);
                    }
                }

                // Extract just the simple class name (last part)
                lastDot = className.lastIndexOf('.');
                String simpleClassName = (lastDot >= 0) ? className.substring(lastDot + 1) : className;

                // Remove inner class markers ($)
                simpleClassName = simpleClassName.replace('$', '.');

                // Return compact format: ClassName:lineNumber
                return simpleClassName + ":" + lineNumber;
            }
        }

        return null;
    }
}

