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
package org.apache.unomi.extension.encryption.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.*;

public class AESEncryptionService {

    private static final Logger logger = LoggerFactory.getLogger(AESEncryptionService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    public Object encrypt(Object value, byte[] key) throws Exception {
        if (value == null || key == null) {
            return null;
        }

        try {
            if (value instanceof Map) {
                return encryptMap((Map<?, ?>) value, key);
            } else if (value instanceof Collection) {
                return encryptCollection((Collection<?>) value, key);
            } else if (value instanceof Object[]) {
                return encryptArray((Object[]) value, key);
            } else {
                return encryptValue(value.toString(), key);
            }
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            throw e;
        }
    }

    private String encryptValue(String value, byte[] key) throws Exception {
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance(ALGORITHM);

        byte[] iv = generateIV();
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        byte[] encrypted = cipher.doFinal(value.getBytes());
        byte[] combined = new byte[iv.length + encrypted.length];

        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    private byte[] generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    private Map<Object, Object> encryptMap(Map<?, ?> map, byte[] key) throws Exception {
        Map<Object, Object> encrypted = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            encrypted.put(entry.getKey(), encrypt(entry.getValue(), key));
        }
        return encrypted;
    }

    private Collection<Object> encryptCollection(Collection<?> collection, byte[] key) throws Exception {
        Collection<Object> encrypted = collection instanceof List ? new ArrayList<>() : new HashSet<>();
        for (Object item : collection) {
            encrypted.add(encrypt(item, key));
        }
        return encrypted;
    }

    private Object[] encryptArray(Object[] array, byte[] key) throws Exception {
        Object[] encrypted = new Object[array.length];
        for (int i = 0; i < array.length; i++) {
            encrypted[i] = encrypt(array[i], key);
        }
        return encrypted;
    }

    public Object decrypt(Object value, byte[] key) throws Exception {
        if (value == null || key == null) {
            return null;
        }

        try {
            if (value instanceof Map) {
                return decryptMap((Map<?, ?>) value, key);
            } else if (value instanceof Collection) {
                return decryptCollection((Collection<?>) value, key);
            } else if (value instanceof Object[]) {
                return decryptArray((Object[]) value, key);
            } else {
                return decryptValue(value.toString(), key);
            }
        } catch (Exception e) {
            logger.error("Decryption failed", e);
            throw e;
        }
    }

    private String decryptValue(String encryptedValue, byte[] key) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedValue);

        // Extract IV and encrypted data
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

        SecretKey secretKey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
        byte[] decrypted = cipher.doFinal(encrypted);

        return new String(decrypted);
    }

    private Map<Object, Object> decryptMap(Map<?, ?> map, byte[] key) throws Exception {
        Map<Object, Object> decrypted = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            decrypted.put(entry.getKey(), decrypt(entry.getValue(), key));
        }
        return decrypted;
    }

    private Collection<Object> decryptCollection(Collection<?> collection, byte[] key) throws Exception {
        Collection<Object> decrypted = collection instanceof List ? new ArrayList<>() : new HashSet<>();
        for (Object item : collection) {
            decrypted.add(decrypt(item, key));
        }
        return decrypted;
    }

    private Object[] decryptArray(Object[] array, byte[] key) throws Exception {
        Object[] decrypted = new Object[array.length];
        for (int i = 0; i < array.length; i++) {
            decrypted[i] = decrypt(array[i], key);
        }
        return decrypted;
    }
}
