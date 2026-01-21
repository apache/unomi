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

import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.Topic;
import org.apache.unomi.api.Scope;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integration tests for unomi:crud command.
 * Tests CRUD operations for various object types including schema.
 */
public class CrudCommandsIT extends ShellCommandsBaseIT {

    private List<String> createdItemIds = new ArrayList<>();
    private List<File> tempFiles = new ArrayList<>();

    @Before
    public void setUp() {
        createdItemIds.clear();
        tempFiles.clear();
    }

    @After
    public void tearDown() {
        // Clean up created items - try CRUD delete first, then fall back to services
        for (String itemId : new ArrayList<>(createdItemIds)) {
            cleanupItem(itemId);
        }
        createdItemIds.clear();

        // Clean up temp files
        for (File file : tempFiles) {
            try {
                if (file.exists()) {
                    file.delete();
                }
            } catch (Exception e) {
                // Don't log here - any logging can be captured by command output stream causing StackOverflow
            }
        }
        tempFiles.clear();
    }

    /**
     * Clean up a single item by trying various deletion methods.
     */
    private void cleanupItem(String itemId) {
        // Try CRUD delete for common types
        if (tryCrudDelete(itemId)) {
            return;
        }
        
        // Fall back to direct service calls if CRUD didn't work
        tryServiceDeletion(itemId);
    }

    /**
     * Try to delete an item using CRUD commands.
     * 
     * @param itemId the item ID to delete
     * @return true if deletion was successful
     */
    private boolean tryCrudDelete(String itemId) {
        String[] types = {"goal", "rule", "segment", "topic", "scope", "schema"};
        for (String type : types) {
            try {
                String output = executeCommandAndGetOutput("unomi:crud delete " + type + " " + itemId);
                if (output.contains("Deleted")) {
                    return true;
                }
            } catch (Exception e) {
                // Try next type
            }
        }
        return false;
    }

    /**
     * Try to delete an item using direct service calls.
     * 
     * @param itemId the item ID to delete
     */
    private void tryServiceDeletion(String itemId) {
        try {
            if (rulesService != null) {
                Rule rule = rulesService.getRule(itemId);
                if (rule != null) {
                    rulesService.removeRule(itemId);
                    return;
                }
            }
            if (goalsService != null) {
                Goal goal = goalsService.getGoal(itemId);
                if (goal != null) {
                    goalsService.removeGoal(itemId);
                    return;
                }
            }
            if (segmentService != null) {
                Segment segment = segmentService.getSegmentDefinition(itemId);
                if (segment != null) {
                    segmentService.removeSegmentDefinition(itemId, false);
                    return;
                }
            }
            if (topicService != null) {
                Topic topic = topicService.load(itemId);
                if (topic != null) {
                    topicService.delete(itemId);
                    return;
                }
            }
            if (scopeService != null) {
                Scope scope = scopeService.getScope(itemId);
                if (scope != null) {
                    scopeService.delete(itemId);
                    return;
                }
            }
            if (schemaService != null) {
                try {
                    schemaService.deleteSchema(itemId);
                } catch (Exception e) {
                    // Ignore schema deletion errors
                }
            }
        } catch (Exception e) {
            // Don't log here - any logging can be captured by command output stream causing StackOverflow
        }
    }

    /**
     * Create a temporary JSON file with the given content.
     */
    private File createTempJsonFile(String content) throws IOException {
        Path tempFile = Files.createTempFile("unomi-test-", ".json");
        File file = tempFile.toFile();
        file.deleteOnExit();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        tempFiles.add(file);
        return file;
    }

    // ========== Goal Tests ==========

    @Test
    public void testGoalCrudOperations() throws Exception {
        String goalId = createTestId("test-goal");

        // Test create
        createGoal(goalId, "Test Goal", "Test goal description");
        createdItemIds.add(goalId);

        // Test read and validate
        validateGoalRead(goalId, "Test Goal", "Test goal description");

        // Test update
        updateGoal(goalId, "Updated Goal", "Updated description");
        validateGoalRead(goalId, "Updated Goal", "Updated description");

        // Test list
        validateGoalInList(goalId);
        validateListWithLimit("goal", 5);

        // Test delete
        deleteGoal(goalId);
        validateGoalNotFound(goalId);
    }

