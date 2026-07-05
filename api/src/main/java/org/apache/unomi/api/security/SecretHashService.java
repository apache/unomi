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
 * API keys and other machine-generated secrets are hashed with SHA-256 (lowercase hex). Keys are
 * generated with {@link #generateRandomSecret(int)} (256 bits of randomness), so a fast digest is
 * sufficient and safe to run on every HTTP request.
 */
public interface SecretHashService {

    /**
     * Hashes a secret with SHA-256 for storage.
     *
     * @param plaintext the secret to hash; must not be {@code null}
     * @return lowercase hex-encoded SHA-256 digest
     * @throws IllegalArgumentException if {@code plaintext} is {@code null}
     */
    String hash(String plaintext);

    /**
     * Verifies a secret against a stored SHA-256 digest using a constant-time comparison.
     *
     * @param plaintext the plaintext secret to verify
     * @param storedHash the stored digest, as produced by {@link #hash(String)}
     * @return {@code true} if the secret matches, {@code false} otherwise
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
