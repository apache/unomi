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
 * An extension of {@link Profile} to handle merge strategy and deletion when importing profiles
 */
public class ProfileToImport extends Profile {

    private List<String> propertiesToOverwrite;
    private String mergingProperty;
    private boolean profileToDelete;
    private boolean overwriteExistingProfiles;


    public List<String> getPropertiesToOverwrite() {
        return this.propertiesToOverwrite;
    }

    public void setPropertiesToOverwrite(List<String> propertiesToOverwrite) {
        this.propertiesToOverwrite = propertiesToOverwrite;
    }

    public boolean isProfileToDelete() {
        return this.profileToDelete;
    }

    public void setProfileToDelete(boolean profileToDelete) {
        this.profileToDelete = profileToDelete;
    }

    public boolean isOverwriteExistingProfiles() {
        return this.overwriteExistingProfiles;
    }

    /**
     * Sets the overwriteExistingProfiles flag.
     * @param overwriteExistingProfiles flag used to specify if we want to overwrite existing profiles
     */
    public void setOverwriteExistingProfiles(boolean overwriteExistingProfiles) {
        this.overwriteExistingProfiles = overwriteExistingProfiles;
    }

    public String getMergingProperty() {
        return this.mergingProperty;
    }

    /**
     * Sets the merging property.
     * @param mergingProperty property used to check if the profile exist when merging
     */
    public void setMergingProperty(String mergingProperty) {
        this.mergingProperty = mergingProperty;
    }



}
