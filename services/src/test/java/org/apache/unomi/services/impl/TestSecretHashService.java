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
package org.apache.unomi.services.impl;

import org.apache.unomi.api.security.SecretHashService;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Fast {@link SecretHashService} for in-memory test doubles such as {@link TestTenantService}.
 * Uses deterministic hashing so key generation and verification stay consistent without crypto cost.
 * Production-grade hashing is covered by {@link org.apache.unomi.services.security.SecretHashServiceImplTest}.
 */
public class TestSecretHashService implements SecretHashService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String hash(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext cannot be null");
        }
        return "test:" + Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean verify(String plaintext, String storedHash) {
        if (plaintext == null || storedHash == null) {
            return false;
        }
        return storedHash.equals(hash(plaintext));
    }

    @Override
    public String generateRandomSecret(int randomByteLength) {
        if (randomByteLength <= 0) {
            throw new IllegalArgumentException("randomByteLength must be positive");
        }
        byte[] randomBytes = new byte[randomByteLength];
        SECURE_RANDOM.nextBytes(randomBytes);
        StringBuilder hex = new StringBuilder(randomBytes.length * 2);
        for (byte b : randomBytes) {
            hex.append(String.format("%02X", b));
        }
        return hex.toString();
    }

    @Override
    public String mask(String plaintext, String displayPrefix) {
        if (plaintext == null) {
            return null;
        }
        String prefix = displayPrefix != null ? displayPrefix : "";
        String body = prefix.isEmpty() || !plaintext.startsWith(prefix)
                ? plaintext
                : plaintext.substring(prefix.length());
        int suffixLength = Math.min(4, body.length());
        String lastVisible = body.substring(body.length() - suffixLength);
        String visiblePrefix = prefix.isEmpty() || !plaintext.startsWith(prefix) ? "" : prefix;
        return visiblePrefix + "****" + lastVisible;
    }
}
