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
package org.apache.unomi.router.services;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.unomi.api.Profile;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.router.api.ProfileToImport;
import org.apache.unomi.router.api.services.ProfileImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Created by amidani on 18/05/2017.
 */
public class ProfileImportServiceImpl implements ProfileImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileImportServiceImpl.class.getName());

    private PersistenceService persistenceService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public boolean saveMergeDeleteImportedProfile(ProfileToImport profileToImport) throws InvocationTargetException, IllegalAccessException {
        LOGGER.debug("Importing profile with ID : {}", profileToImport.getItemId());
        Profile existingProfile = new Profile();
        if(profileToImport.getProperties().get(profileToImport.getMergingProperty()) != null) {
            List<Profile> existingProfiles = persistenceService.query("properties." + profileToImport.getMergingProperty(), profileToImport.getProperties().get(profileToImport.getMergingProperty()).toString(), null, Profile.class);
            LOGGER.debug("Query existing profile with mergingProperty: {}. Found: {}", profileToImport.getMergingProperty(), existingProfiles.size());

            //Profile already exist, and import config allow to overwrite profiles
            if (existingProfiles.size() == 1) {
                existingProfile = existingProfiles.get(0);
                if (profileToImport.isProfileToDelete()) {
                    LOGGER.debug("Profile is to delete!");
                    persistenceService.remove(existingProfile.getItemId(), Profile.class);
                    return true;
                }
                List<String> propertiesToOverwrite = profileToImport.getPropertiesToOverwrite();
                if (profileToImport.isOverwriteExistingProfiles() && propertiesToOverwrite != null && propertiesToOverwrite.size() > 0) { // We overwrite only properties marked to overwrite
                    LOGGER.debug("Properties to overwrite: {}", propertiesToOverwrite);
                    for (String propName : propertiesToOverwrite) {
                        existingProfile.getProperties().put(propName, profileToImport.getProperties().get(propName));
                    }
                } else { //If no property is marked to overwrite we replace the whole properties map
                    LOGGER.debug("Overwrite all properties");
                    existingProfile.setProperties(profileToImport.getProperties());
                }
                //update segments and scores
                existingProfile.setSegments(profileToImport.getSegments());
                existingProfile.setScores(profileToImport.getScores());
            } else if (existingProfiles.size() == 0 && !profileToImport.isProfileToDelete()) {
                LOGGER.debug("New profile to add...");
                BeanUtils.copyProperties(existingProfile, profileToImport);
            } else {
                LOGGER.warn("{} occurrence(s) found for profile with {} = {}. Profile import is skipped", existingProfiles.size(),
                        profileToImport.getMergingProperty(), profileToImport.getProperties().get(profileToImport.getMergingProperty()).toString());
            }
            LOGGER.debug("-------------------------------------");
            if (!profileToImport.isProfileToDelete()) {
                return persistenceService.save(existingProfile, true);
            } else {
                return false;
            }
        } else {
            LOGGER.error("Merging property '{}' has no value in the current profile.", profileToImport.getMergingProperty());
            return false;
        }
    }
}