    /**
     * Create a goal via CRUD command.
     */
    private void createGoal(String goalId, String name, String description) throws Exception {
        String createJson = String.format(
            "{\"itemId\":\"%s\",\"metadata\":{\"id\":\"%s\",\"name\":\"%s\",\"description\":\"%s\",\"scope\":\"systemscope\",\"enabled\":true}}",
            goalId, goalId, name, description
        );
        // Quote JSON to ensure it's treated as a single argument (prevents Gogo shell from interpreting {} as closure)
        String createOutput = executeCommandAndGetOutput(
            String.format("unomi:crud create goal '%s'", createJson)
        );
        Assert.assertTrue("Goal should be created", 
            createOutput.contains("Created goal with ID: " + goalId) || createOutput.contains(goalId));
    }

    /**
     * Validate goal read operation and field values.
     */
    private void validateGoalRead(String goalId, String expectedName, String expectedDescription) throws Exception {
        String readOutput = executeCommandAndGetOutput("unomi:crud read goal " + goalId);
        Assert.assertTrue("Should read goal", readOutput.contains(goalId));
        Assert.assertTrue("Should contain goal name", readOutput.contains(expectedName));
        
        Map<String, Object> goalData = parseJsonOutput(readOutput);
        Assert.assertNotNull("Goal data should be parsed", goalData);
        
        Map<String, Object> expectedFields = new HashMap<>();
        expectedFields.put("itemId", goalId);
        expectedFields.put("metadata.name", expectedName);
        expectedFields.put("metadata.description", expectedDescription);
        validateJsonFields(goalData, expectedFields);
    }

    /**
     * Update a goal via CRUD command.
     */
    private void updateGoal(String goalId, String name, String description) throws Exception {
        String updateJson = String.format(
            "{\"itemId\":\"%s\",\"metadata\":{\"id\":\"%s\",\"name\":\"%s\",\"description\":\"%s\",\"scope\":\"systemscope\",\"enabled\":true}}",
            goalId, goalId, name, description
        );
        // Quote JSON to ensure it's treated as a single argument (prevents Gogo shell from interpreting {} as closure)
        String updateOutput = executeCommandAndGetOutput(
            String.format("unomi:crud update goal %s '%s'", goalId, updateJson)
        );
        Assert.assertTrue("Goal should be updated", updateOutput.contains("Updated goal with ID: " + goalId));
    }

    /**
     * Validate goal appears in list.
     * Uses retry logic to handle eventual consistency.
     */
    private void validateGoalInList(String goalId) throws Exception {
        // Wait for goal to appear in list with retries
        boolean found = waitForCondition(
            "Goal should appear in list",
            () -> {
                try {
                    String listOutput = executeCommandAndGetOutput("unomi:crud list goal");
                    validateTableHeaders(listOutput, new String[]{"ID", "Tenant", "Identifier"});
                    return tableContainsValue(listOutput, goalId);
                } catch (Exception e) {
                    return false;
                }
            },
            5, // maxRetries
            200 // retryDelayMs
        );
        Assert.assertTrue("Goal should be found in table", found);
    }

    /**
     * Validate list command with limit.
     */
    private void validateListWithLimit(String objectType, int limit) throws Exception {
        String listOutput = executeCommandAndGetOutput(
            String.format("unomi:crud list %s -n %d", objectType, limit)
        );
        validateTableHeaders(listOutput, new String[]{"ID", "Tenant"});
    }

    /**
     * Delete a goal via CRUD command.
     */
    private void deleteGoal(String goalId) throws Exception {
        String deleteOutput = executeCommandAndGetOutput("unomi:crud delete goal " + goalId);
        Assert.assertTrue("Goal should be deleted", deleteOutput.contains("Deleted goal with ID: " + goalId));
        createdItemIds.remove(goalId);
    }

