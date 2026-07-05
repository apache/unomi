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
 * API keys are machine-generated with high entropy and hashed with SHA-256 (lowercase hex) for
 * storage and online verification.
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
}
