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
import org.apache.unomi.services.common.security.SecurityUtils;
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
 * OSGi service that loads V2 third-party provider configuration from {@code org.apache.unomi.thirdparty.cfg}
 * and validates protected events and provider keys in V2 compatibility mode.
 */
@Component(service = V2ThirdPartyConfigService.class, configurationPid = "org.apache.unomi.thirdparty")
@Designate(ocd = V2ThirdPartyConfigService.Config.class)
public class V2ThirdPartyConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(V2ThirdPartyConfigService.class);

    /**
     * OSGi configuration for V2 third-party providers.
     */
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
     * Provider configuration entry parsed from OSGi properties.
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

    /**
     * Activates the service and loads third-party provider configuration.
     *
     * @param properties the OSGi configuration properties
     */
    @Activate
    public void activate(Map<String, Object> properties) {
        modified(properties);
    }

    /**
     * Reloads third-party provider configuration.
     *
     * @param properties the OSGi configuration properties
     */
    @Modified
    public void modified(Map<String, Object> properties) {
        Map<String, ProviderConfig> newProviders = new HashMap<>();

        if (properties != null) {
            // Phase 1: collect raw property values per provider, order-independent
            Map<String, Map<String, String>> rawProviders = new HashMap<>();
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String propKey = entry.getKey();
                String value = entry.getValue() != null ? entry.getValue().toString() : "";

                // Look for provider configuration patterns: thirdparty.{providerName}.{property}
                if (propKey.startsWith("thirdparty.") && propKey.contains(".")) {
                    String[] parts = propKey.split("\\.");
                    if (parts.length >= 3) {
                        String providerName = parts[1];
                        String property = parts[2];
                        rawProviders.computeIfAbsent(providerName, k -> new HashMap<>()).put(property, value);
                    }
                }
            }

            // Phase 2: build ProviderConfig objects — only for providers that have a key
            for (Map.Entry<String, Map<String, String>> entry : rawProviders.entrySet()) {
                String providerName = entry.getKey();
                Map<String, String> props = entry.getValue();
                String configKey = props.get("key");
                if (StringUtils.isNotBlank(configKey)) {
                    Set<String> configIpAddresses = parseCommaSeparatedList(props.getOrDefault("ipAddresses", ""));
                    Set<String> configAllowedEvents = parseCommaSeparatedList(props.getOrDefault("allowedEvents", ""));
                    newProviders.put(providerName, new ProviderConfig(configKey, configIpAddresses, configAllowedEvents));
                }
            }
        }

        if (newProviders.isEmpty()) {
            // The fallback key below is the well-known Unomi V2 default key, publicly documented
            // in Apache Unomi changelogs and issue trackers. It provides no confidentiality on its own
            // and is restricted to localhost only as a partial mitigation.
            // Configure org.apache.unomi.thirdparty.cfg with a custom key before production use.
            LOGGER.warn("V2 compatibility mode: no third-party providers configured in org.apache.unomi.thirdparty.cfg — " +
                        "falling back to the well-known default key restricted to localhost. " +
                        "Configure a custom provider key before using V2 compatibility mode in production.");
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
     * Returns whether the event type requires third-party authentication.
     *
     * @param eventType the event type to check
     * @return {@code true} when the event type is protected
     */
    public boolean isProtectedEventType(String eventType) {
        if (StringUtils.isBlank(eventType)) {
            return false;
        }

        return providers.values().stream()
            .anyMatch(config -> config.getAllowedEvents().contains(eventType));
    }

    /**
     * Returns all protected event types declared by configured providers.
     *
     * @return an unmodifiable set of protected event type names
     */
    public Set<String> getAllProtectedEventTypes() {
        Set<String> allProtectedEvents = new HashSet<>();
        for (ProviderConfig config : providers.values()) {
            allProtectedEvents.addAll(config.getAllowedEvents());
        }
        return Collections.unmodifiableSet(allProtectedEvents);
    }

    /**
     * Validates a provider key from the {@code X-Unomi-Peer} header for an event and source IP.
     *
     * @param providerKey the third-party provider key from the request header
     * @param eventType the event type to validate
     * @param sourceIP the source IP address
     * @return {@code true} when the provider is authorized for the event and IP
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
            LOGGER.debug("V2 compatibility mode: Unknown provider key: {}", SecurityUtils.maskSecret(providerKey));
            return false;
        }

        if (!config.getAllowedEvents().contains(eventType)) {
            LOGGER.debug("V2 compatibility mode: Event type {} not allowed for provider {} (key: {})", eventType, foundProviderId, SecurityUtils.maskSecret(providerKey));
            return false;
        }

        boolean ipAuthorized = IPValidationUtils.isIpAuthorized(sourceIP, config.getIpAddresses());
        if (!ipAuthorized) {
            LOGGER.debug("V2 compatibility mode: IP {} not authorized for provider {} (key: {})", sourceIP, foundProviderId, SecurityUtils.maskSecret(providerKey));
        }

        return ipAuthorized;
    }

    /**
     * Returns the authentication key for the given provider ID.
     *
     * @param providerId the third-party provider ID
     * @return the provider key, or {@code null} when the provider is unknown
     */
    public String getProviderKey(String providerId) {
        ProviderConfig config = providers.get(providerId);
        return config != null ? config.getKey() : null;
    }

    /**
     * Returns whether the provider ID is configured.
     *
     * @param providerId the third-party provider ID
     * @return {@code true} when the provider ID is known
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
