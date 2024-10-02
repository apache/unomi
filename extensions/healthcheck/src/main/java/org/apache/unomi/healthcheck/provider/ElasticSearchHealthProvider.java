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
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.healthcheck.HealthCheckResponse;
import org.apache.unomi.healthcheck.HealthProvider;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Jerome Blanchard
 */
@Component(service = HealthProvider.class, immediate = true)
public class ElasticSearchHealthProvider implements HealthProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchHealthProvider.class.getName());
    public static final String NAME = "elasticsearch";

    private CloseableHttpClient httpClient;

    public ElasticSearchHealthProvider() {
        LOGGER.info("Building elasticsearch health provider service...");
        httpClient = HttpClients.createDefault();
    }

    @Override public String name() {
        return NAME;
    }

    @Override public HealthCheckResponse execute() {
        LOGGER.debug("Health check elasticsearch");
        HealthCheckResponse.Builder builder = new HealthCheckResponse.Builder();
        builder.name(NAME).down();
        //TODO Parse addresses from configuration
        HttpGet httpGet = new HttpGet("http://localhost:9200/_cluster/health");
        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(httpGet);
            if (response != null && response.getStatusLine().getStatusCode() == 200) {
                builder.up();
                HttpEntity entity = response.getEntity();
                if (entity != null && EntityUtils.toString(entity).contains("\"status\":\"green\"")) {
                    builder.live();
                    //TODO parse and add cluster data
                }
            }
        } catch (IOException e) {
            builder.error().withData("error", e.getMessage());
        } finally {
            if (response != null) {
                EntityUtils.consumeQuietly(response.getEntity());
            }
        }
        return builder.build();
    }
}
