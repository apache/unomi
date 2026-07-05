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
package org.apache.unomi.services.security;

import org.apache.unomi.api.security.SecretHashService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Default {@link SecretHashService} implementation using SHA-256 for one-way secret storage.
 */
public class SecretHashServiceImpl implements SecretHashService {

    /** Digest algorithm for stored API keys and other high-entropy secrets. */
    public static final String HASH_ALGORITHM = "SHA-256";

    /** Number of trailing characters shown after the mask marker. */
    public static final int DEFAULT_VISIBLE_SUFFIX_LENGTH = 4;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String hash(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext cannot be null");
        }
        return sha256Hex(plaintext);
    }

    @Override
    public boolean verify(String plaintext, String storedHash) {
        if (plaintext == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sha256Hex(plaintext).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
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
        boolean hasPrefix = !prefix.isEmpty() && plaintext.startsWith(prefix);
        String body = hasPrefix ? plaintext.substring(prefix.length()) : plaintext;
        int suffixLength = Math.min(DEFAULT_VISIBLE_SUFFIX_LENGTH, body.length());
        String lastVisible = body.substring(body.length() - suffixLength);
        String visiblePrefix = hasPrefix ? prefix : "";
        return visiblePrefix + "****" + lastVisible;
    }

    private String sha256Hex(String plaintext) {
        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(plaintext.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to compute SHA-256 hash", e);
        }
    }
}
