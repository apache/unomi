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

package org.apache.unomi.healthcheck;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Health check configuration (UNOMI-1007). Values usually come from
 * {@code etc/org.apache.unomi.healthcheck.cfg}, which resolves
 * {@code org.apache.unomi.healthcheck.*} system properties — those can be set from
 * {@code UNOMI_HEALTHCHECK_ENABLED}, {@code UNOMI_HEALTHCHECK_PROVIDERS} and
 * {@code UNOMI_HEALTHCHECK_TIMEOUT}. Any enabled value other than {@code true} disables the endpoint;
 * an invalid timeout falls back to the default with a warning.
 * None of these settings can turn authentication off.
 */
@Component(immediate = true, service = HealthCheckConfig.class, configurationPid = {"org.apache.unomi.healthcheck"})
public class HealthCheckConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckConfig.class.getName());

    public static final String CONFIG_ES_ADDRESSES = "esAddresses";
    public static final String CONFIG_ES_SSL_ENABLED = "esSSLEnabled";
    public static final String CONFIG_ES_LOGIN = "esLogin";
    public static final String CONFIG_ES_PASSWORD = "esPassword";
    public static final String CONFIG_ES_TRUST_ALL_CERTIFICATES = "esHttpClient.trustAllCertificates";
    public static final String CONFIG_ES_MINIMAL_CLUSTER_STATE = "esMinimalClusterState";
    public static final String CONFIG_OS_ADDRESSES = "osAddresses";
    public static final String CONFIG_OS_SSL_ENABLED = "osSSLEnabled";
    public static final String CONFIG_OS_LOGIN = "osLogin";
    public static final String CONFIG_OS_PASSWORD = "osPassword";
    public static final String CONFIG_OS_TRUST_ALL_CERTIFICATES = "osHttpClient.trustAllCertificates";
    public static final String CONFIG_OS_MINIMAL_CLUSTER_STATE = "osMinimalClusterState";
    public static final String CONFIG_AUTH_REALM = "authentication.realm";
    public static final String ENABLED = "healthcheck.enabled";
    public static final String PROVIDERS = "healthcheck.providers";
    public static final String TIMEOUT = "healthcheck.timeout";

    /** Default when enabled is missing. */
    public static final boolean DEFAULT_ENABLED = true;
    /** Default provider timeout in milliseconds. */
    public static final int DEFAULT_TIMEOUT_MS = 400;
    /** Lowest accepted timeout (milliseconds). */
    public static final int MIN_TIMEOUT_MS = 1;
    /** Highest accepted timeout (milliseconds). */
    public static final int MAX_TIMEOUT_MS = 60_000;
    /** JAAS realm used when the cfg does not set {@link #CONFIG_AUTH_REALM}. */
    public static final String DEFAULT_AUTH_REALM = "karaf";

    private Map<String, String> config = new HashMap<>();
    private boolean enabled = DEFAULT_ENABLED;
    private List<String> enabledProviders = new ArrayList<>();
    private int timeout = DEFAULT_TIMEOUT_MS;

    @Activate
    @Modified
    public void modified(Map<String, String> config) {
        LOGGER.info("Updating healthcheck configuration, config size: {}", config == null ? 0 : config.size());
        apply(config == null ? Map.of() : config);
    }

    /**
     * Applies a configuration map with validation. Package-visible for unit tests.
     *
     * @param config OSGi configuration entries; may be empty but not {@code null}
     */
    void apply(Map<String, String> config) {
        Map<String, String> values = new HashMap<>(config);
        String realm = values.get(CONFIG_AUTH_REALM);
        if (realm == null || realm.trim().isEmpty()) {
            values.put(CONFIG_AUTH_REALM, DEFAULT_AUTH_REALM);
        }
        this.setConfig(values);
        this.setEnabled(parseEnabled(config.get(ENABLED)));
        this.setEnabledProviders(parseProviders(config.get(PROVIDERS)));
        this.setTimeout(parseTimeout(config.get(TIMEOUT)));
    }

    static boolean parseEnabled(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_ENABLED;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        LOGGER.warn("Invalid healthcheck.enabled value '{}'; disabling the health check endpoint", raw);
        return false;
    }

    static List<String> parseProviders(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> providers = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            providers.add(name);
        }
        return new ArrayList<>(providers);
    }

    static int parseTimeout(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_TIMEOUT_MS;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("Ignoring invalid healthcheck.timeout value '{}'; using {}", raw, DEFAULT_TIMEOUT_MS);
            return DEFAULT_TIMEOUT_MS;
        }
        if (parsed < MIN_TIMEOUT_MS || parsed > MAX_TIMEOUT_MS) {
            LOGGER.warn("Ignoring healthcheck.timeout {} outside {}-{} ms; using {}",
                    parsed, MIN_TIMEOUT_MS, MAX_TIMEOUT_MS, DEFAULT_TIMEOUT_MS);
            return DEFAULT_TIMEOUT_MS;
        }
        return parsed;
    }

    public String get(String configKey) {
        return this.config.get(configKey);
    }

    public int getSize() {
        return this.config.size();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getEnabledProviders() {
        return enabledProviders;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setConfig(Map<String, String> config) {
        this.config = config;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setEnabledProviders(List<String> enabledProviders) {
        this.enabledProviders = enabledProviders;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
