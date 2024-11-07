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
import java.util.List;
import java.util.Map;

/**
 * Health check configuration.
 */
@Component(immediate = true, service = HealthCheckConfig.class, configurationPid = {"org.apache.unomi.healthcheck"})
public class HealthCheckConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckConfig.class.getName());

    public static final String CONFIG_ES_ADDRESSES = "esAddresses";
    public static final String CONFIG_ES_SSL_ENABLED = "esSSLEnabled";
    public static final String CONFIG_ES_LOGIN = "esLogin";
    public static final String CONFIG_ES_PASSWORD = "esPassword";
    public static final String CONFIG_TRUST_ALL_CERTIFICATES = "httpClient.trustAllCertificates";
    public static final String CONFIG_AUTH_REALM = "authentication.realm";
    public static final String ENABLED = "healthcheck.enabled";
    public static final String PROVIDERS = "healthcheck.providers";
    public static final String TIMEOUT = "healthcheck.timeout";

    private Map<String, String> config = new HashMap<>();
    private boolean enabled = true;
    private List<String> enabledProviders = new ArrayList<>();
    private int timeout = 400;

    @Activate
    @Modified
    public void modified(Map<String, String> config) {
        LOGGER.info("Updating healthcheck configuration, config size: {}", config.size());
        this.setConfig(config);
        this.setEnabled(config.getOrDefault(ENABLED, "true").equalsIgnoreCase("true"));
        this.setEnabledProviders(config.getOrDefault(PROVIDERS, "").isEmpty() ? new ArrayList<>() : List.of(config.get(PROVIDERS).split(",")));
        this.setTimeout(Integer.parseInt(config.getOrDefault(TIMEOUT, "400")));
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
