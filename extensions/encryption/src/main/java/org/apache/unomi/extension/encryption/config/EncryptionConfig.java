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
package org.apache.unomi.extension.encryption.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class EncryptionConfig {

    private static final Logger logger = LoggerFactory.getLogger(EncryptionConfig.class);

    private boolean enabled;
    private String encryptionVersion;
    private Map<String, Set<String>> sensitiveFieldsMap;
    private Map<String, byte[]> tenantKeys;

    public EncryptionConfig() {
        this.sensitiveFieldsMap = new HashMap<>();
        this.tenantKeys = new HashMap<>();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEncryptionVersion() {
        return encryptionVersion;
    }

    public void setEncryptionVersion(String encryptionVersion) {
        this.encryptionVersion = encryptionVersion;
    }

    public Set<String> getSensitiveFields(String itemType) {
        return sensitiveFieldsMap.getOrDefault(itemType, Collections.emptySet());
    }

    public void setSensitiveFields(String itemType, Set<String> fields) {
        sensitiveFieldsMap.put(itemType, fields);
    }

    public byte[] getTenantKey(String tenantId) {
        return tenantKeys.get(tenantId);
    }

    public void setTenantKey(String tenantId, byte[] key) {
        tenantKeys.put(tenantId, key);
    }
}
