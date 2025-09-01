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

package org.apache.unomi.rest.authentication;

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.services.common.security.IPValidationUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Service to handle V2 third-party configuration for V2 compatibility mode.
 * This service reads the legacy V2 third-party configuration and provides
 * methods to validate protected events and third-party providers.
 * Uses the original V2 configuration file: org.apache.unomi.thirdparty.cfg
 */
@Component(service = V2ThirdPartyConfigService.class, configurationPid = "org.apache.unomi.thirdparty")
@Designate(ocd = V2ThirdPartyConfigService.Config.class)
public class V2ThirdPartyConfigService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(V2ThirdPartyConfigService.class);
    
    @ObjectClassDefinition(
        name = "Apache Unomi Third-Party Configuration",
        description = "Configuration for third-party providers (V2 compatibility mode). " +
                     "Providers are configured using the pattern: thirdparty.{providerName}.{property}. " +
                     "Example: thirdparty.myapp.key, thirdparty.myapp.ipAddresses, thirdparty.myapp.allowedEvents"
    )
    public @interface Config {
        // No hardcoded attributes - all providers are configured dynamically
        // using the pattern: thirdparty.{providerName}.{property}
    }
    
    /**
     * Provider configuration data structure
     */
    private static class ProviderConfig {
        private final String key;
        private final Set<String> ipAddresses;
        private final Set<String> allowedEvents;
        
        public ProviderConfig(String key, Set<String> ipAddresses, Set<String> allowedEvents) {
            this.key = key;
            this.ipAddresses = ipAddresses;
            this.allowedEvents = allowedEvents;
        }
        
        public String getKey() { return key; }
        public Set<String> getIpAddresses() { return ipAddresses; }
        public Set<String> getAllowedEvents() { return allowedEvents; }
    }
    
    private volatile Map<String, ProviderConfig> providers = new HashMap<>();
    
    @Activate
    public void activate(Map<String, Object> properties) {
        modified(properties);
    }
    
    @Modified
    public void modified(Map<String, Object> properties) {
        Map<String, ProviderConfig> newProviders = new HashMap<>();
        
        if (properties != null) {
            // Parse all provider configurations dynamically
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                
                // Look for provider configuration patterns: thirdparty.{providerName}.{property}
                if (key.startsWith("thirdparty.") && key.contains(".")) {
                    String[] parts = key.split("\\.");
                    if (parts.length >= 3) {
                        String providerName = parts[1];
                        String property = parts[2];
                        
                        ProviderConfig existingConfig = newProviders.get(providerName);
                        String configKey = existingConfig != null ? existingConfig.getKey() : "";
                        Set<String> configIpAddresses = existingConfig != null ? existingConfig.getIpAddresses() : new HashSet<>();
                        Set<String> configAllowedEvents = existingConfig != null ? existingConfig.getAllowedEvents() : new HashSet<>();
                        
                        switch (property) {
                            case "key":
                                configKey = value;
                                break;
                            case "ipAddresses":
                                configIpAddresses = parseCommaSeparatedList(value);
                                break;
                            case "allowedEvents":
                                configAllowedEvents = parseCommaSeparatedList(value);
                                break;
                        }
                        
                        // Only add provider if it has a key (required for authentication)
                        if (StringUtils.isNotBlank(configKey)) {
                            newProviders.put(providerName, new ProviderConfig(configKey, configIpAddresses, configAllowedEvents));
                        }
                    }
                }
            }
        }
        
        // Set default provider1 if no providers configured
        if (newProviders.isEmpty()) {
            newProviders.put("provider1", new ProviderConfig(
                "670c26d1cc413346c3b2fd9ce65dab41",
                new HashSet<>(Arrays.asList("127.0.0.1", "::1")),
                new HashSet<>(Arrays.asList("login", "updateProperties"))
            ));
        }
        
        this.providers = newProviders;
        
        int totalEvents = newProviders.values().stream()
            .mapToInt(config -> config.getAllowedEvents().size())
            .sum();
        
        LOGGER.info("V2 Third-Party Configuration updated - {} providers with {} total protected events", 
                   newProviders.size(), totalEvents);
    }
    
    /**
     * Check if an event type is protected (requires third-party authentication).
     * 
     * @param eventType the event type to check
     * @return true if the event type is protected, false otherwise
     */
    public boolean isProtectedEventType(String eventType) {
        if (StringUtils.isBlank(eventType)) {
            return false;
        }
        
        return providers.values().stream()
            .anyMatch(config -> config.getAllowedEvents().contains(eventType));
    }
    
    /**
     * Get all protected event types from all providers.
     * 
     * @return set of all protected event types
     */
    public Set<String> getAllProtectedEventTypes() {
        Set<String> allProtectedEvents = new HashSet<>();
        for (ProviderConfig config : providers.values()) {
            allProtectedEvents.addAll(config.getAllowedEvents());
        }
        return Collections.unmodifiableSet(allProtectedEvents);
    }
    

    /**
     * Validate a third-party provider by key for a given event type.
     * This method is used when the X-Unomi-Peer header contains the provider key.
     * 
     * @param providerKey the third-party provider key (from X-Unomi-Peer header)
     * @param eventType the event type to validate
     * @param sourceIP the source IP address
     * @return true if the provider is authorized for this event type and IP, false otherwise
     */
    public boolean validateProviderByKey(String providerKey, String eventType, String sourceIP) {
        if (StringUtils.isBlank(providerKey) || StringUtils.isBlank(eventType) || StringUtils.isBlank(sourceIP)) {
            return false;
        }
        
        // Find the provider that has the matching key
        ProviderConfig config = null;
        String foundProviderId = null;
        for (Map.Entry<String, ProviderConfig> entry : providers.entrySet()) {
            if (providerKey.equals(entry.getValue().getKey())) {
                config = entry.getValue();
                foundProviderId = entry.getKey();
                break;
            }
        }
        
        if (config == null) {
            LOGGER.debug("V2 compatibility mode: Unknown provider key: {}", providerKey);
            return false;
        }
        
        if (!config.getAllowedEvents().contains(eventType)) {
            LOGGER.debug("V2 compatibility mode: Event type {} not allowed for provider {} (key: {})", eventType, foundProviderId, providerKey);
            return false;
        }
        
        boolean ipAuthorized = IPValidationUtils.isIpAuthorized(sourceIP, config.getIpAddresses());
        if (!ipAuthorized) {
            LOGGER.debug("V2 compatibility mode: IP {} not authorized for provider {} (key: {})", sourceIP, foundProviderId, providerKey);
        }
        
        return ipAuthorized;
    }
    
    /**
     * Get the key for a third-party provider.
     * 
     * @param providerId the third-party provider ID
     * @return the provider key, or null if not found
     */
    public String getProviderKey(String providerId) {
        ProviderConfig config = providers.get(providerId);
        return config != null ? config.getKey() : null;
    }
    
    /**
     * Check if a provider ID is valid.
     * 
     * @param providerId the third-party provider ID
     * @return true if the provider ID is valid, false otherwise
     */
    public boolean isValidProvider(String providerId) {
        return providers.containsKey(providerId);
    }
    
    private Set<String> parseCommaSeparatedList(String value) {
        if (StringUtils.isBlank(value)) {
            return new HashSet<>();
        }
        
        Set<String> result = new HashSet<>();
        String[] parts = value.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (StringUtils.isNotBlank(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }
    

}
