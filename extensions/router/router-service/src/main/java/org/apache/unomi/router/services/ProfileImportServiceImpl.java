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
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Created by amidani on 18/05/2017.
 */
@Component(immediate = true, service = ProfileImportService.class)
public class ProfileImportServiceImpl implements ProfileImportService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileImportServiceImpl.class.getName());
    @Reference
    private PersistenceService persistenceService;

    public boolean saveMergeDeleteImportedProfile(ProfileToImport profileToImport) throws InvocationTargetException, IllegalAccessException {
        logger.debug("Importing profile with ID : {}", profileToImport.getItemId());
        Profile existingProfile = new Profile();
        if(profileToImport.getProperties().get(profileToImport.getMergingProperty()) != null) {
            List<Profile> existingProfiles = persistenceService.query("properties." + profileToImport.getMergingProperty(), profileToImport.getProperties().get(profileToImport.getMergingProperty()).toString(), null, Profile.class);
            logger.debug("Query existing profile with mergingProperty: {}. Found: {}", profileToImport.getMergingProperty(), existingProfiles.size());

            //Profile already exist, and import config allow to overwrite profiles
            if (existingProfiles.size() == 1) {
                existingProfile = existingProfiles.get(0);
                if (profileToImport.isProfileToDelete()) {
                    logger.debug("Profile is to delete!");
                    persistenceService.remove(existingProfile.getItemId(), Profile.class);
                    return true;
                }
                List<String> propertiesToOverwrite = profileToImport.getPropertiesToOverwrite();
                if (profileToImport.isOverwriteExistingProfiles() && propertiesToOverwrite != null && propertiesToOverwrite.size() > 0) { // We overwrite only properties marked to overwrite
                    logger.debug("Properties to overwrite: {}", propertiesToOverwrite);
                    for (String propName : propertiesToOverwrite) {
                        existingProfile.getProperties().put(propName, profileToImport.getProperties().get(propName));
                    }
                } else { //If no property is marked to overwrite we replace the whole properties map
                    logger.debug("Overwrite all properties");
                    existingProfile.setProperties(profileToImport.getProperties());
                }
                //update segments and scores
                existingProfile.setSegments(profileToImport.getSegments());
                existingProfile.setScores(profileToImport.getScores());
            } else if (existingProfiles.size() == 0 && !profileToImport.isProfileToDelete()) {
                logger.debug("New profile to add...");
                BeanUtils.copyProperties(existingProfile, profileToImport);
            } else {
                logger.warn("{} occurences found for profile with {} = {}. Profile import is skipped", existingProfiles.size(),
                        profileToImport.getMergingProperty(), profileToImport.getProperties().get(profileToImport.getMergingProperty()).toString());
            }
            logger.debug("-------------------------------------");
            if (!profileToImport.isProfileToDelete()) {
                return persistenceService.save(existingProfile, true);
            } else {
                return false;
            }
        } else {
            logger.error("Merging property '{}' has no value in the current profile.", profileToImport.getMergingProperty());
            return false;
        }
    }
}
