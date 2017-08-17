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
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.services.ConfigSharingService;
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
public class ProfileExportServiceImpl extends AbstractCustomServiceImpl implements ProfileExportService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileExportServiceImpl.class.getName());

    private ConfigSharingService configSharingService;

    public String extractProfilesBySegment(ExportConfiguration exportConfiguration) {
        List<Profile> profileList = persistenceService.query("segments", (String) exportConfiguration.getProperty("segment"), null, Profile.class);
        StringBuilder csvContent = new StringBuilder();
        for (Profile profile : profileList) {
            csvContent.append(convertProfileToCSVLine(profile, exportConfiguration));
            csvContent.append(RouterUtils.getCharFromLineSeparator(exportConfiguration.getLineSeparator()));
        }
        logger.debug("Exporting {} extracted profiles.", profileList.size());

        Map execution = new HashMap();
        execution.put(RouterConstants.KEY_EXECS_DATE, new Date().getTime());
        execution.put(RouterConstants.KEY_EXECS_EXTRACTED, profileList.size());

        exportConfiguration = (ExportConfiguration) RouterUtils.addExecutionEntry(exportConfiguration, execution, Integer.parseInt((String) configSharingService.getProperty(RouterConstants.KEY_HISTORY_SIZE)));
        persistenceService.save(exportConfiguration);

        return csvContent.toString();
    }

    public String convertProfileToCSVLine(Profile profile, ExportConfiguration exportConfiguration) {
        Collection<PropertyType> propertiesDef = persistenceService.query("target", "profiles", null, PropertyType.class);
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

    public void setConfigSharingService(ConfigSharingService configSharingService) {
        this.configSharingService = configSharingService;
    }

}
