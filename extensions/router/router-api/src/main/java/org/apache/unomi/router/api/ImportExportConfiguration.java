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
package org.apache.unomi.router.api;

import org.apache.unomi.api.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base configuration class for import and export operations in Apache Unomi.
 * This class serves as the foundation for both ImportConfiguration and ExportConfiguration,
 * providing common configuration properties and behaviors needed for data transfer operations.
 *
 * <p>Key features and responsibilities:
 * <ul>
 *   <li>Defines common configuration properties for import/export operations</li>
 *   <li>Manages separators and delimiters for CSV-like file formats</li>
 *   <li>Tracks execution status and history</li>
 *   <li>Handles configuration activation/deactivation</li>
 * </ul>
 * </p>
 *
 * <p>Usage in Unomi:
 * <ul>
 *   <li>Used by ImportExportConfigurationService to manage data transfer configurations</li>
 *   <li>Consumed by Camel routes to determine how to process data</li>
 *   <li>Referenced by import/export processors to format data correctly</li>
 * </ul>
 * </p>
 *
 * <p>Configuration properties include:
 * <ul>
 *   <li>name - unique identifier for the configuration</li>
 *   <li>configType - type of configuration (import/export)</li>
 *   <li>columnSeparator - character used to separate columns (default: ",")</li>
 *   <li>lineSeparator - character used to separate lines (default: "\n")</li>
 *   <li>multiValueSeparator - character used to separate multiple values (default: ";")</li>
 *   <li>active - whether the configuration is currently active</li>
 *   <li>status - current status of the configuration</li>
 *   <li>executions - history of execution attempts</li>
 * </ul>
 * </p>
 *
 * @see org.apache.unomi.router.api.services.ImportExportConfigurationService
 * @since 1.0
 */
public class ImportExportConfiguration extends Item {

    private String name;
    private String description;
    private String configType;
    private Map<String, Object> properties = new HashMap<>();
    private String columnSeparator = ",";
    private String lineSeparator = "\n";
    private String multiValueSeparator = ";";
    private String multiValueDelimiter = "";
    private boolean active;
    private String status;

    private List<Map<String, Object>> executions = new ArrayList();

    /**
     * Sets the property identified by the specified name to the specified value. If a property with that name already exists, replaces its value, otherwise adds the new
     * property with the specified name and value.
     *
     * @param name  the name of the property to set
     * @param value the value of the property
     */
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Retrieves the display name of this configuration.
     *
     * @return the name of this configuration
     */
    public String getName() { return this.name; }

    /**
     * Sets the display name of this configuration.
     *
     * @param name the name of this configuration
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Retrieves the human-readable description of this configuration.
     *
     * @return the description of this configuration
     */
    public String getDescription() { return this.description; }

    /**
     * Sets the human-readable description of this configuration.
     *
     * @param description the description of this configuration
     */
    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * Retrieves the configuration type (for example import vs export semantics used by the router).
     *
     * @return the config type of this configuration
     */
    public String getConfigType() { return this.configType; }

    /**
     * Sets the configuration type.
     *
     * @param configType the config type for this configuration
     */
    public void setConfigType(String configType) {
        this.configType = configType;
    }

    /**
     * Retrieves the property identified by the specified name.
     *
     * @param name the name of the property to retrieve
     * @return the value of the specified property or {@code null} if no such property exists
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Retrieves a map of all property name/value pairs for this configuration.
     *
     * @return a map of all property name/value pairs for this configuration
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Returns whether this configuration is active (eligible for scheduled or triggered runs).
     *
     * @return {@code true} if this configuration is active, {@code false} otherwise
     */
    public boolean isActive() {
        return this.active;
    }

    /**
     * Sets whether this configuration is active.
     *
     * @param active {@code true} to activate, {@code false} to deactivate
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Retrieves the status of the last execution for this configuration.
     *
     * @return status of the last execution, or {@code null} if none
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * Sets the status of the last execution for this configuration.
     *
     * @param status the status of the last execution
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Retrieves the column separator.
     * @return column separator
     */
    public String getColumnSeparator() {
        return this.columnSeparator;
    }

    /**
     * Sets the column separator used when reading or writing delimited text (typically CSV).
     *
     * @param columnSeparator the column delimiter; defaults to {@code ","} when not overridden
     */
    public void setColumnSeparator(String columnSeparator) {
        if (columnSeparator != null) {
            this.columnSeparator = columnSeparator;
        }
    }

    /**
     * Retrieves the line separator.
     * @return the line separator
     */
    public String getLineSeparator() {
        return this.lineSeparator;
    }

    /**
     * Sets the line separator.
     * @param lineSeparator property used to specify a line separator. Defaults to '\n'
     */
    public void setLineSeparator(String lineSeparator) {
        if(lineSeparator != null) {
            this.lineSeparator = lineSeparator;
        }
    }

    /**
     * Returns the separator used between multiple values within a single field.
     *
     * @return the multi-value separator (often {@code ";"})
     */
    public String getMultiValueSeparator() {
        return this.multiValueSeparator;
    }


    /**
     * Sets the multi value separator.
     *
     * @param multiValueSeparator multi value separator
     */
    public void setMultiValueSeparator(String multiValueSeparator) {
        this.multiValueSeparator = multiValueSeparator;
    }

    /**
     * Returns the delimiter wrapping multi-valued fields when serialized.
     *
     * @return the multi-value delimiter (may be empty when not used)
     */
    public String getMultiValueDelimiter() {
        return this.multiValueDelimiter;
    }


    /**
     * Sets the multi value delimiter.
     *
     * @param multiValueDelimiter multi value delimiter
     */
    public void setMultiValueDelimiter(String multiValueDelimiter) {
        this.multiValueDelimiter = multiValueDelimiter;
    }

    /**
     * Returns the history of execution records for this configuration (timestamps, counts, errors, etc.).
     *
     * @return the list of execution maps; may be empty
     */
    public List<Map<String, Object>> getExecutions() {
        return this.executions;
    }


    /**
     * Replaces the execution history for this configuration.
     *
     * @param executions the new execution history list
     */
    public void setExecutions(List<Map<String, Object>> executions) {
        this.executions = executions;
    }


}

