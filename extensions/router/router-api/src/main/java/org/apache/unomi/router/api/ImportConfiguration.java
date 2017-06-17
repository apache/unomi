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
import org.apache.unomi.api.MetadataItem;

import javax.lang.model.type.MirroredTypeException;
import java.util.*;

/**
 * Created by amidani on 28/04/2017.
 */
public class ImportConfiguration extends Item {

    /**
     * The ImportConfiguration ITEM_TYPE
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "importConfig";
    private String name;
    private String description;
    private String configType;
    private Map<String, Object> properties = new HashMap<>();
    private String mergingProperty;
    private boolean overwriteExistingProfiles = false;
    private List<String> propertiesToOverwrite;

    private String columnSeparator = ",";
    private String lineSeparator = "\n";
    private boolean active = false;
    private boolean running = false;

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
     * Retrieves the name of the import configuration
     * @return the name of the import configuration
     */
    public String getName() { return this.name; }

    /**
     * Sets the name of the import configuration
     * @param name the name of the import configuration
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Retrieves the description of the import configuration
     * @return the description of the import configuration
     */
    public String getDescription() { return this.description; }

    /**
     * Sets the description of the import configuration
     * @param description the description of the import configuration
     */
    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * Retrieves the config type of the import configuration
     * @return the config type of the import configuration
     */
    public String getConfigType() { return this.configType; }

    /**
     * Sets the config type of the import configuration
     * @param configType the config type of the import configuration
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
     * Retrieves a Map of all property name - value pairs for this import configuration.
     *
     * @return a Map of all property name - value pairs for this import configuration
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Sets the property name - value pairs for this import configuration.
     *
     * @param properties a Map containing the property name - value pairs for this import configuration
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public String getMergingProperty() {
        return mergingProperty;
    }

    /**
     * Sets the merging property.
     * @param mergingProperty property used to check if the profile exist when merging
     */
    public void setMergingProperty(String mergingProperty) {
        this.mergingProperty = mergingProperty;
    }


    /**
     * Retrieves the import configuration active flag.
     *
     * @return true if the import configuration is active false if not
     */
    public boolean isActive() {
        return this.active;
    }

    /**
     * Sets the active flag true/false.
     *
     * @param active a boolean to set to active or inactive the import configuration
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Retrieves the import configuration running flag.
     *
     * @return true if the import configuration is running false if not
     */
    public boolean isRunning() {
        return this.running;
    }

    /**
     * Sets the running flag true/false.
     *
     * @param running a boolean to set to running or inactive the import configuration
     */
    public void setRunning(boolean running) {
        this.running = running;
    }

    /**
     * Retrieves the import configuration overwriteExistingProfiles flag.
     *
     * @return true if during the import existing profiles must be overwritten
     */
    public boolean isOverwriteExistingProfiles() {
        return this.overwriteExistingProfiles;
    }

    /**
     * Sets the overwriteExistingProfiles flag true/false.
     *
     * @param overwriteExistingProfiles a boolean to set overwriteExistingProfiles in the import configuration
     */
    public void setOverwriteExistingProfiles(boolean overwriteExistingProfiles) {
        this.overwriteExistingProfiles = overwriteExistingProfiles;
    }

    public List<String> getPropertiesToOverwrite() {
        return propertiesToOverwrite;
    }

    public void setPropertiesToOverwrite(List<String> propertiesToOverwrite) {
        this.propertiesToOverwrite = propertiesToOverwrite;
    }

    /**
     * Retrieves the column separator.
     */
    public String getColumnSeparator() {
        return this.columnSeparator;
    }

    /**
     * Sets the column separator.
     * @param columnSeparator property used to specify a line separator. Defaults to ','
     */
    public void setColumnSeparator(String columnSeparator) {
        if(this.columnSeparator !=null) {
            this.columnSeparator = columnSeparator;
        }
    }

    /**
     * Retrieves the line separator.
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
     * Retrieves the executions
     */
    public List<Map<String, Object>> getExecutions() {
        return this.executions;
    }


    /**
     * Sets the executions
     * @param executions
     */
    public void setExecutions(List<Map<String, Object>> executions) {
        this.executions = executions;
    }


}
