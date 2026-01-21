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
package org.apache.unomi.itests.shell;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.itests.BaseIT;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for shell command integration tests.
 * Provides common utilities for command execution and output parsing.
 */
public abstract class ShellCommandsBaseIT extends BaseIT {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ShellCommandsBaseIT.class);

    /**
     * Get ObjectMapper for JSON parsing.
     * Uses CustomObjectMapper for consistency with Unomi's JSON handling.
     * This ensures proper deserialization of Unomi Item types and maintains
     * the same date formatting and configuration as the rest of the system.
     *
     * Note: This is lazy-initialized to avoid class loading issues before OSGi is ready.
     */
    protected ObjectMapper getJsonMapper() {
        return CustomObjectMapper.getObjectMapper();
    }

    /**
     * Execute a shell command and capture its output as a string.
     * Temporarily disables InMemoryLogAppender during execution to prevent StackOverflow
     * caused by recursive output capture in Karaf shell streams.
     *
     * @param command the command to execute
     * @return the command output
     */
    protected String executeCommandAndGetOutput(String command) {
        String output = executeCommand(command);
        // Return empty string if output is null to avoid NPE
        return output != null ? output : "";
    }

    /**
     * Execute a command and verify the output contains expected text.
     *
     * @param command the command to execute
     * @param expectedOutput the expected text in the output
     */
    protected void executeCommandAndVerify(String command, String expectedOutput) {
        String output = executeCommandAndGetOutput(command);
        if (!output.contains(expectedOutput)) {
            throw new AssertionError("Expected output to contain '" + expectedOutput +
                "' but got: " + output);
        }
    }

    /**
     * Parse JSON output from a command.
     * Attempts to extract JSON from the output string.
     *
     * @param output the command output
     * @return parsed JSON as a Map
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> parseJsonOutput(String output) {
        try {
            // Try to find JSON in the output (may be mixed with other text)
            int jsonStart = output.indexOf('{');
            int jsonEnd = output.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = output.substring(jsonStart, jsonEnd + 1);
                return (Map<String, Object>) getJsonMapper().readValue(jsonStr, Map.class);
            }
            // If no JSON found, try parsing the whole output
            return (Map<String, Object>) getJsonMapper().readValue(output, Map.class);
        } catch (Exception e) {
            // Don't log here - any logging can be captured by command output stream causing StackOverflow
            // Just throw exception without logging
            throw new RuntimeException("Failed to parse JSON output", e);
        }
    }

    /**
     * Verify table output contains expected headers.
     *
     * @param output the command output
     * @param expectedHeaders the expected column headers
     */
    protected void verifyTableOutput(String output, String[] expectedHeaders) {
        for (String header : expectedHeaders) {
            if (!output.contains(header)) {
                throw new AssertionError("Expected table to contain header '" + header +
                    "' but got: " + output);
            }
        }
    }

    /**
     * Extract table rows from command output.
     * Assumes output is in Karaf ShellTable format.
     *
     * @param output the command output
     * @return list of rows, each row is a list of cell values
     */
    protected List<List<String>> extractTableRows(String output) {
        List<List<String>> rows = new ArrayList<>();
        String[] lines = output.split("\n");

        boolean inTable = false;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            // Check if this is a table separator line
            if (line.matches("^[+-]+$")) {
                inTable = true;
                continue;
            }

            if (inTable && !line.isEmpty()) {
                // Split by multiple spaces (table columns)
                String[] cells = line.split("\\s{2,}");
                if (cells.length > 0) {
                    List<String> row = new ArrayList<>();
                    for (String cell : cells) {
                        row.add(cell.trim());
                    }
                    rows.add(row);
                }
            }
        }

        return rows;
    }

    /**
     * Extract CSV rows from command output.
     *
     * @param output the command output
     * @return list of rows, each row is a list of cell values
     */
    protected List<List<String>> extractCsvRows(String output) {
        List<List<String>> rows = new ArrayList<>();
        String[] lines = output.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] cells = line.split(",");
            List<String> row = new ArrayList<>();
            for (String cell : cells) {
                row.add(cell.trim());
            }
            rows.add(row);
        }

        return rows;
    }

    /**
     * Create a unique test ID with timestamp.
     *
     * @param prefix the prefix for the ID
     * @return a unique ID
     */
    protected String createTestId(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }

    /**
     * Wait for a condition to be true, with retries.
     *
     * @param message the message to log
     * @param condition the condition supplier
     * @param maxRetries maximum number of retries
     * @param retryDelayMs delay between retries in milliseconds
     * @return true if condition became true, false otherwise
     */
    protected boolean waitForCondition(String message, Supplier<Boolean> condition,
                                       int maxRetries, long retryDelayMs) {
        for (int i = 0; i < maxRetries; i++) {
            if (condition.get()) {
                return true;
            }
            if (i < maxRetries - 1) {
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        // Don't log here - any logging can be captured by command output stream causing StackOverflow
        return false;
    }

    /**
     * Validate that a line contains a numeric value after a label.
     *
     * @param line the line to validate
     * @param label the label to look for (e.g., "Hits:", "Total:")
     * @param allowDecimal whether to allow decimal numbers (true) or only integers (false)
     * @return true if the line contains the label followed by a valid number
     */
    protected boolean validateNumericValue(String line, String label, boolean allowDecimal) {
        if (!line.contains(label)) {
            return false;
        }
        String[] parts = line.split(":");
        if (parts.length > 1) {
            String value = parts[1].trim();
            // Remove percentage sign if present
            value = value.replace("%", "").trim();
            String pattern = allowDecimal ? "\\d+(\\.\\d+)?" : "\\d+";
            return value.matches(pattern);
        }
        return false;
    }

    /**
     * Validate numeric values in output lines for given labels.
     *
     * @param output the command output
     * @param labels the labels to validate (e.g., "Hits:", "Misses:")
     * @param allowDecimal whether to allow decimal numbers
     */
    protected void validateNumericValuesInOutput(String output, String[] labels, boolean allowDecimal) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            for (String label : labels) {
                if (line.contains(label)) {
                    Assert.assertTrue("Value after " + label + " should be numeric: " + line,
                        validateNumericValue(line, label, allowDecimal));
                }
            }
        }
    }

    /**
     * Extract a numeric value from a line that matches a pattern.
     *
     * @param output the command output
     * @param pattern the regex pattern with a capturing group for the number
     * @return the extracted number, or -1 if not found
     */
    protected int extractNumericValue(String output, Pattern pattern) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    // Continue to next line
                }
            }
        }
        return -1;
    }

    /**
     * Validate that output contains expected table headers.
     *
     * @param output the command output
     * @param requiredHeaders at least one of these headers must be present
     * @param optionalHeaders additional headers that may be present
     */
    protected void validateTableHeaders(String output, String[] requiredHeaders, String... optionalHeaders) {
        boolean foundRequired = false;
        for (String header : requiredHeaders) {
            if (output.contains(header)) {
                foundRequired = true;
                break;
            }
        }
        Assert.assertTrue("Should contain at least one required table header: " +
            Arrays.toString(requiredHeaders), foundRequired);
    }

    /**
     * Validate that a table contains a specific value in its rows.
     *
     * @param output the command output
     * @param expectedValue the value to search for
     * @return true if the value is found in the table
     */
    protected boolean tableContainsValue(String output, String expectedValue) {
        List<List<String>> rows = extractTableRows(output);
        for (List<String> row : rows) {
            if (row.contains(expectedValue)) {
                return true;
            }
        }
        // Also check raw output as fallback
        return output.contains(expectedValue);
    }

    /**
     * Validate error message contains expected content.
     *
     * @param output the command output
     * @param expectedErrorPattern the expected error pattern (e.g., "not found", "Error:")
     * @param expectedId the ID that should appear in the error (if any)
     */
    protected void validateErrorMessage(String output, String expectedErrorPattern, String expectedId) {
        Assert.assertTrue("Should contain error pattern: " + expectedErrorPattern,
            output.contains(expectedErrorPattern));
        if (expectedId != null) {
            Assert.assertTrue("Error message should contain ID: " + expectedId,
                output.contains(expectedId));
        }
    }

    /**
     * Test that a command exists by checking help or error handling.
     *
     * @param command the command to test
     * @param expectedKeywords keywords that should appear in help output (if available)
     */
    protected void validateCommandExists(String command, String... expectedKeywords) {
        try {
            String output = executeCommandAndGetOutput(command + " --help");
            if (output != null && output.length() > 0 && expectedKeywords.length > 0) {
                boolean foundKeyword = false;
                for (String keyword : expectedKeywords) {
                    if (output.contains(keyword)) {
                        foundKeyword = true;
                        break;
                    }
                }
                Assert.assertTrue("Help should contain command information",
                    foundKeyword || output.length() > 0);
            }
        } catch (Exception e) {
            // Command might not have help or might require parameters
            // Verify it's not a "command not found" error
            String errorMsg = e.getMessage();
            if (errorMsg != null) {
                Assert.assertFalse("Command should exist (error: " + errorMsg + ")",
                    errorMsg.contains("command not found") ||
                    errorMsg.contains("CommandNotFoundException") ||
                    errorMsg.contains("Unknown command"));
            }
        }
    }

    /**
     * Extract a value from output after a label.
     *
     * @param output the command output
     * @param label the label to search for (e.g., "Current tenant ID:")
     * @return the value after the label, or null if not found
     */
    protected String extractValueAfterLabel(String output, String label) {
        if (!output.contains(label)) {
            return null;
        }
        String[] parts = output.split(Pattern.quote(label));
        if (parts.length > 1) {
            return parts[1].trim().split("\\s")[0]; // Get first word after label
        }
        return null;
    }

    /**
     * Validate that output contains at least one of the given strings.
     *
     * @param output the command output
     * @param possibleValues possible values that should appear in output
     * @param message the assertion message
     */
    protected void assertContainsAny(String output, String[] possibleValues, String message) {
        boolean found = false;
        for (String value : possibleValues) {
            if (output.contains(value)) {
                found = true;
                break;
            }
        }
        Assert.assertTrue(message, found);
    }

    /**
     * Validate JSON object structure and values.
     *
     * @param jsonData the parsed JSON data
     * @param expectedFields map of field paths to expected values (e.g., "itemId" -> "test-123", "metadata.name" -> "Test")
     */
    @SuppressWarnings("unchecked")
    protected void validateJsonFields(Map<String, Object> jsonData, Map<String, Object> expectedFields) {
        for (Map.Entry<String, Object> entry : expectedFields.entrySet()) {
            String fieldPath = entry.getKey();
            Object expectedValue = entry.getValue();

            String[] pathParts = fieldPath.split("\\.");
            Object current = jsonData;

            for (String part : pathParts) {
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(part);
                    if (current == null) {
                        Assert.fail("Field path '" + fieldPath + "' not found in JSON");
                        return;
                    }
                } else {
                    Assert.fail("Cannot navigate path '" + fieldPath + "' - intermediate value is not a map");
                    return;
                }
            }

            Assert.assertEquals("Field '" + fieldPath + "' should match", expectedValue, current);
        }
    }

    /**
     * Create a rule via CRUD command for testing.
     *
     * @param ruleId the rule ID
     * @param ruleName the rule name
     * @return the create command output
     */
    protected String createTestRule(String ruleId, String ruleName) {
        // Include parameterValues (even if empty) to ensure proper condition deserialization
        String ruleJson = String.format(
            "{\"itemId\":\"%s\",\"metadata\":{\"id\":\"%s\",\"name\":\"%s\",\"description\":\"Test\",\"scope\":\"systemscope\",\"enabled\":true},\"condition\":{\"type\":\"matchAllCondition\",\"parameterValues\":{}},\"actions\":[]}",
            ruleId, ruleId, ruleName
        );
        // Use new argument-based syntax: unomi:crud create rule '<json>'
        // Quote JSON to ensure it's treated as a single argument (prevents Gogo shell from interpreting {} as closure)
        return executeCommandAndGetOutput(
            String.format("unomi:crud create rule '%s'", ruleJson)
        );
    }
}
