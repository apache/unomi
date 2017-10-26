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
package org.apache.unomi.sfdc.services;

import org.apache.cxf.common.util.StringUtils;
import org.apache.unomi.api.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * An persistence item that contains the configuration to the Salesforce service.
 */
public class SFDCConfiguration extends Item {

    /**
     * The ImportConfiguration ITEM_TYPE
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "sfdcConfiguration";

    private String sfdcLoginEndpoint;
    private String sfdcUserUsername;
    private String sfdcUserPassword;
    private String sfdcUserSecurityToken;
    private String sfdcConsumerKey;
    private String sfdcConsumerSecret;
    private String sfdcChannel;
    private String sfdcFieldMappings;
    private String sfdcFieldMappingsIdentifier;
    private long sfdcSessionTimeout = 15 * 60 * 1000L; // 15 minutes by default

    private Map<String, String> unomiToSfdcFieldMappings = new HashMap<>();
    private Map<String, String> sfdcToUnomiFieldMappings = new HashMap<>();

    private String unomiIdentifierField;
    private String sfdcIdentifierField;

    private boolean sfdcCheckIfContactExistBeforeLeadCreation;

    public SFDCConfiguration() { }

    public String getSfdcLoginEndpoint() {
        return sfdcLoginEndpoint;
    }

    public void setSfdcLoginEndpoint(String sfdcLoginEndpoint) {
        this.sfdcLoginEndpoint = sfdcLoginEndpoint;
    }

    public String getSfdcUserUsername() {
        return sfdcUserUsername;
    }

    public void setSfdcUserUsername(String sfdcUserUsername) {
        this.sfdcUserUsername = sfdcUserUsername;
    }

    public String getSfdcUserPassword() {
        return sfdcUserPassword;
    }

    public void setSfdcUserPassword(String sfdcUserPassword) {
        this.sfdcUserPassword = sfdcUserPassword;
    }

    public String getSfdcUserSecurityToken() {
        return sfdcUserSecurityToken;
    }

    public void setSfdcUserSecurityToken(String sfdcUserSecurityToken) {
        this.sfdcUserSecurityToken = sfdcUserSecurityToken;
    }

    public String getSfdcConsumerKey() {
        return sfdcConsumerKey;
    }

    public void setSfdcConsumerKey(String sfdcConsumerKey) {
        this.sfdcConsumerKey = sfdcConsumerKey;
    }

    public String getSfdcConsumerSecret() {
        return sfdcConsumerSecret;
    }

    public void setSfdcConsumerSecret(String sfdcConsumerSecret) {
        this.sfdcConsumerSecret = sfdcConsumerSecret;
    }

    public String getSfdcChannel() {
        return sfdcChannel;
    }

    public void setSfdcChannel(String sfdcChannel) {
        this.sfdcChannel = sfdcChannel;
    }

    public String getSfdcFieldMappings() {
        return sfdcFieldMappings;
    }

    public void setSfdcFieldMappings(String sfdcFieldMappings) {
        this.sfdcFieldMappings = sfdcFieldMappings;
        String[] mappings = sfdcFieldMappings.split(",");
        if (mappings != null && mappings.length > 0) {
            for (String mapping : mappings) {
                String[] parts = mapping.split("=");
                if (parts != null && parts.length == 2) {
                    unomiToSfdcFieldMappings.put(parts[0], parts[1]);
                    sfdcToUnomiFieldMappings.put(parts[1], parts[0]);
                }
            }
        }
    }

    public void setSfdcFieldMappingsIdentifier(String sfdcFieldMappingsIdentifier) {
        this.sfdcFieldMappingsIdentifier = sfdcFieldMappingsIdentifier;
        String[] sfdcFieldMappingsIdentifierParts = sfdcFieldMappingsIdentifier.split("=");
        if (sfdcFieldMappingsIdentifierParts != null && sfdcFieldMappingsIdentifierParts.length == 2) {
            unomiIdentifierField = sfdcFieldMappingsIdentifierParts[0];
            sfdcIdentifierField = sfdcFieldMappingsIdentifierParts[1];
        }
    }

    public long getSfdcSessionTimeout() {
        return sfdcSessionTimeout;
    }

    public void setSfdcSessionTimeout(long sfdcSessionTimeout) {
        this.sfdcSessionTimeout = sfdcSessionTimeout;
    }

    public boolean isSfdcCheckIfContactExistBeforeLeadCreation() {
        return sfdcCheckIfContactExistBeforeLeadCreation;
    }

    public void setSfdcCheckIfContactExistBeforeLeadCreation(boolean sfdcCheckIfContactExistBeforeLeadCreation) {
        this.sfdcCheckIfContactExistBeforeLeadCreation = sfdcCheckIfContactExistBeforeLeadCreation;
    }

    public Map<String, String> getUnomiToSfdcFieldMappings() {
        return unomiToSfdcFieldMappings;
    }

    public Map<String, String> getSfdcToUnomiFieldMappings() {
        return sfdcToUnomiFieldMappings;
    }

    public String getUnomiIdentifierField() {
        return unomiIdentifierField;
    }

    public String getSfdcIdentifierField() {
        return sfdcIdentifierField;
    }

    public boolean isComplete() {
        return (!StringUtils.isEmpty(sfdcLoginEndpoint) &&
                !StringUtils.isEmpty(sfdcUserUsername) &&
                !StringUtils.isEmpty(sfdcUserPassword) &&
                !StringUtils.isEmpty(sfdcUserSecurityToken) &&
                !StringUtils.isEmpty(sfdcConsumerKey) &&
                !StringUtils.isEmpty(sfdcConsumerSecret) &&
                !StringUtils.isEmpty(sfdcFieldMappingsIdentifier));
    }

}
