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

package org.apache.unomi.healthcheck.provider;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.healthcheck.HealthCheckConfig;
import org.apache.unomi.healthcheck.HealthCheckProvider;
import org.apache.unomi.healthcheck.HealthCheckResponse;
import org.apache.unomi.healthcheck.util.CachedValue;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * A Health Check that checks the status of the OpenSearch connectivity according to the provided configuration.
 * This connectivity should be LIVE before any try to start Unomi.
 */
@Component(service = HealthCheckProvider.class, immediate = true)
public class OpenSearchHealthCheckProvider implements HealthCheckProvider {

    public static final String NAME = "opensearch";

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchHealthCheckProvider.class.getName());
    private final CachedValue<HealthCheckResponse> cache = new CachedValue<>(10, TimeUnit.SECONDS);

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private HealthCheckConfig config;

    private CloseableHttpClient httpClient;

    @Reference(service = PersistenceService.class, cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, bind = "bind", unbind = "unbind")
    private volatile PersistenceService persistenceService;

    public void bind(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void unbind(PersistenceService persistenceService) {
        this.persistenceService = null;
    }

    public OpenSearchHealthCheckProvider() {
        LOGGER.info("Building OpenSearch health provider service...");
    }

    @Override
    public boolean isAvailable() {
        return persistenceService != null && "opensearch".equals(persistenceService.getName());
    }

    @Activate
    public void activate() {
        LOGGER.info("Activating OpenSearch health provider service...");
        CredentialsProvider credentialsProvider = null;
        String login = config.get(HealthCheckConfig.CONFIG_OS_LOGIN); // Reuse ElasticSearch credentials key
        if (StringUtils.isNotEmpty(login)) {
            credentialsProvider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials
                    = new UsernamePasswordCredentials(login, config.get(HealthCheckConfig.CONFIG_OS_PASSWORD));
            credentialsProvider.setCredentials(AuthScope.ANY, credentials);
        }
        try {
            httpClient = HttpUtils.initHttpClient(
                    Boolean.parseBoolean(config.get(HealthCheckConfig.CONFIG_OS_TRUST_ALL_CERTIFICATES)), credentialsProvider);
        } catch (IOException e) {
            LOGGER.error("Unable to initialize http client", e);
        }
    }

    public void setConfig(HealthCheckConfig config) {
        this.config = config;
    }

    @Override public String name() {
        return NAME;
    }

    @Override public HealthCheckResponse execute() {
        LOGGER.debug("Health check OpenSearch");
        if (cache.isStaled() || cache.getValue().isDown() || cache.getValue().isError()) {
            cache.setValue(refresh());
        }
        return cache.getValue();
    }

    private HealthCheckResponse refresh() {
        LOGGER.debug("Refresh");
        HealthCheckResponse.Builder builder = new HealthCheckResponse.Builder();
        builder.name(NAME).down();
        String minimalClusterState = config.get(HealthCheckConfig.CONFIG_OS_MINIMAL_CLUSTER_STATE);
        if (StringUtils.isEmpty(minimalClusterState)) {
            minimalClusterState = "green";
        } else {
            minimalClusterState = minimalClusterState.toLowerCase();
        }
        String url = (config.get(HealthCheckConfig.CONFIG_OS_SSL_ENABLED).equals("true") ? "https://" : "http://")
                .concat(config.get(HealthCheckConfig.CONFIG_OS_ADDRESSES).split(",")[0].trim())
                .concat("/_cluster/health");
        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(new HttpGet(url));
            if (response != null && response.getStatusLine().getStatusCode() == 200) {
                builder.up();
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    String content = EntityUtils.toString(entity);
                    if (content.contains("\"status\":\"green\"") ||
                        content.contains("\"status\":\"yellow\"") && minimalClusterState.equals("yellow")) {
                        builder.live();
                    }
                }
            }
        } catch (IOException e) {
            builder.error().withData("error", e.getMessage());
            LOGGER.error("Error while checking OpenSearch health", e);
        } finally {
            if (response != null) {
                EntityUtils.consumeQuietly(response.getEntity());
            }
        }
        return builder.build();
    }
}
