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
package org.apache.unomi.api.tenants;

/**
 * Result of an API key creation operation.
 * Carries the persisted {@link ApiKey} metadata (which only stores a hash and a masked
 * representation of the key) together with the one-time plaintext key value. The plaintext
 * key is only ever available at creation time; it cannot be recovered afterwards since it
 * is not persisted (see UNOMI-938).
 */
public class ApiKeyCreationResult {

    /**
     * Persisted API key metadata (hash, maskedKey, type, dates). Does not contain the secret.
     */
    private ApiKey apiKey;
    /**
     * One-time plaintext key value, only available at creation. Prefixed with {@code unomi_v1_}.
     * Store immediately; it cannot be recovered later.
     * @api.example unomi_v1_0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
     */
    private String plainTextKey;

    /**
     * Constructs an empty {@link ApiKeyCreationResult}.
     * This result object must be populated manually with the created API key
     * metadata and the plaintext key value.
     */
    public ApiKeyCreationResult() {
    }

    /**
     * Creates a result containing both the persisted {@link ApiKey} metadata
     * and the plaintext key value.
     * The plaintext key is only available at creation time, as
     * it is not persisted.
     *
     * @param apiKey the API key metadata that was successfully persisted.
     * @param plainTextKey the one-time plaintext key value.
     */
    public ApiKeyCreationResult(ApiKey apiKey, String plainTextKey) {
        this.apiKey = apiKey;
        this.plainTextKey = plainTextKey;
    }

    /**
     * Gets the persisted API key metadata (type, masked key, dates, etc.), without the secret.
     *
     * @return the API key metadata
     */
    public ApiKey getApiKey() {
        return apiKey;
    }

    /**
     * Sets the persisted API key metadata.
     *
     * @param apiKey the API key metadata to set
     */
    public void setApiKey(ApiKey apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Gets the one-time plaintext key value. This is only available right after creation;
     * it is never persisted and cannot be retrieved again afterwards.
     *
     * @return the plaintext API key
     */
    public String getPlainTextKey() {
        return plainTextKey;
    }

    /**
     * Sets the one-time plaintext key value.
     *
     * @param plainTextKey the plaintext API key to set
     */
    public void setPlainTextKey(String plainTextKey) {
        this.plainTextKey = plainTextKey;
    }
}
