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

import org.apache.unomi.api.Profile;

import java.util.List;

/**
 * A specialized Profile class designed for import operations in Apache Unomi.
 * This class extends the standard {@link Profile} with additional properties and behaviors
 * specifically needed during the profile import process.
 *
 * <p>Key features:
 * <ul>
 *   <li>Controls which properties should be overwritten during import</li>
 *   <li>Specifies the property used for merging with existing profiles</li>
 *   <li>Handles profile deletion flags</li>
 *   <li>Controls overwrite behavior for existing profiles</li>
 * </ul>
 * </p>
 *
 * <p>Usage in Unomi:
 * <ul>
 *   <li>Used by import processors to handle profile data</li>
 *   <li>Consumed by ProfileImportService for import operations</li>
 *   <li>Supports different import strategies (merge/overwrite/delete)</li>
 * </ul>
 * </p>
 *
 * @see Profile
 * @see org.apache.unomi.router.api.services.ProfileImportService
 * @since 1.0
 */
public class ProfileToImport extends Profile {

    /** List of property names that should be overwritten during import */
    private List<String> propertiesToOverwrite;
    
    /** Property used to identify existing profiles for merging */
    private String mergingProperty;
    
    /** Flag indicating if this profile should be deleted */
    private boolean profileToDelete;
    
    /** Flag controlling whether to overwrite existing profile data */
    private boolean overwriteExistingProfiles;

    /**
     * Gets the list of properties that should be overwritten during import.
     * These properties will be updated even if they already exist in the target profile.
     *
     * @return list of property names to overwrite
     */
    public List<String> getPropertiesToOverwrite() {
        return this.propertiesToOverwrite;
    }

    /**
     * Sets the list of properties that should be overwritten during import.
     *
     * @param propertiesToOverwrite list of property names that should be overwritten
     */
    public void setPropertiesToOverwrite(List<String> propertiesToOverwrite) {
        this.propertiesToOverwrite = propertiesToOverwrite;
    }

    /**
     * Checks if this profile is marked for deletion.
     * When true, the matching profile in the system will be deleted rather than updated.
     *
     * @return true if the profile should be deleted, false otherwise
     */
    public boolean isProfileToDelete() {
        return this.profileToDelete;
    }

    /**
     * Sets whether this profile should be deleted during import.
     *
     * @param profileToDelete true to mark the profile for deletion, false otherwise
     */
    public void setProfileToDelete(boolean profileToDelete) {
        this.profileToDelete = profileToDelete;
    }

    /**
     * Checks if existing profiles should be overwritten during import.
     * When true, all properties of existing profiles will be overwritten with imported data.
     *
     * @return true if existing profiles should be overwritten, false for selective updates
     */
    public boolean isOverwriteExistingProfiles() {
        return this.overwriteExistingProfiles;
    }

    /**
     * Sets whether existing profiles should be completely overwritten during import.
     *
     * @param overwriteExistingProfiles true to overwrite all properties, false for selective updates
     */
    public void setOverwriteExistingProfiles(boolean overwriteExistingProfiles) {
        this.overwriteExistingProfiles = overwriteExistingProfiles;
    }

    /**
     * Gets the property name used for identifying existing profiles during merge operations.
     * This property is used to match imported profiles with existing ones in the system.
     *
     * @return the name of the property used for profile matching
     */
    public String getMergingProperty() {
        return this.mergingProperty;
    }

    /**
     * Sets the property name used for identifying existing profiles during merge operations.
     *
     * @param mergingProperty the name of the property to use for profile matching
     */
    public void setMergingProperty(String mergingProperty) {
        this.mergingProperty = mergingProperty;
    }
}
