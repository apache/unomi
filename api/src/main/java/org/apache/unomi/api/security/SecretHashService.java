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
package org.apache.unomi.api.security;

/**
 * One-way hashing for secrets that must never be stored in plaintext. This is distinct from
 * {@link EncryptionService}, which handles reversible encryption keys.
 * <p>
 * Two strategies are provided:
 * <ul>
 *   <li>{@link #hashHighEntropySecret(String)} / {@link #verifyHighEntropySecret(String, String)}
 *       — fast SHA-256 for machine-generated secrets such as API keys (256 bits of randomness).
 *       Safe to run on every HTTP request.</li>
 *   <li>{@link #hash(String)} / {@link #verify(String, String)} — slow PBKDF2-HMAC-SHA512 with
 *       per-value salt for low-entropy human secrets such as passwords. Not intended for per-request
 *       API key verification.</li>
 * </ul>
 */
public interface SecretHashService {

    /**
     * Hashes a high-entropy secret (API keys, tokens) with SHA-256 for storage and online verification.
     *
     * @param plaintext the secret to hash; must not be {@code null}
     * @return lowercase hex-encoded SHA-256 digest
     * @throws IllegalArgumentException if {@code plaintext} is {@code null}
     */
    String hashHighEntropySecret(String plaintext);

    /**
     * Verifies a high-entropy secret against a stored SHA-256 digest using a constant-time comparison.
     *
     * @param plaintext the plaintext secret to verify
     * @param storedHash the stored digest, as produced by {@link #hashHighEntropySecret(String)}
     * @return {@code true} if the secret matches, {@code false} otherwise
     */
    boolean verifyHighEntropySecret(String plaintext, String storedHash);

    /**
     * Hashes a low-entropy secret for storage using PBKDF2-HMAC-SHA512. Each call generates a new
     * random salt. Do not use for API keys — use {@link #hashHighEntropySecret(String)} instead.
     *
     * @param plaintext the secret to hash; must not be {@code null}
     * @return the salted hash, in the format {@code iterations:base64(salt):base64(hash)}
     * @throws IllegalArgumentException if {@code plaintext} is {@code null}
     */
    String hash(String plaintext);

    /**
     * Verifies a low-entropy secret against a PBKDF2 hash produced by {@link #hash(String)}.
     *
     * @param plaintext the plaintext secret to verify
     * @param storedHash the stored hash to verify against
     * @return {@code true} if the secret matches the hash, {@code false} otherwise
     */
    boolean verify(String plaintext, String storedHash);

    /**
     * Generates cryptographically random secret material as an uppercase hexadecimal string.
     * Callers add any domain-specific prefix (for example {@code unomi_v1_} for API keys).
     *
     * @param randomByteLength number of random bytes to generate before hex encoding
     * @return uppercase hex string of length {@code randomByteLength * 2}
     */
    String generateRandomSecret(int randomByteLength);

    /**
     * Produces a display-safe masked representation of a plaintext secret, suitable for UIs
     * and logs. The result is {@code displayPrefix + "****" + lastFour}, where {@code lastFour}
     * is taken from the secret body after stripping {@code displayPrefix} when present.
     *
     * @param plaintext the plaintext secret to mask; may be {@code null}
     * @param displayPrefix optional prefix shown before the mask (for example {@code unomi_v1_});
     *                      use an empty string when no prefix is needed
     * @return the masked value, or {@code null} when {@code plaintext} is {@code null}
     */
    String mask(String plaintext, String displayPrefix);
}
