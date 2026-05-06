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
package org.apache.unomi.shell.dev.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.karaf.shell.api.action.*;
import org.apache.karaf.shell.api.action.lifecycle.Init;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.table.ShellTable;

import java.io.PrintStream;
import org.apache.unomi.shell.dev.completers.IdCompleter;
import org.apache.unomi.shell.dev.completers.OperationCompleter;
import org.apache.unomi.shell.dev.completers.TypeCompleter;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Command(scope = "unomi", name = "crud", description = "Perform CRUD operations on Unomi objects")
@Service
public class UnomiCrudCommand implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnomiCrudCommand.class.getName());

    private static final ObjectMapper OBJECT_MAPPER = CustomObjectMapper.getObjectMapper();

    @Reference
    private BundleContext bundleContext;

    @Reference
    private Session session;

    @Argument(index = 0, name = "operation", description = "Operation to perform (create/read/update/delete/list/help)", required = true)
    @Completion(OperationCompleter.class)
    private String operation;

    @Argument(index = 1, name = "type", description = "Object type", required = true)
    @Completion(TypeCompleter.class)
    private String type;

    // Multi-valued argument that captures all remaining tokens after type
    // ⚠️ IMPORTANT: Only the last argument (highest index) can be multi-valued in Karaf
    // Since this is at index 2 (last argument), it can safely be multi-valued
    // For create: remaining[0] = JSON/URL
    // For read/delete: remaining[0] = ID
    // For update: remaining[0] = ID, remaining[1] = JSON/URL
    // For list: remaining contains all remaining tokens (--csv, -n, 50, etc.) for manual parsing
    @Argument(index = 2, name = "remaining", description = "ID/JSON/URL (for create/read/update/delete) or remaining tokens (for list)", required = false, multiValued = true)
    @Completion(IdCompleter.class)  // Could be enhanced to detect context
    private List<String> remaining;

    // Option fields for list operation (populated via manual parsing from remaining)
    @Option(name = "--csv", description = "Output list in CSV format", required = false, multiValued = false)
    private boolean csv;

    @Option(name = "-n", aliases = "--max-entries", description = "Maximum number of entries to list", required = false)
    private Integer maxEntries;

    @Init
    public void init() {
        LOGGER.debug("UnomiCrudCommand init");
    }

    /**
     * Check if a token is a max-entries option flag.
     * 
     * @param token the token to check
     * @return true if the token is -n or --max-entries
     */
    private boolean isMaxEntriesOption(String token) {
        return "-n".equals(token) || "--max-entries".equals(token);
    }

    /**
     * Parse max-entries option value from the remaining list.
     * Validates that the value is a positive integer.
     * 
     * @param remaining the remaining argument list
     * @param index the index of the option flag
     * @return the parsed integer value (must be > 0), or null if invalid/missing
     */
    private Integer parseMaxEntriesValue(List<String> remaining, int index) {
        if (index + 1 >= remaining.size()) {
            return null;
        }
        try {
            int value = Integer.parseInt(remaining.get(index + 1));
            // Only accept positive values
            if (value <= 0) {
                LOGGER.warn("Invalid max-entries value (must be positive): " + value);
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid number for max-entries option: " + remaining.get(index + 1));
            return null;
        }
    }

    /**
     * Parse list-specific options from the remaining argument list.
     * This implements Option 1: Simple Manual Parsing from the redesign proposal.
     * 
     * Note: If --csv was already set by Karaf's option parser (when placed before arguments),
     * we preserve that value. Otherwise, we parse it from the remaining list.
     * 
     * @param remaining List of remaining tokens after type (e.g., ["--csv", "-n", "50"])
     */
    private void parseListOptions(List<String> remaining) {
        // Preserve csv value if already set by Karaf's option parser (when --csv comes before arguments)
        boolean csv = this.csv;
        Integer maxEntries = this.maxEntries;
        
        if (remaining == null || remaining.isEmpty()) {
            // Keep existing values if already set by Karaf
            return;
        }
        
        for (int i = 0; i < remaining.size(); i++) {
            String token = remaining.get(i);
            
            if ("--csv".equals(token)) {
                csv = true;
            } else if (isMaxEntriesOption(token)) {
                Integer value = parseMaxEntriesValue(remaining, i);
                if (value != null) {
                    maxEntries = value;
                    i++; // Skip the next token as it's the value
                }
            }
            // Ignore unknown tokens (could log warning)
        }
        
        // Populate option fields
        this.csv = csv;
        this.maxEntries = maxEntries;
    }

    /**
     * Check if remaining argument list has at least the specified number of non-empty elements.
     * 
     * @param remaining the remaining argument list
     * @param minSize minimum number of elements required
     * @return true if valid, false otherwise
     */
    private boolean hasMinimumRemainingArgs(List<String> remaining, int minSize) {
        if (remaining == null || remaining.size() < minSize) {
            return false;
        }
        for (int i = 0; i < minSize; i++) {
            if (StringUtils.isBlank(remaining.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parse JSON properties from remaining argument or URL with error handling.
     * This method wraps parseProperties() and handles exceptions, providing consistent error messages.
     * 
     * @param jsonOrUrl JSON string or URL from remaining argument
     * @param console Console for error output
     * @return Map of properties, or null if parsing failed or error occurred
     */
    private Map<String, Object> parsePropertiesWithErrorHandling(String jsonOrUrl, PrintStream console) {
        final String errorMsg = "Error: Failed to parse JSON or URL: " + jsonOrUrl;
        try {
            Map<String, Object> props = parseProperties(jsonOrUrl);
            if (props == null) {
                console.println(errorMsg);
            }
            return props;
        } catch (Exception e) {
            console.println(errorMsg);
            console.println("Error details: " + e.getMessage());
            return null;
        }
    }

    /**
     * Strip surrounding quotes from a string if present.
     * 
     * @param str the string to process
     * @return the string with quotes removed, or original if no quotes
     */
    private String stripQuotes(String str) {
        if (StringUtils.isEmpty(str) || str.length() < 2) {
            return str;
        }
        // Check for single quotes
        if (str.charAt(0) == '\'' && str.charAt(str.length() - 1) == '\'') {
            return str.substring(1, str.length() - 1);
        }
        // Check for double quotes
        if (str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"') {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    /**
     * Check if a string is a valid URL by attempting to parse it as a URI.
     * This method supports all URL schemes that Pax URL supports:
     * - file:// (file protocol)
     * - http://, https:// (HTTP/HTTPS protocols)
     * - mvn: (Maven protocol)
     * - war: (War protocol)
     * - Any other valid URI scheme
     * 
     * The method uses Java's URI class to validate the scheme, which is more
     * robust than simple string matching and supports all standard and custom schemes.
     * 
     * @param str the string to check
     * @return true if the string is a valid URI with a scheme, false otherwise
     */
    private boolean isUrl(String str) {
        if (StringUtils.isBlank(str)) {
            return false;
        }
        
        // JSON strings typically start with { or [, so they're not URLs
        String trimmed = str.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return false;
        }
        
        try {
            URI uri = new URI(trimmed);
            // A valid URI with a scheme is considered a URL
            // getScheme() returns null for relative URIs, which are not URLs
            return uri.getScheme() != null;
        } catch (URISyntaxException e) {
            // Not a valid URI, so not a URL
            return false;
        }
    }

    /**
     * Parse JSON from a file URL.
     * 
     * @param fileUrl the file:// URL
     * @return the parsed JSON as a Map
     * @throws Exception if there's an error reading or parsing the file
     */
    private Map<String, Object> parseFileUrl(String fileUrl) throws Exception {
        URI uri = new URI(fileUrl);
        String scheme = uri.getScheme();
        
        if (!"file".equals(scheme)) {
            throw new IllegalArgumentException("Expected file:// URL, got: " + fileUrl);
        }
        
        // Handle file:// URLs - getPath() handles both file:///path and file://path
        String filePath = uri.getPath();
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("Invalid file URL: " + fileUrl);
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result = OBJECT_MAPPER.readValue(Files.readString(Paths.get(filePath)), Map.class);
        return result;
    }

    /**
     * Parse JSON properties from remaining argument or URL.
     * Supports:
     * - Inline JSON string: {"itemId":"test"} (quoted or unquoted)
     * - File URL: file:///path/to/file.json
     * - HTTP/HTTPS URL: http://example.com/data.json (not yet implemented)
     * - Maven URL: mvn:groupId/artifactId/version (not yet implemented)
     * - War URL: war:file://path/to.war (not yet implemented)
     * - Any other Pax URL supported scheme (not yet implemented)
     * 
     * Note: If JSON is quoted in the command (e.g., '{"itemId":"test"}'), 
     * the Gogo parser will strip the quotes before passing to this method.
     * This method handles both quoted and unquoted JSON strings.
     * 
     * @param jsonOrUrl JSON string or URL from remaining argument
     * @return Map of properties, or null if invalid
     * @throws Exception if there's an error parsing the JSON or reading the URL
     */
    private Map<String, Object> parseProperties(String jsonOrUrl) throws Exception {
        if (StringUtils.isBlank(jsonOrUrl)) {
            return null;
        }
        
        String trimmed = stripQuotes(StringUtils.trim(jsonOrUrl));
        
        if (isUrl(trimmed)) {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            
            if ("file".equals(scheme)) {
                return parseFileUrl(trimmed);
            } else {
                // Other URL schemes (http, https, mvn, war, etc.) are not yet supported
                // In the future, we could use Pax URL's URLStreamHandler to resolve these
                throw new UnsupportedOperationException(
                    "URL scheme '" + scheme + "' is not yet supported. " +
                    "Currently only file:// URLs are supported. Use file:// or inline JSON.");
            }
        }
        
        // Treat as inline JSON
        @SuppressWarnings("unchecked")
        Map<String, Object> result = OBJECT_MAPPER.readValue(trimmed, Map.class);
        return result;
    }

    /**
     * Validate that operation and type are provided.
     * 
     * @param console console for error output
     * @return true if valid, false otherwise
     */
    private boolean validateOperationAndType(PrintStream console) {
        if (StringUtils.isBlank(operation)) {
            console.println("Error: Operation is required");
            console.println("Usage: unomi:crud <operation> <type> [remaining...]");
            console.println("Available operations: create, read, update, delete, list, help");
            return false;
        }
        
        if (StringUtils.isBlank(type)) {
            console.println("Error: Type is required");
            console.println("Usage: unomi:crud <operation> <type> [remaining...]");
            return false;
        }
        
        return true;
    }

    /**
     * Execute the appropriate handler for the given operation.
     * 
     * @param cmd the CrudCommand instance
     * @param operationLower the lowercase operation name
     * @param console console for output
     * @return the result of the operation
     * @throws Exception if the operation fails
     */
    private Object executeOperation(CrudCommand cmd, String operationLower, PrintStream console) throws Exception {
        switch (operationLower) {
            case "create":
                return handleCreate(cmd, console);
                
            case "read":
                return handleRead(cmd, console);
                
            case "update":
                return handleUpdate(cmd, console);
                
            case "delete":
                return handleDelete(cmd, console);
                
            case "list":
                return handleList(cmd, console);
                
            case "help":
                console.println("Properties for " + type + ":");
                console.println(cmd.getPropertiesHelp());
                return null;
                
            default:
                console.println("Unknown operation: " + operation);
                console.println("Available operations: create, read, update, delete, list, help");
                return null;
        }
    }

    /**
     * Find and execute the CrudCommand for the given type.
     * 
     * @param console console for output
     * @return true if a handler was found and executed, false otherwise
     * @throws Exception if the operation fails
     */
    private boolean findAndExecuteCommand(PrintStream console) throws Exception {
        ServiceReference<?>[] refs = bundleContext.getAllServiceReferences(CrudCommand.class.getName(), null);
        if (refs == null) {
            return false;
        }
        
        String operationLower = operation.toLowerCase();
        for (ServiceReference<?> ref : refs) {
            CrudCommand cmd = (CrudCommand) bundleContext.getService(ref);
            if (cmd.getObjectType().equals(type)) {
                try {
                    executeOperation(cmd, operationLower, console);
                    return true; // Handler found and executed
                } finally {
                    bundleContext.ungetService(ref);
                }
            }
        }
        return false; // No handler found
    }

    @Override
    public Object execute() throws Exception {
        PrintStream console = session.getConsole();
        
        if (!validateOperationAndType(console)) {
            return null;
        }
        
        boolean handlerFound = findAndExecuteCommand(console);
        if (!handlerFound) {
            console.println("No handler found for object type: " + type);
        }
        return null;
    }

    /**
     * Validate that remaining argument list has at least one non-empty element for create operation.
     * 
     * @param remaining the remaining argument list
     * @param console console for error output
     * @return true if valid, false otherwise
     */
    private boolean validateCreateRemaining(List<String> remaining, PrintStream console) {
        if (!hasMinimumRemainingArgs(remaining, 1)) {
            console.println("Error: JSON string or URL is required for create operation");
            console.println("Usage: unomi:crud create <type> <json|url>");
            console.println("Example: unomi:crud create goal '{\"itemId\":\"test\",\"enabled\":true}'");
            console.println("Example: unomi:crud create goal file:///path/to/file.json");
            console.println("Note: Quote JSON strings to ensure they're treated as a single argument");
            return false;
        }
        return true;
    }

    /**
     * Handle create operation.
     * Syntax: unomi:crud create <type> <json|url>
     * remaining[0] = JSON string or URL
     */
    private Object handleCreate(CrudCommand cmd, PrintStream console) throws Exception {
        if (!validateCreateRemaining(remaining, console)) {
            return null;
        }
        
        String jsonOrUrl = remaining.get(0);
        Map<String, Object> createProps = parsePropertiesWithErrorHandling(jsonOrUrl, console);
        if (createProps == null) {
            return null;
        }
        
        // Validate that we have at least some properties (empty JSON {} is not valid)
        if (createProps.isEmpty()) {
            console.println("Error: Empty JSON object is not valid. Please provide required properties.");
            console.println("Usage: unomi:crud create <type> <json|url>");
            return null;
        }
        
        String newId = cmd.create(createProps);
        if (newId == null) {
            console.println("Error: Failed to create " + type + ". The create operation returned null.");
            return null;
        }
        console.println("Created " + type + " with ID: " + newId);
        return null;
    }

    /**
     * Validate that remaining argument list has at least one non-empty element.
     * 
     * @param remaining the remaining argument list
     * @param operation the operation name (for error messages)
     * @param console console for error output
     * @return true if valid, false otherwise
     */
    private boolean validateRemainingNotEmpty(List<String> remaining, String operation, PrintStream console) {
        if (!hasMinimumRemainingArgs(remaining, 1)) {
            console.println("Error: ID is required for " + operation + " operation");
            console.println("Usage: unomi:crud " + operation + " <type> <id>");
            console.println("Example: unomi:crud " + operation + " goal test-goal-123");
            return false;
        }
        return true;
    }

    /**
     * Handle read operation.
     * Syntax: unomi:crud read <type> <id>
     * remaining[0] = Object ID
     */
    private Object handleRead(CrudCommand cmd, PrintStream console) throws Exception {
        if (!validateRemainingNotEmpty(remaining, "read", console)) {
            return null;
        }
        
        String id = remaining.get(0);
        Map<String, Object> obj = cmd.read(id);
        if (obj != null) {
            console.println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj));
        } else {
            console.println(type + " not found with ID: " + id);
        }
        return null;
    }

    /**
     * Handle update operation.
     * Syntax: unomi:crud update <type> <id> <json|url>
     * remaining[0] = Object ID
     * remaining[1] = JSON string or URL
     */
    private Object handleUpdate(CrudCommand cmd, PrintStream console) throws Exception {
        if (!hasMinimumRemainingArgs(remaining, 2)) {
            console.println("Error: ID and JSON/URL are required for update operation");
            console.println("Usage: unomi:crud update <type> <id> <json|url>");
            console.println("Example: unomi:crud update goal test-goal-123 '{\"itemId\":\"test-goal-123\",\"enabled\":false}'");
            console.println("Note: Quote JSON strings to ensure they're treated as a single argument");
            return null;
        }
        
        String id = remaining.get(0);
        String jsonOrUrl = remaining.get(1);
        
        // hasMinimumRemainingArgs already ensures both id and jsonOrUrl are non-blank
        Map<String, Object> updateProps = parsePropertiesWithErrorHandling(jsonOrUrl, console);
        if (updateProps == null) {
            return null;
        }
        
        cmd.update(id, updateProps);
        console.println("Updated " + type + " with ID: " + id);
        return null;
    }

    /**
     * Handle delete operation.
     * Syntax: unomi:crud delete <type> <id>
     * remaining[0] = Object ID
     */
    private Object handleDelete(CrudCommand cmd, PrintStream console) throws Exception {
        if (!validateRemainingNotEmpty(remaining, "delete", console)) {
            return null;
        }
        
        String id = remaining.get(0);
        cmd.delete(id);
        console.println("Deleted " + type + " with ID: " + id);
        return null;
    }

    /**
     * Handle list operation.
     * Syntax: unomi:crud list <type> [--csv] [-n <number>]
     * remaining contains all remaining tokens (--csv, -n, 50, etc.) for manual parsing
     */
    private Object handleList(CrudCommand cmd, PrintStream console) throws Exception {
        // Parse list-specific options from remaining argument
        parseListOptions(remaining);
        
        String[] headers = cmd.getHeaders();
        if (headers == null || headers.length == 0) {
            console.println("Error: No headers available for " + type);
            return null;
        }
        
        // Ensure limit is positive (default to 100 if null or invalid)
        int limit = (maxEntries != null && maxEntries > 0) ? maxEntries : 100;
        
        if (csv) {
            // Generate proper CSV output using Apache Commons CSV
            cmd.buildCsvOutput(console, headers, limit);
        } else {
            // Generate table output
            ShellTable table = new ShellTable();
            for (String header : headers) {
                table.column(header);
            }
            cmd.buildRows(table, limit);
            table.print(console, true);
        }
        return null;
    }
}
