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

import org.apache.unomi.api.Item;

import java.security.SecureRandom;
import java.util.Date;

/**
 * Persisted credential used to authenticate REST calls for a {@link Tenant}.
 * Stores hashed key material, scope, expiration, and revocation state. Plaintext
 * keys are only returned once at creation time via {@link ApiKeyCreationResult}.
 */
public class ApiKey extends Item {
    /**
     * The item type for an API key.
     */
    public static final String ITEM_TYPE = "apiKey";

    /** Prefix prepended to generated API key values. */
    public static final String KEY_PREFIX = "unomi_v1_";

    /** Number of random bytes used when generating a new API key. */
    public static final int KEY_RANDOM_BYTES = 32;

    /** Number of trailing characters shown after the mask marker. */
    private static final int VISIBLE_SUFFIX_LENGTH = 4;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Scope of an API key. Serialized as {@code PUBLIC} or {@code PRIVATE}.
     */
    public enum ApiKeyType {
        /** Public key for context.json, event collector, and other public-facing endpoints. */
        PUBLIC,

        /** Private key for protected endpoints (login, updateProperties, admin-style operations). */
        PRIVATE
    }

    /**
     * SHA-256 hex digest of the API key ({@link org.apache.unomi.api.security.SecretHashService#hash(String)}).
     * The plaintext key is never persisted; it is only returned once at creation time via {@link ApiKeyCreationResult}.
     */
    private String keyHash;

    /**
     * Display-safe masked key (for example {@code unomi_v1_****ab12}) for UIs and logs.
     * @api.example unomi_v1_****ab12
     */
    private String maskedKey;

    /**
     * Key scope: {@code PUBLIC} or {@code PRIVATE}.
     * @api.example PUBLIC
     */
    private ApiKeyType keyType;

    /**
     * Optional operator label for the key.
     */
    private String name;

    /**
     * Optional description of the key's purpose.
     */
    private String description;

    /**
     * When the key was created (ISO-8601 date-time in JSON).
     */
    private Date creationDate;

    /**
     * Expiration instant, or omitted/null when the key does not expire.
     */
    private Date expirationDate;

    /**
     * {@code true} if the key has been revoked and must not authenticate.
     * @api.example false
     */
    private boolean revoked;

    /**
     * Default constructor that initializes the API key as an Item.
     */
    public ApiKey() {
        super();
        setItemType(ITEM_TYPE);
    }

    /**
     * Generates a new plaintext API key.
     *
     * @return a newly generated plaintext API key with the {@link #KEY_PREFIX} prefix
     */
    public static String generatePlainTextKey() {
        byte[] randomBytes = new byte[KEY_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        StringBuilder hex = new StringBuilder(randomBytes.length * 2);
        for (byte b : randomBytes) {
            hex.append(String.format("%02X", b));
        }
        return KEY_PREFIX + hex;
    }

    /**
     * Produces a display-safe masked representation of a plaintext API key.
     *
     * @param plainTextKey the plaintext API key to mask; may be {@code null}
     * @return the masked key (e.g. {@code unomi_v1_****ab12}), or {@code null} when {@code plainTextKey} is {@code null}
     */
    public static String maskPlainTextKey(String plainTextKey) {
        if (plainTextKey == null) {
            return null;
        }
        boolean hasPrefix = plainTextKey.startsWith(KEY_PREFIX);
        String body = hasPrefix ? plainTextKey.substring(KEY_PREFIX.length()) : plainTextKey;
        int suffixLength = Math.min(VISIBLE_SUFFIX_LENGTH, body.length());
        String lastVisible = body.substring(body.length() - suffixLength);
        return (hasPrefix ? KEY_PREFIX : "") + "****" + lastVisible;
    }

    /**
     * Gets the SHA-256 digest of the API key.
     *
     * @return the key hash as lowercase hex
     */
    public String getKeyHash() {
        return keyHash;
    }

    /**
     * Sets the SHA-256 digest of the API key.
     *
     * @param keyHash the key hash to set
     */
    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    /**
     * Gets the display-safe masked representation of the key.
     *
     * @return the masked key (e.g. "unomi_v1_****ab12")
     */
    public String getMaskedKey() {
        return maskedKey;
    }

    /**
     * Sets the display-safe masked representation of the key.
     *
     * @param maskedKey the masked key to set
     */
    public void setMaskedKey(String maskedKey) {
        this.maskedKey = maskedKey;
    }

    /**
     * Gets the name or identifier of the API key.
     *
     * @return the API key name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name or identifier of the API key.
     *
     * @param name the API key name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the description of the API key's purpose or usage.
     *
     * @return the API key description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the API key's purpose or usage.
     *
     * @param description the API key description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the creation date of the API key.
     *
     * @return the creation date
     */
    @Override
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * Sets the creation date of the API key.
     *
     * @param creationDate the creation date to set
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Gets the expiration date of the API key.
     *
     * @return the expiration date
     */
    public Date getExpirationDate() {
        return expirationDate;
    }

    /**
     * Sets the expiration date of the API key.
     *
     * @param expirationDate the expiration date to set
     */
    public void setExpirationDate(Date expirationDate) {
        this.expirationDate = expirationDate;
    }

    /**
     * Checks if the API key has been revoked.
     *
     * @return true if the API key is revoked, false otherwise
     */
    public boolean isRevoked() {
        return revoked;
    }

    /**
     * Sets the revocation status of the API key.
     *
     * @param revoked true to revoke the API key, false to reinstate
     */
    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    /**
     * Gets the type of the API key.
     *
     * @return the API key type
     */
    public ApiKeyType getKeyType() {
        return keyType;
    }

    /**
     * Sets the type of the API key.
     *
     * @param keyType the API key type to set
     */
    public void setKeyType(ApiKeyType keyType) {
        this.keyType = keyType;
    }
}
