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
package org.apache.unomi.services.common.security;

import org.apache.unomi.api.security.ApiKeyHashService;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Default implementation of {@link ApiKeyHashService}, using PBKDF2WithHmacSHA512 to hash
 * API keys before they are persisted (see UNOMI-938). Plaintext keys are never stored:
 * only a salted hash and a masked, display-safe representation are kept.
 */
public class ApiKeyHashServiceImpl implements ApiKeyHashService {
    private static final String KEY_PREFIX = "unomi_v1_";
    private static final int KEY_RANDOM_BYTES = 32;
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA512";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BITS = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String generateKey() {
        byte[] randomBytes = new byte[KEY_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        StringBuilder hex = new StringBuilder(randomBytes.length * 2);
        for (byte b : randomBytes) {
            hex.append(String.format("%02X", b));
        }
        return KEY_PREFIX + hex;
    }

    @Override
    public String hash(String plainTextKey) {
        if (plainTextKey == null) {
            throw new IllegalArgumentException("plainTextKey cannot be null");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(plainTextKey.toCharArray(), salt, ITERATIONS);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    @Override
    public boolean verify(String plainTextKey, String storedHash) {
        if (plainTextKey == null || storedHash == null) {
            return false;
        }
        String[] parts = storedHash.split(":");
        if (parts.length != 3) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[2]);
            byte[] actualHash = pbkdf2(plainTextKey.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(actualHash, expectedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String mask(String plainTextKey) {
        if (plainTextKey == null) {
            return null;
        }
        String withoutPrefix = plainTextKey.startsWith(KEY_PREFIX) ? plainTextKey.substring(KEY_PREFIX.length()) : plainTextKey;
        String lastFour = withoutPrefix.length() >= 4 ? withoutPrefix.substring(withoutPrefix.length() - 4) : withoutPrefix;
        return KEY_PREFIX + "****" + lastFour;
    }

    private byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to compute PBKDF2 hash", e);
        }
    }
}
