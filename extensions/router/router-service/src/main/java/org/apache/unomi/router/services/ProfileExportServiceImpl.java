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

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.RouterUtils;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.apache.unomi.router.api.services.ProfileExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by amidani on 30/06/2017.
 */
public class ProfileExportServiceImpl extends AbstractCustomServiceImpl implements ProfileExportService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileExportServiceImpl.class.getName());

    private ConfigSharingService configSharingService;

    public String extractProfilesBySegment(ExportConfiguration exportConfiguration) {
        List<Profile> profileList = persistenceService.query("segments", (String) exportConfiguration.getProperty("segment"), null, Profile.class);
        String csvContent = "";
        for (Profile profile : profileList) {
            csvContent += convertProfileToCSVLine(profile, exportConfiguration);
            csvContent += exportConfiguration.getLineSeparator();
        }
        logger.debug("Exporting {} extracted profiles.", profileList.size());
        Map<String, Object> returnMap = new HashMap();

        Map execution = new HashMap();
        execution.put(RouterConstants.KEY_EXECS_DATE, new Date().getTime());
        execution.put(RouterConstants.KEY_EXECS_EXTRACTED, profileList.size());

        exportConfiguration = (ExportConfiguration) RouterUtils.addExecutionEntry(exportConfiguration, execution, Integer.parseInt((String) configSharingService.getProperty(RouterConstants.KEY_HISTORY_SIZE)));
        persistenceService.save(exportConfiguration);

        returnMap.put(RouterConstants.KEY_CSV_CONTENT, csvContent);
        returnMap.put(RouterConstants.KEY_EXECS, execution);

        return csvContent;
    }

    public String convertProfileToCSVLine(Profile profile, ExportConfiguration exportConfiguration) {
        Map<String, String> mapping = (Map<String, String>) exportConfiguration.getProperty("mapping");
        String lineToWrite = "";
        for (int i = 0; i < mapping.size(); i++) {
            String propertyName = mapping.get(String.valueOf(i));
            lineToWrite += profile.getProperty(propertyName) != null ? profile.getProperty(propertyName) : "";
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
