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
package org.apache.unomi.router.core.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.ProfileToImport;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.core.exception.BadProfileDataFormatException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by amidani on 29/12/2016.
 */
public class LineSplitProcessor implements Processor {

    private Map<String, Integer> fieldsMapping;
    private List<String> propertiesToOverwrite;
    private String mergingProperty;
    private boolean overwriteExistingProfiles;
    private String columnSeparator;

    @Override
    public void process(Exchange exchange) throws Exception {
        //In case of one shot import we check the header and overwrite import config
        ImportConfiguration importConfigOneShot = (ImportConfiguration) exchange.getIn().getHeader(RouterConstants.HEADER_IMPORT_CONFIG_ONESHOT);
        String configType = (String) exchange.getIn().getHeader(RouterConstants.HEADER_CONFIG_TYPE);
        if (importConfigOneShot != null) {
            fieldsMapping = (Map<String, Integer>) importConfigOneShot.getProperties().get("mapping");
            propertiesToOverwrite = importConfigOneShot.getPropertiesToOverwrite();
            mergingProperty = importConfigOneShot.getMergingProperty();
            overwriteExistingProfiles = importConfigOneShot.isOverwriteExistingProfiles();
            columnSeparator = importConfigOneShot.getColumnSeparator();
        }
        String[] profileData = ((String) exchange.getIn().getBody()).split(columnSeparator, -1);
        ProfileToImport profileToImport = new ProfileToImport();
        profileToImport.setItemId(UUID.randomUUID().toString());
        profileToImport.setItemType("profile");
        profileToImport.setScope("system");
        if (profileData.length > 0 && StringUtils.isNotBlank(profileData[0])) {
            if (fieldsMapping.size() != (profileData.length - 1)) {
                throw new BadProfileDataFormatException("The mapping does not match the number of column : line [" + ((Integer) exchange.getProperty("CamelSplitIndex") + 1) + "]", new Throwable("MAPPING_COLUMN_MATCH"));
            }
            Map<String, Object> properties = new HashMap<>();
            for (String fieldMappingKey : fieldsMapping.keySet()) {
                if (profileData.length > fieldsMapping.get(fieldMappingKey)) {
                    properties.put(fieldMappingKey, profileData[fieldsMapping.get(fieldMappingKey)].trim());
                }
            }
            profileToImport.setProperties(properties);
            profileToImport.setMergingProperty(mergingProperty);
            profileToImport.setPropertiesToOverwrite(propertiesToOverwrite);
            profileToImport.setOverwriteExistingProfiles(overwriteExistingProfiles);
            if (StringUtils.isNotBlank(profileData[profileData.length - 1]) && Boolean.parseBoolean(profileData[profileData.length - 1].trim())) {
                profileToImport.setProfileToDelete(true);
            }
        } else {
            throw new BadProfileDataFormatException("Empty line : line [" + ((Integer) exchange.getProperty("CamelSplitIndex") + 1) + "]", new Throwable("EMPTY_LINE"));
        }
        exchange.getIn().setBody(profileToImport, ProfileToImport.class);
        if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
            exchange.getIn().setHeader(KafkaConstants.PARTITION_KEY, 0);
            exchange.getIn().setHeader(KafkaConstants.KEY, "1");
        }
    }

    /**
     * Setter of fieldsMapping
     *
     * @param fieldsMapping map String,Integer fieldName in ES and the matching column index in the import file
     */
    public void setFieldsMapping(Map<String, Integer> fieldsMapping) {
        this.fieldsMapping = fieldsMapping;
    }

    public void setPropertiesToOverwrite(List<String> propertiesToOverwrite) {
        this.propertiesToOverwrite = propertiesToOverwrite;
    }

    public void setOverwriteExistingProfiles(boolean overwriteExistingProfiles) {
        this.overwriteExistingProfiles = overwriteExistingProfiles;
    }

    public String getMergingProperty() {
        return this.mergingProperty;
    }

    /**
     * Sets the merging property.
     *
     * @param mergingProperty property used to check if the profile exist when merging
     */
    public void setMergingProperty(String mergingProperty) {
        this.mergingProperty = mergingProperty;
    }

    /**
     * Sets the line separator.
     *
     * @param columnSeparator property used to specify a line separator. Defaults to ','
     */
    public void setColumnSeparator(String columnSeparator) {
        this.columnSeparator = columnSeparator;
    }

}
