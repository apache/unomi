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
import org.apache.unomi.api.security.CompatPeerPrincipal;
import org.apache.unomi.services.common.security.IPValidationUtils;
import org.apache.unomi.services.common.security.SecurityUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads third-party provider configuration from {@code org.apache.unomi.thirdparty.cfg}
 * and validates {@code X-Unomi-Peer} keys in single-tenant compatibility mode.
 */
@Component(service = V2ThirdPartyConfigService.class, configurationPid = "org.apache.unomi.thirdparty")
@Designate(ocd = V2ThirdPartyConfigService.Config.class)
public class V2ThirdPartyConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(V2ThirdPartyConfigService.class);

    /** Well-known key shipped in examples and old defaults; never safe for production. */
    public static final String EXAMPLE_PROVIDER_KEY = "670c26d1cc413346c3b2fd9ce65dab41";

    /** Minimum accepted key length (UTF-16 code units). Shorter keys disable the provider. */
    public static final int MIN_KEY_LENGTH = 16;

    private static final String PROP_ALLOW_EXAMPLE_KEY = "allowExampleKey";
    private static final String PROP_TRUSTED_PROXIES = "trustedProxies";

    /**
     * OSGi configuration for V2 third-party providers.
     */
    @ObjectClassDefinition(
        name = "Apache Unomi Third-Party Configuration",
        description = "Configuration for third-party providers (single-tenant compatibility mode). "
            + "Providers use thirdparty.{name}.key, .ipAddresses, .allowedEvents and optional .abilities. "
            + "Global: allowExampleKey, trustedProxies."
    )
    public @interface Config {
    }

    /**
     * A configured provider that passed key hygiene checks.
     */
    public static final class ProviderConfig {
        private final String key;
        private final Set<String> ipAddresses;
        private final Set<String> allowedEvents;
        private final Set<String> abilities;

        public ProviderConfig(String key, Set<String> ipAddresses, Set<String> allowedEvents, Set<String> abilities) {
            this.key = key;
            this.ipAddresses = ipAddresses;
            this.allowedEvents = allowedEvents;
            this.abilities = abilities == null || abilities.isEmpty()
                    ? CompatPeerPrincipal.DEFAULT_ABILITIES
                    : Collections.unmodifiableSet(abilities);
        }

        public String getKey() {
            return key;
        }

        public Set<String> getIpAddresses() {
            return ipAddresses;
        }

        public Set<String> getAllowedEvents() {
            return allowedEvents;
        }

        public Set<String> getAbilities() {
            return abilities;
        }
    }

    private volatile Map<String, ProviderConfig> providers = new HashMap<>();
    private volatile Set<String> trustedProxies = Collections.emptySet();
    private volatile boolean allowExampleKey;

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
        boolean allowExample = false;
        Set<String> proxies = new LinkedHashSet<>();

        if (properties != null) {
            allowExample = Boolean.parseBoolean(String.valueOf(properties.getOrDefault(PROP_ALLOW_EXAMPLE_KEY, "false")));
            proxies.addAll(parseCommaSeparatedList(stringValue(properties.get(PROP_TRUSTED_PROXIES))));

            Map<String, Map<String, String>> rawProviders = new HashMap<>();
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String propKey = entry.getKey();
                if (!propKey.startsWith("thirdparty.") || !propKey.contains(".")) {
                    continue;
                }
                String[] parts = propKey.split("\\.");
                if (parts.length >= 3) {
                    String providerName = parts[1];
                    String property = parts[2];
                    rawProviders.computeIfAbsent(providerName, k -> new HashMap<>())
                            .put(property, stringValue(entry.getValue()));
                }
            }

            for (Map.Entry<String, Map<String, String>> entry : rawProviders.entrySet()) {
                String providerName = entry.getKey();
                Map<String, String> props = entry.getValue();
                String configKey = props.get("key");
                if (StringUtils.isBlank(configKey)) {
                    LOGGER.error("Third-party provider '{}' is disabled: its key is empty. "
                            + "Set thirdparty.{}.key to a secret of at least {} characters.",
                            providerName, providerName, MIN_KEY_LENGTH);
                    continue;
                }
                if (configKey.length() < MIN_KEY_LENGTH) {
                    LOGGER.error("Third-party provider '{}' is disabled: its key is shorter than {} characters. "
                            + "Choose a longer secret for thirdparty.{}.key.",
                            providerName, MIN_KEY_LENGTH, providerName);
                    continue;
                }
                if (EXAMPLE_PROVIDER_KEY.equals(configKey) && !allowExample) {
                    LOGGER.error("Third-party provider '{}' is disabled: it uses the well-known example key. "
                            + "Set a unique secret for thirdparty.{}.key, or set allowExampleKey=true only "
                            + "during a transition (a warning is logged at every start).",
                            providerName, providerName);
                    continue;
                }
                if (EXAMPLE_PROVIDER_KEY.equals(configKey) && allowExample) {
                    LOGGER.warn("Third-party provider '{}' uses the well-known example key with allowExampleKey=true. "
                            + "Replace thirdparty.{}.key before production use.",
                            providerName, providerName);
                }

                Set<String> configIpAddresses = parseCommaSeparatedList(props.getOrDefault("ipAddresses", ""));
                Set<String> configAllowedEvents = parseCommaSeparatedList(props.getOrDefault("allowedEvents", ""));
                Set<String> abilities = parseAbilities(props.get("abilities"));
                newProviders.put(providerName,
                        new ProviderConfig(configKey, configIpAddresses, configAllowedEvents, abilities));
            }
        }

        if (newProviders.isEmpty()) {
            LOGGER.error("No usable third-party providers are configured in org.apache.unomi.thirdparty.cfg. "
                    + "Protected events and peer abilities in single-tenant compatibility mode will be refused. "
                    + "Add thirdparty.{{name}}.key (at least {} characters, not the example key unless "
                    + "allowExampleKey=true), .ipAddresses and .allowedEvents.",
                    MIN_KEY_LENGTH);
        }

        this.allowExampleKey = allowExample;
        this.trustedProxies = Collections.unmodifiableSet(proxies);
        this.providers = newProviders;

        int totalEvents = newProviders.values().stream()
                .mapToInt(config -> config.getAllowedEvents().size())
                .sum();
        LOGGER.info("V2 Third-Party Configuration updated - {} providers with {} total protected events"
                        + " (allowExampleKey={}, trustedProxies={})",
                newProviders.size(), totalEvents, allowExample, proxies.size());
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
     * Authenticates the {@code X-Unomi-Peer} header for key and client IP only (no event-type check).
     * Used to attach peer abilities for the whole public request in compatibility mode.
     *
     * @param request the HTTP request
     * @return a principal when key and IP match a provider, otherwise empty
     */
    public Optional<CompatPeerPrincipal> authenticatePeer(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String providerKey = request.getHeader("X-Unomi-Peer");
        if (StringUtils.isBlank(providerKey)) {
            return Optional.empty();
        }
        String sourceIP = resolveClientIp(request);
        return findProviderByKey(providerKey)
                .filter(match -> IPValidationUtils.isIpAuthorized(sourceIP, match.config().getIpAddresses()))
                .map(match -> {
                    LOGGER.debug("Compat peer authenticated: provider={} from IP={}", match.providerId(), sourceIP);
                    return new CompatPeerPrincipal(match.providerId(), match.config().getAbilities());
                });
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

        Optional<ProviderMatch> match = findProviderByKey(providerKey);
        if (match.isEmpty()) {
            LOGGER.debug("V2 compatibility mode: Unknown provider key: {}", SecurityUtils.maskSecret(providerKey));
            return false;
        }

        ProviderConfig config = match.get().config();
        String foundProviderId = match.get().providerId();

        if (!config.getAllowedEvents().contains(eventType)) {
            LOGGER.debug("V2 compatibility mode: Event type {} not allowed for provider {} (key: {})",
                    eventType, foundProviderId, SecurityUtils.maskSecret(providerKey));
            return false;
        }

        boolean ipAuthorized = IPValidationUtils.isIpAuthorized(sourceIP, config.getIpAddresses());
        if (!ipAuthorized) {
            LOGGER.debug("V2 compatibility mode: IP {} not authorized for provider {} (key: {})",
                    sourceIP, foundProviderId, SecurityUtils.maskSecret(providerKey));
        }
        return ipAuthorized;
    }

    /**
     * Resolves the client IP for peer checks: the direct remote address, or the first
     * {@code X-Forwarded-For} hop only when that direct address is a configured trusted proxy.
     *
     * @param request the HTTP request
     * @return the IP to match against provider allow-lists
     */
    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!trustedProxies.isEmpty() && IPValidationUtils.isIpAuthorized(remoteAddr, trustedProxies)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.isNotBlank(xff)) {
                String candidate = xff.split(",")[0].trim();
                if (StringUtils.isNotBlank(candidate)) {
                    return candidate;
                }
            }
        }
        return remoteAddr;
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

    /** @return whether the example key is currently allowed */
    public boolean isAllowExampleKey() {
        return allowExampleKey;
    }

    /** @return configured trusted proxy CIDRs / addresses */
    public Set<String> getTrustedProxies() {
        return trustedProxies;
    }

    /** @return a snapshot of active providers (for tests) */
    public Map<String, ProviderConfig> getProviders() {
        return Collections.unmodifiableMap(providers);
    }

    private Optional<ProviderMatch> findProviderByKey(String providerKey) {
        for (Map.Entry<String, ProviderConfig> entry : providers.entrySet()) {
            if (SecurityUtils.constantTimeEquals(providerKey, entry.getValue().getKey())) {
                return Optional.of(new ProviderMatch(entry.getKey(), entry.getValue()));
            }
        }
        return Optional.empty();
    }

    private static final class ProviderMatch {
        private final String providerId;
        private final ProviderConfig config;

        private ProviderMatch(String providerId, ProviderConfig config) {
            this.providerId = providerId;
            this.config = config;
        }

        private String providerId() {
            return providerId;
        }

        private ProviderConfig config() {
            return config;
        }
    }

    private Set<String> parseAbilities(String value) {
        if (StringUtils.isBlank(value)) {
            return new HashSet<>(CompatPeerPrincipal.DEFAULT_ABILITIES);
        }
        Set<String> parsed = parseCommaSeparatedList(value);
        Set<String> known = new HashSet<>();
        for (String ability : parsed) {
            if (CompatPeerPrincipal.DEFAULT_ABILITIES.contains(ability)) {
                known.add(ability);
            } else {
                LOGGER.warn("Ignoring unknown third-party ability '{}'", ability);
            }
        }
        return known.isEmpty() ? new HashSet<>(CompatPeerPrincipal.DEFAULT_ABILITIES) : known;
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : "";
    }

    private Set<String> parseCommaSeparatedList(String value) {
        if (StringUtils.isBlank(value)) {
            return new HashSet<>();
        }
        Set<String> result = new HashSet<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (StringUtils.isNotBlank(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