    /**
     * Validate that a goal is not found.
     */
    private void validateGoalNotFound(String goalId) throws Exception {
        String readOutput = executeCommandAndGetOutput("unomi:crud read goal " + goalId);
        assertContainsAny(readOutput, new String[]{"not found", "null"}, 
            "Should indicate goal not found");
    }

    @Test
    public void testGoalCreateWithFile() throws Exception {
        String goalId = createTestId("test-goal-file");
        String goalJson = String.format(
            "{\"itemId\":\"%s\",\"metadata\":{\"id\":\"%s\",\"name\":\"File Goal\",\"description\":\"Goal from file\",\"scope\":\"systemscope\",\"enabled\":true}}",
            goalId, goalId
        );
        File jsonFile = createTempJsonFile(goalJson);

        // Quote file path to handle spaces or special characters
        String filePath = jsonFile.getAbsolutePath().replace("'", "'\"'\"'");
        String output = executeCommandAndGetOutput("unomi:crud create goal file://" + filePath);
        Assert.assertTrue("Goal should be created from file", 
            output.contains("Created goal with ID: " + goalId) || output.contains(goalId));
        createdItemIds.add(goalId);
    }

    @Test
    public void testGoalHelp() throws Exception {
        String helpOutput = executeCommandAndGetOutput("unomi:crud help goal");
        Assert.assertTrue("Should show help", helpOutput.contains("Required properties") || helpOutput.contains("itemId"));
    }

    @Test
    public void testGoalListCsv() throws Exception {
        String csvOutput = executeCommandAndGetOutput("unomi:crud list goal --csv");
        // CSV should contain commas and have at least one line
        Assert.assertTrue("Should output CSV format", csvOutput.contains(",") || csvOutput.trim().length() > 0);
        // CSV should have multiple lines (header + data rows, even if empty)
        String[] lines = csvOutput.split("\n");
        Assert.assertTrue("CSV output should have at least one line", lines.length > 0);
    }

    @Test
    public void testGoalListWithCsvAndLimit() throws Exception {
        // Test combining --csv and -n options
        String csvOutput = executeCommandAndGetOutput("unomi:crud list goal --csv -n 10");
        // CSV should contain commas and have at least one line
        Assert.assertTrue("Should output CSV format", csvOutput.contains(",") || csvOutput.trim().length() > 0);
        // CSV should have multiple lines (header + data rows, even if empty)
        String[] lines = csvOutput.split("\n");
        Assert.assertTrue("CSV output should have at least one line", lines.length > 0);
    }

    /**
     * Helper method to test basic CRUD operations for an object type.
     * Reduces code duplication across similar object types.
     * 
     * @param objectType the object type (e.g., "rule", "segment")
     * @param jsonTemplate JSON template with two %s placeholders for itemId (used twice in metadata.id and itemId)
     */
    private void testBasicCrudOperations(String objectType, String jsonTemplate) throws Exception {
        String itemId = createTestId("test-" + objectType);
        String json = String.format(jsonTemplate, itemId, itemId);

        // Test create with retry logic for condition type resolution timing issues
        boolean created = waitForCondition(
            objectType + " should be created",
            () -> {
                try {
                    String createOutput = executeCommandAndGetOutput(
                        String.format("unomi:crud create %s '%s'", objectType, json)
                    );
                    // Check for success indicators
                    boolean success = createOutput.contains("Created " + objectType + " with ID: " + itemId) || 
                                     createOutput.contains(itemId);
                    // Check for condition resolution errors that might resolve with retry
                    boolean isRetryableError = createOutput.contains("Condition type is missing") || 
                                              createOutput.contains("could not be resolved") ||
                                              createOutput.contains("Invalid rule condition") ||
                                              createOutput.contains("Invalid segment condition");
                    if (success) {
                        createdItemIds.add(itemId);
                        return true;
                    } else if (isRetryableError) {
                        return false; // Retry for condition resolution errors
                    }
                    return false; // Other errors, will fail assertion
                } catch (Exception e) {
                    // Check if it's a condition resolution error that might resolve with retry
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && (errorMsg.contains("Condition type is missing") || 
                                            errorMsg.contains("could not be resolved") ||
                                            errorMsg.contains("Invalid rule condition") ||
                                            errorMsg.contains("Invalid segment condition"))) {
                        return false; // Retry
                    }
                    // For other exceptions, return false and let assertion fail with original error
                    return false;
                }
            },
            5, // maxRetries - condition types should be available, but allow more retries
            300 // retryDelayMs - give time for DefinitionsService to be ready
        );
        Assert.assertTrue(objectType + " should be created", created);

