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
package org.apache.unomi.shell.dev.services;

import org.apache.karaf.shell.support.table.ShellTable;

import java.util.List;
import java.util.Map;

/**
 * Interface for implementing CRUD (Create, Read, Update, Delete) operations in Karaf shell commands for Unomi objects.
 * This interface is designed to be implemented by OSGi components that provide shell command functionality for different
 * types of Unomi objects (e.g., rules, segments, profiles, events).
 *
 * Implementations must:
 * 1. Be annotated with @Component(service = CrudCommand.class, immediate = true)
 * 2. Reference the appropriate Unomi service using @Reference
 * 3. Handle JSON serialization/deserialization of objects
 * 4. Provide property name and value completion for shell command auto-completion
 * 5. Use server-side pagination and sorting in list operations to handle large datasets
 * 6. Extend ListCommandSupport to implement rich tabular output
 *
 * The list functionality is provided by extending ListCommandSupport and implementing:
 * 1. getHeaders() - Define column headers for the table
 * 2. buildDataTable() - Build table data using Query with proper sorting and pagination
 * 3. getSortBy() - Define sort criteria (default: metadata.lastModified:desc)
 * 4. buildRow() - Convert an item to a table row
 *
 * Example usage in shell:
 * unomi:crud create rule --file rule.json
 * unomi:crud read rule rule_id
 * unomi:crud update rule rule_id --file updated_rule.json
 * unomi:crud delete rule rule_id
 * unomi:crud list rule [--csv]
 * unomi:crud help rule
 */
public interface CrudCommand {
    /**
     * Get the type of object this command handles. This is used to register the command
     * for a specific object type in the Unomi shell command system.
     *
     * @return the object type identifier (e.g., "rule", "segment", "profile", "event")
     */
    String getObjectType();

    /**
     * Create a new object in Unomi from a map of properties. The properties are typically
     * deserialized from a JSON file provided by the user.
     *
     * Implementations should:
     * 1. Convert the properties map to the appropriate Unomi object type
     * 2. Validate required properties are present
     * 3. Set any default values or metadata
     * 4. Save the object using the appropriate Unomi service
     *
     * @param properties the object properties from JSON
     * @return the ID of the created object
     * @throws IllegalArgumentException if required properties are missing or invalid
     */
    String create(Map<String, Object> properties);

    /**
     * Read an object by ID and return its properties. The properties will be serialized
     * to JSON for display to the user.
     *
     * Implementations should:
     * 1. Load the object using the appropriate Unomi service
     * 2. Return null if the object doesn't exist
     * 3. Convert the object to a map of properties
     *
     * @param id the object ID
     * @return map of object properties, or null if not found
     */
    Map<String, Object> read(String id);

    /**
     * Update an existing object with new properties. The properties are typically
     * deserialized from a JSON file provided by the user.
     *
     * Implementations should:
     * 1. Ensure the ID in properties matches the target ID
     * 2. Convert the properties map to the appropriate Unomi object type
     * 3. Validate required properties are present
     * 4. Update the object using the appropriate Unomi service
     *
     * @param id the object ID to update
     * @param properties the new object properties from JSON
     * @throws IllegalArgumentException if required properties are missing or invalid
     */
    void update(String id, Map<String, Object> properties);

    /**
     * Delete an object by ID.
     *
     * Implementations should:
     * 1. Delete the object using the appropriate Unomi service
     * 2. Handle any cleanup or cascading deletes if necessary
     *
     * @param id the object ID to delete
     */
    void delete(String id);

    /**
     * Get help text describing the properties supported by this object type.
     * This is displayed when the user runs the help command.
     *
     * The help text should include:
     * 1. Required properties with descriptions
     * 2. Optional properties with descriptions
     * 3. Property types and formats
     * 4. Examples or additional notes
     *
     * @return formatted help text for object properties
     */
    String getPropertiesHelp();

    /**
     * Get completions for object IDs based on a prefix. Used for shell command
     * auto-completion when the user is entering an object ID.
     *
     * Implementations should use their service's query capabilities to efficiently
     * search for matching IDs rather than loading all objects.
     *
     * @param prefix the current input prefix to filter completions
     * @return list of matching object IDs
     */
    default List<String> completeId(String prefix) {
        // Implementations should override this with an efficient query
        return List.of();
    }

    /**
     * Get completions for property names based on a prefix. Used for shell command
     * auto-completion when the user is editing a JSON file.
     *
     * Implementations should:
     * 1. Define a static list of valid property names
     * 2. Filter the list by the given prefix
     *
     * @param prefix the current input prefix to filter completions
     * @return list of matching property names
     */
    default List<String> completePropertyNames(String prefix) {
        return List.of(); // Default implementation returns no completions
    }

    /**
     * Get completions for property values based on the property name and prefix.
     * Used for shell command auto-completion when the user is editing a JSON file.
     *
     * Implementations should:
     * 1. Handle specific property names that have predefined values
     * 2. Filter the possible values by the given prefix
     * 3. Return empty list for properties without predefined values
     *
     * @param propertyName the property being completed
     * @param prefix the current input prefix to filter completions
     * @return list of possible values for the property
     */
    default List<String> completePropertyValue(String propertyName, String prefix) {
        return List.of(); // Default implementation returns no completions
    }

    /**
     * Get the column headers for the list output table.
     *
     * @return array of column headers
     */
    String[] getHeaders();

    /**
     * Build the rows for the list output table.
     *
     * @param table the table to add rows to
     * @param maxEntries maximum number of entries to include
     */
    void buildRows(ShellTable table, int maxEntries);
}
