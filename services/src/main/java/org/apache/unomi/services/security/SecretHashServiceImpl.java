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

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Default {@link SecretHashService} implementation using PBKDF2-HMAC-SHA512.
 * Domain-specific callers (for example {@link org.apache.unomi.api.tenants.ApiKey})
 * use this service for one-way hashing while applying their own key format rules.
 */
public class SecretHashServiceImpl implements SecretHashService {

    /** PBKDF2 algorithm used for all one-way secret hashes. */
    public static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA512";

    /** Default iteration count embedded in stored hashes. */
    public static final int DEFAULT_ITERATIONS = 600_000;

    /** Random salt length in bytes. */
    public static final int SALT_LENGTH_BYTES = 16;

    /** Derived key length in bits. */
    public static final int HASH_LENGTH_BITS = 256;

    /** Number of trailing characters shown after the mask marker. */
    public static final int DEFAULT_VISIBLE_SUFFIX_LENGTH = 4;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String hash(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext cannot be null");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(plaintext.toCharArray(), salt, DEFAULT_ITERATIONS);
        return DEFAULT_ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(hash);
    }

    @Override
    public boolean verify(String plaintext, String storedHash) {
        if (plaintext == null || storedHash == null) {
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
            byte[] actualHash = pbkdf2(plaintext.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(actualHash, expectedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
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
        int suffixLength = Math.min(DEFAULT_VISIBLE_SUFFIX_LENGTH, body.length());
        String lastVisible = body.substring(body.length() - suffixLength);
        String visiblePrefix = prefix.isEmpty() || !plaintext.startsWith(prefix) ? "" : prefix;
        return visiblePrefix + "****" + lastVisible;
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
