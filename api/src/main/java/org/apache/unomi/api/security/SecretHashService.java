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
 * One-way hashing for secrets that must never be stored in plaintext (API keys, profile
 * passwords, tokens, and similar values). This is distinct from {@link EncryptionService},
 * which handles reversible encryption keys.
 * <p>
 * Hashes use PBKDF2-HMAC-SHA512 with a per-value random salt. The persisted form is
 * {@code iterations:base64(salt):base64(hash)} so future algorithm or iteration-count
 * changes remain backward compatible at verification time.
 */
public interface SecretHashService {

    /**
     * Hashes a plaintext secret for storage. Each call generates a new random salt.
     *
     * @param plaintext the secret to hash; must not be {@code null}
     * @return the salted hash, in the format {@code iterations:base64(salt):base64(hash)}
     * @throws IllegalArgumentException if {@code plaintext} is {@code null}
     */
    String hash(String plaintext);

    /**
     * Verifies a plaintext secret against a previously computed hash, using a constant-time
     * comparison to reduce timing-attack risk.
     *
     * @param plaintext the plaintext secret to verify
     * @param storedHash the stored hash to verify against, as produced by {@link #hash(String)}
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
