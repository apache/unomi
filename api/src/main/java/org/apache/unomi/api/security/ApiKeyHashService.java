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
 * Service for hashing and verifying API keys so that only salted hashes are persisted,
 * never the plaintext key value (see UNOMI-938).
 */
public interface ApiKeyHashService {

    /**
     * Generates a new plaintext API key value.
     * The returned value is only ever available in memory; callers are responsible for
     * hashing it via {@link #hash(String)} before persisting anything and for returning
     * the plaintext value to the caller exactly once.
     *
     * @return a newly generated plaintext API key
     */
    String generateKey();

    /**
     * Hashes a plaintext API key for storage.
     *
     * @param plainTextKey the plaintext API key to hash
     * @return the salted hash, in the format "iterations:base64(salt):base64(hash)"
     */
    String hash(String plainTextKey);

    /**
     * Verifies a plaintext API key against a previously computed hash, using a
     * constant-time comparison to avoid timing attacks.
     *
     * @param plainTextKey the plaintext API key to verify
     * @param storedHash the stored hash to verify against, as produced by {@link #hash(String)}
     * @return {@code true} if the key matches the hash, {@code false} otherwise
     */
    boolean verify(String plainTextKey, String storedHash);

    /**
     * Produces a display-safe masked representation of a plaintext API key, suitable for
     * showing in UIs and logs without exposing the secret (e.g. "unomi_v1_****ab12").
     *
     * @param plainTextKey the plaintext API key to mask
     * @return the masked key
     */
    String mask(String plainTextKey);
}
