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
 * Router-side import job definition for bulk loading Unomi items.
 * Extends {@link ImportExportConfiguration} with import-specific column mappings,
 * type mappings, and merge behavior. Camel routes in the router extension read
 * this configuration to stream external files into the persistence layer.
 */
public class ImportConfiguration extends ImportExportConfiguration {

    /**
     * Persistence item type for router import configurations.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "importConfig";
    private String mergingProperty;
    private boolean overwriteExistingProfiles = false;
    private List<String> propertiesToOverwrite;
    private boolean hasHeader = false;
    private boolean hasDeleteColumn = false;

    /**
     * Returns the property used to match existing profiles during import.
     *
     * @return the merging property name
     */
    public String getMergingProperty() {
        return mergingProperty;
    }

    /**
     * Sets the property used to match existing profiles during import.
     *
     * @param mergingProperty property used to check whether the profile exists when merging
     */
    public void setMergingProperty(String mergingProperty) {
        this.mergingProperty = mergingProperty;
    }

    /**
     * Returns whether existing profiles are overwritten during import.
     *
     * @return {@code true} when existing profiles must be overwritten
     */
    public boolean isOverwriteExistingProfiles() {
        return this.overwriteExistingProfiles;
    }

    /**
     * Sets whether existing profiles are overwritten during import.
     *
     * @param overwriteExistingProfiles {@code true} to overwrite existing profiles
     */
    public void setOverwriteExistingProfiles(boolean overwriteExistingProfiles) {
        this.overwriteExistingProfiles = overwriteExistingProfiles;
    }

    /**
     * Returns profile property names that may be overwritten during import.
     *
     * @return the property names to overwrite, or {@code null} when not restricted
     */
    public List<String> getPropertiesToOverwrite() {
        return propertiesToOverwrite;
    }

    /**
     * Sets the list of profile properties that may be overwritten during import.
     *
     * @param propertiesToOverwrite property names to overwrite
     */
    public void setPropertiesToOverwrite(List<String> propertiesToOverwrite) {
        this.propertiesToOverwrite = propertiesToOverwrite;
    }

    /**
     * Returns whether the import file includes a header row.
     *
     * @return {@code true} when the first row is a header
     */
    public boolean isHasHeader() {
        return this.hasHeader;
    }

    /**
     * Sets whether the import file includes a header row.
     *
     * @param hasHeader {@code true} when the first row is a header
     */
    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    /**
     * Returns whether the import file includes a delete column.
     *
     * @return {@code true} when a delete column is present
     */
    public boolean isHasDeleteColumn() {
        return this.hasDeleteColumn;
    }

    /**
     * Sets whether the import file includes a delete column.
     *
     * @param hasDeleteColumn {@code true} when a delete column is present
     */
    public void setHasDeleteColumn(boolean hasDeleteColumn) {
        this.hasDeleteColumn = hasDeleteColumn;
    }
}