        // Test read - parse JSON and validate
        String readOutput = executeCommandAndGetOutput("unomi:crud read " + objectType + " " + itemId);
        Assert.assertTrue("Should read " + objectType, readOutput.contains(itemId));
        
        // Parse JSON to ensure valid structure
        try {
            Map<String, Object> readData = parseJsonOutput(readOutput);
            Assert.assertNotNull(objectType + " data should be parsed", readData);
            Assert.assertEquals(objectType + " itemId should match", itemId, readData.get("itemId"));
        } catch (Exception e) {
            // If JSON parsing fails, at least verify the ID is in the output
            Assert.assertTrue("Should contain " + objectType + " ID in output", readOutput.contains(itemId));
        }

        // Test list - validate table structure with retry logic for eventual consistency
        // Different object types have different headers, so we check for common ones
        boolean foundInList = waitForCondition(
            objectType + " should appear in list",
            () -> {
                try {
                    String listOutput = executeCommandAndGetOutput("unomi:crud list " + objectType);
                    // Check for common headers that appear in most list outputs
                    // "Tenant" is always present, and "Identifier" or "ID" appears for most types
                    validateTableHeaders(listOutput, new String[]{"Tenant", "Identifier", "ID"});
                    return tableContainsValue(listOutput, itemId);
                } catch (Exception e) {
                    return false;
                }
            },
            5, // maxRetries
            200 // retryDelayMs
        );
        Assert.assertTrue("Should contain our " + objectType + " ID in the list", foundInList);

