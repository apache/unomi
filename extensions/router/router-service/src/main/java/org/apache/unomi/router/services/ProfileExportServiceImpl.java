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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.RouterUtils;
import org.apache.unomi.router.api.services.ProfileExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by amidani on 30/06/2017.
 */
public class ProfileExportServiceImpl implements ProfileExportService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileExportServiceImpl.class.getName());


    private PersistenceService persistenceService;
    private DefinitionsService definitionsService;
    private ConfigSharingService configSharingService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setConfigSharingService(ConfigSharingService configSharingService) {
        this.configSharingService = configSharingService;
    }

    public String extractProfilesBySegment(ExportConfiguration exportConfiguration) {
        Collection<PropertyType> propertiesDef = persistenceService.query("target", "profiles", null, PropertyType.class);

        Condition segmentCondition = new Condition();
        segmentCondition.setConditionType(definitionsService.getConditionType("profileSegmentCondition"));
        segmentCondition.setParameter("segments", Collections.singletonList((String) exportConfiguration.getProperty("segment")));
        segmentCondition.setParameter("matchType", "in");

        StringBuilder csvContent = new StringBuilder();
        PartialList<Profile> profiles = persistenceService.query(segmentCondition, null, Profile.class, 0, 1000, "10m");
        int counter = 0;
        while (profiles != null && profiles.getList().size() > 0) {
            List<Profile> scrolledProfiles = profiles.getList();
            for (Profile profile : scrolledProfiles) {
                csvContent.append(convertProfileToCSVLine(profile, exportConfiguration, propertiesDef));
                csvContent.append(RouterUtils.getCharFromLineSeparator(exportConfiguration.getLineSeparator()));
            }
            counter += scrolledProfiles.size();
            profiles = persistenceService.continueScrollQuery(Profile.class, profiles.getScrollIdentifier(), profiles.getScrollTimeValidity());
        }

        Map execution = new HashMap();
        execution.put(RouterConstants.KEY_EXECS_DATE, new Date().getTime());
        execution.put(RouterConstants.KEY_EXECS_EXTRACTED, counter);

        exportConfiguration = (ExportConfiguration) RouterUtils.addExecutionEntry(exportConfiguration, execution, Integer.parseInt((String) configSharingService.getProperty(RouterConstants.KEY_HISTORY_SIZE)));
        persistenceService.save(exportConfiguration);

        return csvContent.toString();
    }

    public String convertProfileToCSVLine(Profile profile, ExportConfiguration exportConfiguration) {
        // TODO: UNOMI-759 querying this everytimes
        Collection<PropertyType> propertiesDef = persistenceService.query("target", "profiles", null, PropertyType.class);
        return convertProfileToCSVLine(profile, exportConfiguration, propertiesDef);
    }

    public String convertProfileToCSVLine(Profile profile, ExportConfiguration exportConfiguration, Collection<PropertyType> propertiesDef) {
        Map<String, String> mapping = (Map<String, String>) exportConfiguration.getProperty("mapping");
        String lineToWrite = "";
        for (int i = 0; i < mapping.size(); i++) {
            String propertyName = mapping.get(String.valueOf(i));
            if (propertyName == null) {
                logger.error("No index {} found in the provided mapping!", i);
                return "";
            }
            PropertyType propType = RouterUtils.getPropertyTypeById(propertiesDef, propertyName);
            Object propertyValue = profile.getProperty(propertyName);
            if (propType != null && BooleanUtils.isTrue(propType.isMultivalued())) {
                if (propertyValue != null) {
                    List<String> multiValue = (List<String>) propertyValue;
                    lineToWrite += StringUtils.isNotBlank(exportConfiguration.getMultiValueDelimiter()) ? exportConfiguration.getMultiValueDelimiter().charAt(0) : "";
                    int j = 0;
                    for (String entry : multiValue) {
                        lineToWrite += entry.replaceAll("\"", "\"\"");
                        if (j + 1 < multiValue.size()) {
                            lineToWrite += exportConfiguration.getMultiValueSeparator();
                        }
                        j++;
                    }
                    lineToWrite += StringUtils.isNotBlank(exportConfiguration.getMultiValueDelimiter()) ? exportConfiguration.getMultiValueDelimiter().charAt(1) : "";
                } else {
                    lineToWrite += "";
                }
            } else {
                if(propertyValue != null) {
                    propertyValue = propertyValue.toString().replaceAll("\"", "\"\"");
                    if (StringUtils.contains(propertyValue.toString(), exportConfiguration.getColumnSeparator())) {
                        propertyValue = "\"" + propertyValue + "\"";
                    }
                    lineToWrite += propertyValue.toString();
                } else {
                    lineToWrite += "";
                }
            }
            if (i + 1 < mapping.size()) {
                lineToWrite += exportConfiguration.getColumnSeparator();
            }
        }
        return lineToWrite;
    }

}
