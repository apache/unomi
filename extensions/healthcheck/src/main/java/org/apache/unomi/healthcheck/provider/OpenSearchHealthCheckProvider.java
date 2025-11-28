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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.unomi.healthcheck.HealthCheckResponse;
import org.apache.unomi.healthcheck.util.CachedValue;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * A Health Check that checks the status of the OpenSearch connectivity according to the provided configuration.
 * This connectivity should be LIVE before any try to start Unomi.
 */
public class OpenSearchHealthCheckProvider implements PersistenceEngineHealthProvider {

    public static final String NAME = "opensearch";

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchHealthCheckProvider.class.getName());
    private final CachedValue<HealthCheckResponse> cache = new CachedValue<>(10, TimeUnit.SECONDS);

    private HealthCheckConfig config;

    private CloseableHttpClient httpClient;

    public OpenSearchHealthCheckProvider() {
        LOGGER.info("Building OpenSearch health provider service...");
    }

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

    @Override public HealthCheckResponse detailed() {
        return execute();
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
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root = mapper.readTree(content);
                        String status = root.has("status") ? root.get("status").asText() : null;
                        if ("green".equals(status) || ("yellow".equals(status) && "yellow".equals(minimalClusterState))) {
                            builder.live();
                        }
                        if (root.has("cluster_name")) builder.withData("cluster_name", root.get("cluster_name").asText());
                        if (root.has("status")) builder.withData("status", root.get("status").asText());
                        if (root.has("timed_out")) builder.withData("timed_out", root.get("timed_out").asBoolean());
                        if (root.has("number_of_nodes")) builder.withData("number_of_nodes", root.get("number_of_nodes").asLong());
                        if (root.has("number_of_data_nodes")) builder.withData("number_of_data_nodes", root.get("number_of_data_nodes").asLong());
                        if (root.has("active_primary_shards")) builder.withData("active_primary_shards", root.get("active_primary_shards").asLong());
                        if (root.has("active_shards")) builder.withData("active_shards", root.get("active_shards").asLong());
                        if (root.has("relocating_shards")) builder.withData("relocating_shards", root.get("relocating_shards").asLong());
                        if (root.has("initializing_shards")) builder.withData("initializing_shards", root.get("initializing_shards").asLong());
                        if (root.has("unassigned_shards")) builder.withData("unassigned_shards", root.get("unassigned_shards").asLong());
                    } catch (Exception parseEx) {
                        if (content.contains("\"status\":\"green\"") ||
                                (content.contains("\"status\":\"yellow\"") && "yellow".equals(minimalClusterState))) {
                            builder.live();
                        }
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
