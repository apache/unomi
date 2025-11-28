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
 * limitations under the License
 */

package org.apache.unomi.itests;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.fail;

/**
 * Health Check Integration Tests
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class HealthCheckIT extends BaseIT {

    private final static Logger LOGGER = LoggerFactory.getLogger(HealthCheckIT.class);

    protected static final String HEALTHCHECK_AUTH_USER_NAME = "health";
    protected static final String HEALTHCHECK_AUTH_PASSWORD = "health";
    protected static final String HEALTHCHECK_ENDPOINT = "/health/check";

    @Test
    public void testHealthCheck() {
        try {
            List<HealthCheckResponse> response = get(HEALTHCHECK_ENDPOINT, new TypeReference<>() {});
            LOGGER.info("health check response: {}", response);
            Assert.assertNotNull(response);
            Assert.assertEquals(5, response.size());
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("karaf") && r.getStatus() == HealthCheckResponse.Status.LIVE));
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals(searchEngine) && r.getStatus() == HealthCheckResponse.Status.LIVE));
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("unomi") && r.getStatus() == HealthCheckResponse.Status.LIVE));
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("persistence") && r.getStatus() == HealthCheckResponse.Status.LIVE));
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("cluster") && r.getStatus() == HealthCheckResponse.Status.LIVE));
        } catch (Exception e) {
            LOGGER.error("Error while executing health check", e);
            fail("Error while executing health check" + e.getMessage());
        }
    }

    protected <T> T get(final String url, TypeReference<T> typeReference) {
        CloseableHttpResponse response = null;
        try {
            final HttpGet httpGet = new HttpGet(getFullUrl(url));
            response = executeHttpRequest(httpGet);
            if (response.getStatusLine().getStatusCode() == 200 || response.getStatusLine().getStatusCode() == 206) {
                return objectMapper.readValue(response.getEntity().getContent(), typeReference);
            } else {
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Error performing GET request with url {}", url, e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    LOGGER.error("Error closing response: ", e);
                }
            }
        }
        return null;
    }

    public BasicCredentialsProvider getHttpClientCredentialProvider() {
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(HEALTHCHECK_AUTH_USER_NAME, HEALTHCHECK_AUTH_PASSWORD));
        return credsProvider;
    }

    public static class HealthCheckResponse {
        private String name;
        private Status status;
        private long collectingTime;
        private Map<String, Object> data;

        public HealthCheckResponse() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public long getCollectingTime() {
            return collectingTime;
        }

        public void setCollectingTime(long collectingTime) {
            this.collectingTime = collectingTime;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public void setData(Map<String, Object> data) {
            this.data = data;
        }

        @Override public String toString() {
            return "HealthCheckResponse{" + "name='" + name + '\'' + ", status=" + status + ", collectingTime=" + collectingTime + ", data="
                    + data + '}';
        }

        public enum Status {
            DOWN,     //Not available
            UP,       //Running or starting
            LIVE,     //Ready to serve requests
            ERROR     //Errors during check
        }
    }
}
