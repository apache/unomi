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
public class ImportConfiguration extends ImportExportConfiguration {

    /**
     * The ImportConfiguration ITEM_TYPE
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "importConfig";
    private String mergingProperty;
    private boolean overwriteExistingProfiles = false;
    private List<String> propertiesToOverwrite;


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

}