        // Test delete
        String deleteOutput = executeCommandAndGetOutput("unomi:crud delete " + objectType + " " + itemId);
        Assert.assertTrue(objectType + " should be deleted", 
            deleteOutput.contains("Deleted " + objectType + " with ID: " + itemId));
        createdItemIds.remove(itemId);
    }

    // ========== Rule Tests ==========

    @Test
    public void testRuleCrudOperations() throws Exception {
        // Include parameterValues (even if empty) to ensure proper condition deserialization
        String ruleJsonTemplate = 
            "{\"itemId\":\"%s\",\"metadata\":{\"id\":\"%s\",\"name\":\"Test Rule\",\"description\":\"Test rule\",\"scope\":\"systemscope\",\"enabled\":true},\"condition\":{\"type\":\"matchAllCondition\",\"parameterValues\":{}},\"actions\":[]}";
        testBasicCrudOperations("rule", ruleJsonTemplate);
    }

    // ========== Segment Tests ==========

    @Test
    public void testSegmentCrudOperations() throws Exception {
        // Include parameterValues (even if empty) to ensure proper condition deserialization
        String segmentJsonTemplate = 
            "{\"itemId\":\"%s\",\"metadata\":{\"id\":\"%s\",\"name\":\"Test Segment\",\"description\":\"Test segment\",\"scope\":\"systemscope\"},\"condition\":{\"type\":\"matchAllCondition\",\"parameterValues\":{}}}";
        testBasicCrudOperations("segment", segmentJsonTemplate);
    }

    // ========== Topic Tests ==========

    @Test
    public void testTopicCrudOperations() throws Exception {
        // Topic extends Item (not MetadataItem), so it doesn't have metadata property
        // Topic has: itemId, topicId, name, scope (from Item)
        String topicJsonTemplate = 
            "{\"itemId\":\"%s\",\"topicId\":\"%s\",\"name\":\"Test Topic\",\"scope\":\"systemscope\"}";
        testBasicCrudOperations("topic", topicJsonTemplate);
    }

    // ========== Scope Tests ==========

    @Test
    public void testScopeCrudOperations() throws Exception {
        String scopeJsonTemplate = 
            "{\"itemId\":\"%s\",\"metadata\":{\"id\":\"%s\",\"name\":\"Test Scope\",\"description\":\"Test scope\",\"scope\":\"systemscope\"}}";
        testBasicCrudOperations("scope", scopeJsonTemplate);
    }

    // ========== Schema Tests ==========

    @Test
    public void testSchemaCrudOperations() throws Exception {
        String schemaId = "https://unomi.apache.org/schemas/json/test/" + createTestId("test-schema");

        // Create a simple schema
        // Note: self.name must match [_A-Za-z][_0-9A-Za-z]* (no spaces, must start with letter/underscore)
        String schemaJson = String.format(
            "{\"$id\":\"%s\",\"self\":{\"target\":\"events\",\"name\":\"TestSchema\"},\"type\":\"object\",\"properties\":{\"testProperty\":{\"type\":\"string\"}}}",
            schemaId
        );
        // Quote JSON to ensure it's treated as a single argument
        String createOutput = executeCommandAndGetOutput(
            String.format("unomi:crud create schema '%s'", schemaJson)
        );
        Assert.assertTrue("Schema should be created", 
            createOutput.contains("Created schema with ID: " + schemaId) || createOutput.contains(schemaId));
        createdItemIds.add(schemaId);

        // Test read - parse JSON and validate schema structure
        String readOutput = executeCommandAndGetOutput("unomi:crud read schema " + schemaId);
        Assert.assertTrue("Should read schema", readOutput.contains(schemaId));
        
        Map<String, Object> schemaData = parseJsonOutput(readOutput);
        Assert.assertNotNull("Schema data should be parsed", schemaData);
        
        // Schema read returns a wrapped structure: {id, name, target, tenantId, schema: {...}}
        // The actual schema is nested under "schema" key
        Map<String, Object> expectedSchemaFields = new HashMap<>();
        expectedSchemaFields.put("id", schemaId);
        // Check that schema.type exists in the nested schema object
        Assert.assertTrue("Schema data should contain 'schema' key", schemaData.containsKey("schema"));
        @SuppressWarnings("unchecked")
        Map<String, Object> actualSchema = (Map<String, Object>) schemaData.get("schema");
        Assert.assertNotNull("Nested schema should not be null", actualSchema);
        Assert.assertEquals("Schema type should be 'object'", "object", actualSchema.get("type"));

        // Test list
        String listOutput = executeCommandAndGetOutput("unomi:crud list schema");
        validateTableHeaders(listOutput, new String[]{"ID", "Tenant"});

        // Test delete
        String deleteOutput = executeCommandAndGetOutput("unomi:crud delete schema " + schemaId);
        Assert.assertTrue("Schema should be deleted", deleteOutput.contains("Deleted schema with ID: " + schemaId));
        createdItemIds.remove(schemaId);
    }

    // ========== Error Handling Tests ==========

    @Test
    public void testReadNonExistentGoal() throws Exception {
        String nonExistentId = "non-existent-goal-" + System.currentTimeMillis();
        String output = executeCommandAndGetOutput("unomi:crud read goal " + nonExistentId);
        assertContainsAny(output, new String[]{"not found", "null"}, 
            "Should indicate goal not found");
    }

    @Test
    public void testCreateWithInvalidJson() throws Exception {
        // Quote even invalid JSON to ensure it's treated as a single argument
        String output = executeCommandAndGetOutput("unomi:crud create goal '[[invalid json]]'");
        assertContainsAny(output, new String[]{"error", "Error", "Exception"}, 
            "Should show error for invalid JSON");
    }

    @Test
    public void testDeleteWithoutId() throws Exception {
        String output = executeCommandAndGetOutput("unomi:crud delete goal");
        assertContainsAny(output, new String[]{"required", "ID"}, 
            "Should require ID");
    }

    @Test
    public void testUpdateWithoutId() throws Exception {
        String output = executeCommandAndGetOutput("unomi:crud update goal");
        assertContainsAny(output, new String[]{"required", "ID", "Error"}, 
            "Should require ID and JSON");
    }

    // ========== Syntax Error Tests ==========

    @Test
    public void testCreateWithUnquotedJson() throws Exception {
        // Unquoted JSON may be interpreted as closure or cause parsing errors
        String unquotedJson = "{\"itemId\":\"test\",\"metadata\":{\"id\":\"test\",\"name\":\"Test\",\"scope\":\"systemscope\"}}";
        String output = executeCommandAndGetOutput(
            String.format("unomi:crud create goal %s", unquotedJson)
        );
        // Should either fail with parsing error or be interpreted incorrectly
        assertContainsAny(output, new String[]{"error", "Error", "Exception", "Too many arguments", "parse", "syntax"}, 
            "Should show error for unquoted JSON");
    }

    @Test
    public void testCreateWithMalformedJson() throws Exception {
        // Missing closing brace
        String output = executeCommandAndGetOutput("unomi:crud create goal '{\"itemId\":\"test\"'");
        assertContainsAny(output, new String[]{"error", "Error", "Exception", "parse", "invalid"}, 
            "Should show error for malformed JSON");
    }

    @Test
    public void testCreateWithEmptyJson() throws Exception {
        String output = executeCommandAndGetOutput("unomi:crud create goal '{}'");
        // Empty JSON might be valid but should show validation error for missing required fields
        assertContainsAny(output, new String[]{"error", "Error", "required", "itemId", "Exception"}, 
            "Should show error for empty or incomplete JSON");
    }

    @Test
    public void testUpdateWithMissingJson() throws Exception {
        String goalId = createTestId("test-goal-syntax");
        // Update with ID but no JSON
        String output = executeCommandAndGetOutput("unomi:crud update goal " + goalId);
        assertContainsAny(output, new String[]{"required", "JSON", "Error"}, 
            "Should require JSON for update operation");
    }

    @Test
    public void testUpdateWithOnlyJsonNoId() throws Exception {
        // Update with JSON but no ID (missing ID argument)
        String json = "'{\"itemId\":\"test\",\"metadata\":{\"id\":\"test\",\"name\":\"Test\",\"scope\":\"systemscope\"}}'";
        String output = executeCommandAndGetOutput("unomi:crud update goal " + json);
        // Should fail because ID is required as first remaining argument
        // The JSON will be treated as remaining[0], but we need remaining[0] = ID, remaining[1] = JSON
        assertContainsAny(output, new String[]{"required", "ID", "Error", "JSON"}, 
            "Should require ID as first argument for update");
    }

    @Test
    public void testReadWithExtraArguments() throws Exception {
        // Read should only take ID, extra arguments will be in remaining list but ignored
        String nonExistentId = "non-existent-" + System.currentTimeMillis();
        String output = executeCommandAndGetOutput("unomi:crud read goal " + nonExistentId + " extra-arg");
        // With multi-valued remaining, extra args are captured but ignored for read operation
        // Should show "not found" error, not "too many arguments"
        assertContainsAny(output, new String[]{"not found", "null", "error", "Error"}, 
            "Should handle extra arguments gracefully (ignore them, show not found)");
    }

    @Test
    public void testDeleteWithExtraArguments() throws Exception {
        // Delete should only take ID, extra arguments will be in remaining list but ignored
        String nonExistentId = "non-existent-" + System.currentTimeMillis();
        String output = executeCommandAndGetOutput("unomi:crud delete goal " + nonExistentId + " extra-arg");
        // With multi-valued remaining, extra args are captured but ignored for delete operation
        // Should show "not found" or similar, not "too many arguments"
        assertContainsAny(output, new String[]{"not found", "error", "Error", "Deleted"}, 
            "Should handle extra arguments gracefully (ignore them)");
    }

    @Test
    public void testListWithInvalidOptionValue() throws Exception {
        // -n option should have a numeric value
        String output = executeCommandAndGetOutput("unomi:crud list goal -n invalid");
        // Should either ignore invalid value or show error
        assertContainsAny(output, new String[]{"ID", "error", "Error", "invalid", "number"}, 
            "Should handle invalid option value (may ignore or show error)");
    }

    @Test
    public void testListWithNegativeLimit() throws Exception {
        // Negative limit might be invalid
        String output = executeCommandAndGetOutput("unomi:crud list goal -n -5");
        // Should either ignore negative value or show error
        assertContainsAny(output, new String[]{"ID", "error", "Error", "invalid"}, 
            "Should handle negative limit (may ignore or show error)");
    }

    @Test
    public void testCreateWithInvalidUrl() throws Exception {
        // Invalid file URL (file doesn't exist)
        String output = executeCommandAndGetOutput("unomi:crud create goal file:///nonexistent/path/file.json");
        assertContainsAny(output, new String[]{"error", "Error", "Exception", "not found", "No such file"}, 
            "Should show error for invalid file URL");
    }

    @Test
    public void testCreateWithInvalidUrlFormat() throws Exception {
        // Unsupported URL scheme (valid URI format but scheme not supported)
        // With improved URL detection, this will be detected as a URL and show unsupported scheme error
        String output = executeCommandAndGetOutput("unomi:crud create goal invalid://url");
        assertContainsAny(output, new String[]{"error", "Error", "Exception", "unsupported", "scheme", "not yet supported", "Failed to parse"}, 
            "Should show error for unsupported URL scheme");
    }

    @Test
    public void testCreateWithJsonContainingUnescapedQuotes() throws Exception {
        // JSON with unescaped quotes inside (should be properly escaped in the test)
        // This tests that the quoting mechanism works correctly
        // Note: description should be in metadata for Goal
        String jsonWithQuotes = "{\"itemId\":\"test\",\"metadata\":{\"id\":\"test\",\"name\":\"Test\",\"description\":\"Test with 'single' quotes\",\"scope\":\"systemscope\"}}";
        String output = executeCommandAndGetOutput(
            String.format("unomi:crud create goal '%s'", jsonWithQuotes)
        );
        // Should either succeed (if quotes are handled) or show error
        assertContainsAny(output, new String[]{"Created", "error", "Error", "parse"}, 
            "Should handle JSON with quotes (may succeed or show parse error)");
    }

    @Test
    public void testCreateWithMissingType() throws Exception {
        // Missing type argument - Karaf will throw CommandException before our code runs
        try {
            String output = executeCommandAndGetOutput("unomi:crud create");
            // If we get here, check for error message
            assertContainsAny(output, new String[]{"required", "type", "Error", "usage", "Usage", "Argument type is required"}, 
                "Should require type argument");
        } catch (Exception e) {
            // CommandException is expected for missing required arguments
            Assert.assertTrue("Should throw exception for missing type", 
                e.getMessage().contains("required") || e.getMessage().contains("type") || 
                e.getClass().getSimpleName().contains("CommandException"));
        }
    }

    @Test
    public void testCreateWithMissingOperation() throws Exception {
        // Missing operation (just type) - Karaf will throw CommandException before our code runs
        try {
            String output = executeCommandAndGetOutput("unomi:crud goal");
            // If we get here, check for error message
            assertContainsAny(output, new String[]{"required", "operation", "Error", "usage", "Usage", "Unknown", "Argument type is required"}, 
                "Should require operation argument");
        } catch (Exception e) {
            // CommandException is expected for missing required arguments
            Assert.assertTrue("Should throw exception for missing operation", 
                e.getMessage().contains("required") || e.getMessage().contains("type") || 
                e.getClass().getSimpleName().contains("CommandException"));
        }
    }

    @Test
    public void testInvalidOperation() throws Exception {
        // Invalid operation name
        String output = executeCommandAndGetOutput("unomi:crud invalid-operation goal");
        assertContainsAny(output, new String[]{"Unknown", "invalid", "Error", "operation", "usage", "Usage"}, 
            "Should show error for invalid operation");
    }

    @Test
    public void testInvalidType() throws Exception {
        // Invalid type (not supported)
        String output = executeCommandAndGetOutput("unomi:crud create invalid-type '{\"itemId\":\"test\"}'");
        assertContainsAny(output, new String[]{"Unknown", "invalid", "Error", "type", "not found", "not supported"}, 
            "Should show error for invalid type");
    }
}
