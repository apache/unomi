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
package org.apache.unomi.extension.encryption;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.tenants.TenantTransformationListener;
import org.apache.unomi.extension.encryption.config.EncryptionConfig;
import org.apache.unomi.extension.encryption.services.AESEncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DefaultTenantTransformationListener implements TenantTransformationListener {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTenantTransformationListener.class);

    private EncryptionConfig config;
    private AESEncryptionService encryptionService;

    public DefaultTenantTransformationListener() {
        this.encryptionService = new AESEncryptionService();
    }

    @Override
    public int getPriority() {
        return 100; // Default encryption transformation has high priority
    }

    @Override
    public Item transformItem(Item item, String tenantId) {
        if (item == null || tenantId == null) {
            logger.warn("Cannot encrypt data: item or tenantId is null");
            return item;
        }

        try {
            Set<String> sensitiveFields = config.getSensitiveFields(item.getItemType());
            if (sensitiveFields.isEmpty()) {
                return item;
            }

            byte[] encryptionKey = config.getTenantKey(tenantId);
            if (encryptionKey == null) {
                logger.error("No encryption key found for tenant {}", tenantId);
                return item;
            }

            // Encrypt fields
            encryptFields(item, sensitiveFields, encryptionKey);

            // Add encryption metadata
            item.setSystemMetadata("encrypted", true);
            item.setSystemMetadata("encryptionVersion", config.getEncryptionVersion());
            item.setSystemMetadata("encryptedFields", String.join(",", sensitiveFields));

            return item;
        } catch (Exception e) {
            logger.error("Error during encryption for item {} in tenant {}", item.getItemId(), tenantId, e);
            return item;
        }
    }

    @Override
    public Item reverseTransformItem(Item item, String tenantId) {
        if (!isItemTransformed(item) || tenantId == null) {
            return item; // Nothing to decrypt
        }

        try {
            String encryptedFieldsStr = (String) item.getSystemMetadata("encryptedFields");
            if (encryptedFieldsStr == null) {
                logger.warn("Item {} is marked as encrypted but has no encrypted fields metadata", item.getItemId());
                return item;
            }

            Set<String> encryptedFields = new HashSet<>(Arrays.asList(encryptedFieldsStr.split(",")));
            byte[] encryptionKey = config.getTenantKey(tenantId);

            if (encryptionKey == null) {
                logger.error("No encryption key found for tenant {} during decryption", tenantId);
                return item;
            }

            // Decrypt fields
            decryptFields(item, encryptedFields, encryptionKey);

            // Remove encryption metadata
            item.setSystemMetadata("encrypted", null);
            item.setSystemMetadata("encryptionVersion", null);
            item.setSystemMetadata("encryptedFields", null);

            return item;
        } catch (Exception e) {
            logger.error("Error during decryption for item {} in tenant {}", item.getItemId(), tenantId, e);
            return item;
        }
    }

    private void encryptFields(Item item, Set<String> sensitiveFields, byte[] key) throws Exception {
        Class<?> itemClass = item.getClass();
        for (Field field : itemClass.getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(item);

            if (value != null) {
                if (sensitiveFields.contains(field.getName())) {
                    field.set(item, encryptionService.encrypt(value, key));
                } else if (value instanceof Map) {
                    encryptMapFields((Map<String, Object>) value, sensitiveFields, key);
                }
            }
        }
    }

    private void encryptMapFields(Map<String, Object> map, Set<String> sensitiveFields, byte[] key) throws Exception {
        for (String fieldName : sensitiveFields) {
            Object value = map.get(fieldName);
            if (value != null) {
                map.put(fieldName, encryptionService.encrypt(value, key));
            }
        }
    }

    private void decryptFields(Item item, Set<String> encryptedFields, byte[] key) throws Exception {
        Class<?> itemClass = item.getClass();
        for (Field field : itemClass.getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(item);

            if (value != null) {
                if (encryptedFields.contains(field.getName())) {
                    field.set(item, encryptionService.decrypt(value, key));
                } else if (value instanceof Map) {
                    decryptMapFields((Map<String, Object>) value, encryptedFields, key);
                }
            }
        }
    }

    private void decryptMapFields(Map<String, Object> map, Set<String> encryptedFields, byte[] key) throws Exception {
        for (String fieldName : encryptedFields) {
            Object value = map.get(fieldName);
            if (value != null) {
                map.put(fieldName, encryptionService.decrypt(value, key));
            }
        }
    }

    public void setConfig(EncryptionConfig config) {
        this.config = config;
    }

    @Override
    public boolean isTransformationEnabled() {
        return config != null && config.isEnabled();
    }

    @Override
    public String getTransformationType() {
        return "encryption";
    }
} 