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

import java.util.HashMap;
import java.util.Map;

/**
 * @author Jerome Blanchard
 */
@Component(immediate = true, service = HealthCheckConfig.class, configurationPid = {"org.apache.unomi.healthcheck"})
public class HealthCheckConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckConfig.class.getName());

    public static final String CONFIG_ES_ADDRESSES = "esAddresses";
    public static final String CONFIG_ES_SSL_ENABLED = "esSSLEnabled";
    public static final String CONFIG_ES_LOGIN = "esLogin";
    public static final String CONFIG_ES_PASSWORD = "esPassword";
    public static final String CONFIG_TRUST_ALL_CERTIFICATES = "httpClient.trustAllCertificates";

    private Map<String, String> config = new HashMap<>();

    @Activate
    @Modified
    public void modified(Map<String, String> config) {
        LOGGER.info("Updating healthcheck configuration, config size: {}", config.size());
        this.config = config;
    }

    public String get(String configKey) {
        return this.config.get(configKey);
    }
}
