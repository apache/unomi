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
import java.util.concurrent.TimeUnit;

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
            // Retry health check until all required providers are LIVE
            List<HealthCheckResponse> response = waitForHealthCheckReady();
            LOGGER.info("health check response: {}", response);
            Assert.assertNotNull(response);
            // Health check may return 4 or 5 providers depending on configuration (persistence may be included)
            Assert.assertTrue("Health check should return at least 4 providers", response.size() >= 4);
            Assert.assertTrue("Karaf health check should be LIVE",
                    response.stream().anyMatch(r -> r.getName().equals("karaf") && r.getStatus() == HealthCheckResponse.Status.LIVE));
            Assert.assertTrue("Search engine (" + searchEngine + ") health check should be LIVE",
                    response.stream().anyMatch(r -> r.getName().equals(searchEngine) && r.getStatus() == HealthCheckResponse.Status.LIVE));
            Assert.assertTrue("Unomi health check should be LIVE",
                    response.stream().anyMatch(r -> r.getName().equals("unomi") && r.getStatus() == HealthCheckResponse.Status.LIVE));
            Assert.assertTrue("Cluster health check should be LIVE",
                    response.stream().anyMatch(r -> r.getName().equals("cluster") && r.getStatus() == HealthCheckResponse.Status.LIVE));
        } catch (Exception e) {
            LOGGER.error("Error while executing health check", e);
            fail("Error while executing health check" + e.getMessage());
        }
    }

    /**
     * Waits for health check to be ready by retrying until all required providers are LIVE.
     * This method retries the health check with exponential backoff up to a maximum timeout.
     *
     * @return List of health check responses when all required providers are LIVE
     * @throws InterruptedException if the maximum timeout is reached
     */
    private List<HealthCheckResponse> waitForHealthCheckReady() throws InterruptedException {
        final long maxWaitTime = TimeUnit.SECONDS.toMillis(30); // Maximum 30 seconds
        final long initialRetryInterval = 500; // Start with 500ms
        final long maxRetryInterval = 2000; // Max 2 seconds between retries
        final long startTime = System.currentTimeMillis();
        long retryInterval = initialRetryInterval;
        int attemptCount = 0;

        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            attemptCount++;
            try {
                List<HealthCheckResponse> response = get(HEALTHCHECK_ENDPOINT, new TypeReference<>() {});
                
                if (response != null && response.size() >= 4) {
                    // Check if all required providers are LIVE
                    boolean karafLive = response.stream().anyMatch(r -> r.getName().equals("karaf") && r.getStatus() == HealthCheckResponse.Status.LIVE);
                    boolean searchEngineLive = response.stream().anyMatch(r -> r.getName().equals(searchEngine) && r.getStatus() == HealthCheckResponse.Status.LIVE);
                    boolean unomiLive = response.stream().anyMatch(r -> r.getName().equals("unomi") && r.getStatus() == HealthCheckResponse.Status.LIVE);
                    boolean clusterLive = response.stream().anyMatch(r -> r.getName().equals("cluster") && r.getStatus() == HealthCheckResponse.Status.LIVE);

                    if (karafLive && searchEngineLive && unomiLive && clusterLive) {
                        LOGGER.info("All health checks are LIVE after {} attempts ({} ms)", attemptCount, System.currentTimeMillis() - startTime);
                        return response;
                    } else {
                        LOGGER.debug("Health check attempt {}: karaf={}, {}={}, unomi={}, cluster={}", 
                                attemptCount, karafLive, searchEngine, searchEngineLive, unomiLive, clusterLive);
                    }
                } else {
                    LOGGER.debug("Health check attempt {}: response is null or has insufficient providers (size: {})", 
                            attemptCount, response != null ? response.size() : 0);
                }
            } catch (Exception e) {
                LOGGER.debug("Health check attempt {} failed: {}", attemptCount, e.getMessage());
            }

            // Wait before retrying with exponential backoff
            Thread.sleep(retryInterval);
            retryInterval = Math.min(retryInterval * 2, maxRetryInterval);
        }

        // Final attempt without waiting
        List<HealthCheckResponse> finalResponse = get(HEALTHCHECK_ENDPOINT, new TypeReference<>() {});
        if (finalResponse == null) {
            throw new InterruptedException("Health check did not become ready within " + maxWaitTime + " ms after " + attemptCount + " attempts");
        }
        return finalResponse;
    }

    protected <T> T get(final String url, TypeReference<T> typeReference) {
        CloseableHttpResponse response = null;
        try {
            final HttpGet httpGet = new HttpGet(getFullUrl(url));
            response = executeHttpRequest(httpGet, AuthType.CUSTOM_BASIC, HEALTHCHECK_AUTH_USER_NAME, HEALTHCHECK_AUTH_PASSWORD);
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
