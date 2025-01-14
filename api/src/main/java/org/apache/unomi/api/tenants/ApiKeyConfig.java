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

import java.util.List;
import java.util.Map;

/**
 * Configuration settings for API keys.
 * This class defines the configuration parameters for API key management,
 * including validation rules and usage limits.
 */
public class ApiKeyConfig {
    private int minLength;
    private int maxLength;
    private String pattern;
    private int maxActiveKeys;
    private int defaultExpirationDays;
    private List<String> allowedScopes;
    private Map<String, Integer> rateLimits;
    private Map<String, Object> additionalSettings;

    /**
     * Gets the minimum length required for API keys.
     * @return the minimum length
     */
    public int getMinLength() {
        return minLength;
    }

    /**
     * Sets the minimum length required for API keys.
     * @param minLength the minimum length to set
     */
    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    /**
     * Gets the maximum length allowed for API keys.
     * @return the maximum length
     */
    public int getMaxLength() {
        return maxLength;
    }

    /**
     * Sets the maximum length allowed for API keys.
     * @param maxLength the maximum length to set
     */
    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    /**
     * Gets the regex pattern for API key validation.
     * @return the validation pattern
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * Sets the regex pattern for API key validation.
     * @param pattern the validation pattern to set
     */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    /**
     * Gets the maximum number of active API keys allowed.
     * @return the maximum number of active keys
     */
    public int getMaxActiveKeys() {
        return maxActiveKeys;
    }

    /**
     * Sets the maximum number of active API keys allowed.
     * @param maxActiveKeys the maximum number to set
     */
    public void setMaxActiveKeys(int maxActiveKeys) {
        this.maxActiveKeys = maxActiveKeys;
    }

    /**
     * Gets the default expiration period in days for new API keys.
     * @return the default expiration period in days
     */
    public int getDefaultExpirationDays() {
        return defaultExpirationDays;
    }

    /**
     * Sets the default expiration period in days for new API keys.
     * @param defaultExpirationDays the default expiration period to set
     */
    public void setDefaultExpirationDays(int defaultExpirationDays) {
        this.defaultExpirationDays = defaultExpirationDays;
    }

    /**
     * Gets the list of allowed scopes for API keys.
     * @return list of allowed scopes
     */
    public List<String> getAllowedScopes() {
        return allowedScopes;
    }

    /**
     * Sets the list of allowed scopes for API keys.
     * @param allowedScopes list of allowed scopes to set
     */
    public void setAllowedScopes(List<String> allowedScopes) {
        this.allowedScopes = allowedScopes;
    }

    /**
     * Gets the rate limits for different operations.
     * @return map of operation names to their rate limits
     */
    public Map<String, Integer> getRateLimits() {
        return rateLimits;
    }

    /**
     * Sets the rate limits for different operations.
     * @param rateLimits map of operation names to their rate limits
     */
    public void setRateLimits(Map<String, Integer> rateLimits) {
        this.rateLimits = rateLimits;
    }

    /**
     * Gets additional configuration settings.
     * @return map of additional settings
     */
    public Map<String, Object> getAdditionalSettings() {
        return additionalSettings;
    }

    /**
     * Sets additional configuration settings.
     * @param additionalSettings map of additional settings to set
     */
    public void setAdditionalSettings(Map<String, Object> additionalSettings) {
        this.additionalSettings = additionalSettings;
    }
} 