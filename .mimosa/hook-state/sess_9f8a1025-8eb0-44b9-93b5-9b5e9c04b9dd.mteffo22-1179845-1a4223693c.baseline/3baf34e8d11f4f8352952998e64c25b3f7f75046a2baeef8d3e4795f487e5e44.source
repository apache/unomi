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

package org.apache.unomi.didvc.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.didvc.api.DidDocumentData;
import org.apache.unomi.didvc.api.services.DidMethodResolver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP DID-method driver in the Danube Tech Universal Resolver pattern:
 * delegates resolution of one DID method (iAM Smart, RealDID, …) to a
 * remote driver endpoint serving the Universal Resolver HTTP API
 * ({@code GET {baseUrl}/1.0/identifiers/{did}}).
 *
 * <p>The component is config-required (DS {@code REQUIRE}): it only
 * activates when a configuration supplies the driver URL, so the built-in
 * bundle ships inactive and operators enable the methods they use. Two
 * component descriptors register this class — {@code didvc.resolver.method}
 * selects the served method ({@code iamsmart} / {@code realdid}) and
 * {@code didvc.resolver.url} the driver base URL. When neither a
 * configured driver nor a registry stub exists for a method, resolution
 * returns null.</p>
 */
@Component(service = DidMethodResolver.class,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = "didvc.did.method=iamsmart",
        configurationPid = "org.apache.unomi.didvc.resolver.http")
public class HttpDidMethodResolver implements DidMethodResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpDidMethodResolver.class);
    private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String method;
    private volatile String baseUrl;
    private volatile long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;

    @Activate
    public void activate(Map<String, Object> properties) {
        method = stringProperty(properties, "didvc.resolver.method", "iamsmart");
        baseUrl = stringProperty(properties, "didvc.resolver.url", null);
        if (baseUrl == null) {
            throw new IllegalArgumentException("didvc.resolver.url is required to activate the "
                    + method + " HTTP DID-method driver");
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String timeout = stringProperty(properties, "didvc.resolver.timeoutMillis", null);
        if (timeout != null) {
            timeoutMillis = Long.parseLong(timeout);
        }
        LOGGER.info("Activated {} DID-method driver against {}", method, baseUrl);
    }

    @Deactivate
    public void deactivate() {
        baseUrl = null;
    }

    public HttpDidMethodResolver() {
    }

    /**
     * Direct-construction form for tests and embedders.
     *
     * @param method  the DID method to serve
     * @param baseUrl the driver base URL
     */
    public HttpDidMethodResolver(String method, String baseUrl) {
        this.method = method;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public DidDocumentData resolve(String did) {
        String url = baseUrl + "/1.0/identifiers/" + did;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout((int) timeoutMillis);
            connection.setReadTimeout((int) timeoutMillis);
            connection.setRequestProperty("Accept", "application/ld+json, application/json");
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND || status == HttpURLConnection.HTTP_GONE) {
                return null;
            }
            if (status < 200 || status >= 300) {
                LOGGER.warn("{} driver for {} returned HTTP {}", method, did, status);
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                return objectMapper.readValue(in, DidDocumentData.class);
            }
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Resolution of {} through the {} driver failed", did, method, e);
            return null;
        }
    }

    private static String stringProperty(Map<String, Object> properties, String key, String defaultValue) {
        Object value = properties.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }
}
