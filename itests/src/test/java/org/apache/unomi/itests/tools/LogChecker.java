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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility class to check logs for unexpected errors and warnings using an in-memory appender.
 * This replaces the file-based log checker and works with PaxExam/Karaf integration tests.
 */
public class LogChecker {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LogChecker.class);
    
    private int checkpointIndex = 0;
    private final Set<Pattern> ignoredPatterns;
    private final int errorContextLinesBefore;
    private final int errorContextLinesAfter;
    private final int warningContextLinesBefore;
    private final int warningContextLinesAfter;
    
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
                for (LogEntry error : errors) {
                    sb.append("\n").append(error.getFullContext());
                }
            }
        }
        
        private void appendWarningsSummary(StringBuilder sb) {
            if (!warnings.isEmpty()) {
                sb.append(String.format("\nFound %d warning(s):", warnings.size()));
                for (LogEntry warning : warnings) {
                    sb.append("\n").append(warning.getFullContext());
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
     * Create a new LogChecker with custom context line settings
     * @param errorContextLinesBefore Number of lines to capture before each error
     * @param errorContextLinesAfter Number of lines to capture after each error
     * @param warningContextLinesBefore Number of lines to capture before each warning
     * @param warningContextLinesAfter Number of lines to capture after each warning
     */
    public LogChecker(int errorContextLinesBefore, int errorContextLinesAfter, 
                      int warningContextLinesBefore, int warningContextLinesAfter) {
        this.ignoredPatterns = new HashSet<>();
        this.errorContextLinesBefore = errorContextLinesBefore;
        this.errorContextLinesAfter = errorContextLinesAfter;
        this.warningContextLinesBefore = warningContextLinesBefore;
        this.warningContextLinesAfter = warningContextLinesAfter;
        addDefaultIgnoredPatterns();
    }
    
    /**
     * Add a pattern to ignore (expected errors/warnings)
     * @param pattern Regex pattern to match against log messages
     */
    public void addIgnoredPattern(String pattern) {
        ignoredPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
    }
    
    /**
     * Add multiple patterns to ignore
     * @param patterns List of regex patterns
     */
    public void addIgnoredPatterns(List<String> patterns) {
        if (patterns != null) {
            for (String pattern : patterns) {
                addIgnoredPattern(pattern);
            }
        }
    }
    
    /**
     * Add default ignored patterns for common expected errors
     */
    private void addDefaultIgnoredPatterns() {
        // BundleWatcher warnings (common during startup)
        addIgnoredPattern("BundleWatcher.*WARN");
        // Old-style feature file deprecation warnings
        addIgnoredPattern("DEPRECATED.*feature.*file");
        // Segment condition recommendations
        addIgnoredPattern("segment.*condition.*recommendation");
        // KarafTestWatcher FAILED messages (just echoes of test failures)
        addIgnoredPattern("KarafTestWatcher.*FAILED:");
        // Dynamic test conditions (expected during test runs)
        addIgnoredPattern("loginEventCondition for rule testLogin");
        // Deprecated legacy query builder warnings
        addIgnoredPattern("DEPRECATED.*Using legacy queryBuilderId");
        // Test migration script intentional failures (expected in migration recovery tests)
        addIgnoredPattern("failingMigration.*Intentional failure");
        addIgnoredPattern("Error executing migration script.*failingMigration");
        
        // InvalidRequestExceptionMapper errors (expected in InputValidationIT tests)
        addIgnoredPattern("InvalidRequestExceptionMapper.*Invalid parameter");
        addIgnoredPattern("InvalidRequestExceptionMapper.*Invalid Context request object");
        addIgnoredPattern("InvalidRequestExceptionMapper.*Invalid events collector object");
        addIgnoredPattern("InvalidRequestExceptionMapper.*Invalid profile ID format in cookie");
        addIgnoredPattern("InvalidRequestExceptionMapper.*events collector cannot be empty");
        addIgnoredPattern("InvalidRequestExceptionMapper.*Unable to deserialize object because");
        addIgnoredPattern("InvalidRequestExceptionMapper.*Incoming POST request blocked because exceeding maximum bytes size");
        
        // Test-related schema errors (expected in JSONSchemaIT and other tests)
        addIgnoredPattern("Schema not found for event type: dummy");
        addIgnoredPattern("Schema not found for event type: flattened");
        addIgnoredPattern("Error executing system operation: Test exception");
        addIgnoredPattern("Error executing system operation:.*ValidationException.*Schema not found");
        addIgnoredPattern("Couldn't find persona");
        addIgnoredPattern("Unable to save schema");
        
        // Test-related property copy errors (expected in CopyPropertiesActionIT)
        addIgnoredPattern("Impossible to copy the property");
        
        // Expected HTTP response codes in tests
        addIgnoredPattern("Response status code: 204");
        addIgnoredPattern("Response status code: 400");
        
        // Shutdown-related errors (expected during test teardown)
        addIgnoredPattern("FrameworkEvent ERROR");
        addIgnoredPattern("EventDispatcher: Error during dispatch.*Blueprint container is being or has been destroyed");
        
        // Test query errors (expected when testing invalid queries/scroll IDs)
        addIgnoredPattern("Error while executing in class loader.*scrollIdentifier=dummyScrollId");
        addIgnoredPattern("Error while executing in class loader.*Error loading itemType");
        addIgnoredPattern("Error while executing in class loader.*Error continuing scrolling query");
        
        // Condition validation errors (expected in tests with invalid conditions)
        addIgnoredPattern("Failed to validate condition");
        addIgnoredPattern("Error executing condition evaluator.*pastEventConditionEvaluator");
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
            LOGGER.warn("Failed to get events from InMemoryLogAppender", e);
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
            LOGGER.warn("Failed to extract data from log event", e);
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
     */
    private boolean shouldIncludeEntry(LogEntry entry) {
        // Default ignores based on level/logger (fast path)
        if ("WARN".equals(entry.getLevel()) && entry.getLogger() != null && entry.getLogger().contains("BundleWatcher")) {
            return false;
        }
        
        // Build a rich candidate string for matching custom ignored patterns
        StringBuilder candidateBuilder = new StringBuilder();
        candidateBuilder.append(entry.getLevel() != null ? entry.getLevel() : "")
                .append(' ')
                .append(entry.getLogger() != null ? entry.getLogger() : "")
                .append(' ')
                .append(entry.getMessage() != null ? entry.getMessage() : "")
                .append(' ')
                .append(entry.getFullMessage() != null ? entry.getFullMessage() : "");
        String candidate = candidateBuilder.toString();
        for (Pattern pattern : ignoredPatterns) {
            if (pattern.matcher(candidate).find()) {
                return false;
            }
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

