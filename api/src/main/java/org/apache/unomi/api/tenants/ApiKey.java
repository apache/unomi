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

import java.util.Date;

/**
 * Represents an API key for tenant authentication and authorization.
 * This class extends the base Item class and provides functionality for managing
 * API keys including their lifecycle (creation, expiration, revocation) and metadata.
 */
public class ApiKey extends Item {
    /**
     * The item type for an API key.
     */
    public static final String ITEM_TYPE = "apiKey";

    /**
     * Enum defining the types of API keys.
     */
    public enum ApiKeyType {
        /**
         * Public API key for context.json, event collector and other public-facing endpoints
         */
        PUBLIC,
        
        /**
         * Private API key for protected endpoints including login and updateProperties
         */
        PRIVATE
    }

    /**
     * The API key value.
     */
    private String key;

    /**
     * The type of API key (public or private).
     */
    private ApiKeyType keyType;

    /**
     * The name or identifier of the API key.
     */
    private String name;

    /**
     * A description of the API key's purpose or usage.
     */
    private String description;

    /**
     * The date when the API key was created.
     */
    private Date creationDate;

    /**
     * The date when the API key expires.
     */
    private Date expirationDate;

    /**
     * Whether the API key has been revoked.
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
     * Gets the API key value.
     * @return the API key value
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the API key value.
     * @param key the API key value to set
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Gets the name or identifier of the API key.
     * @return the API key name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name or identifier of the API key.
     * @param name the API key name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the description of the API key's purpose or usage.
     * @return the API key description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the API key's purpose or usage.
     * @param description the API key description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the creation date of the API key.
     * @return the creation date
     */
    @Override
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * Sets the creation date of the API key.
     * @param creationDate the creation date to set
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Gets the expiration date of the API key.
     * @return the expiration date
     */
    public Date getExpirationDate() {
        return expirationDate;
    }

    /**
     * Sets the expiration date of the API key.
     * @param expirationDate the expiration date to set
     */
    public void setExpirationDate(Date expirationDate) {
        this.expirationDate = expirationDate;
    }

    /**
     * Checks if the API key has been revoked.
     * @return true if the API key is revoked, false otherwise
     */
    public boolean isRevoked() {
        return revoked;
    }

    /**
     * Sets the revocation status of the API key.
     * @param revoked true to revoke the API key, false to reinstate
     */
    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    /**
     * Gets the type of the API key.
     * @return the API key type
     */
    public ApiKeyType getKeyType() {
        return keyType;
    }

    /**
     * Sets the type of the API key.
     * @param keyType the API key type to set
     */
    public void setKeyType(ApiKeyType keyType) {
        this.keyType = keyType;
    }
}
