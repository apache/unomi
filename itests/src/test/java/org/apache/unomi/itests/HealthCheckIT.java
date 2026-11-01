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
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.*;

import static org.junit.Assert.fail;

/**
 * Health Check Integration Tests.
 * <p>
 * Always asserts {@code karaf}, {@code unomi}, and {@code persistence} LIVE.
 * Optional probes ({@code providerNamedHealthProbe}, {@code clusterHealthProbe}) are
 * asserted only when the active {@link org.apache.unomi.itests.persistence.PersistenceITCapabilities}
 * advertise them — so the same class covers Elasticsearch, OpenSearch, PostgreSQL, etc.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class HealthCheckIT extends BaseIT {

    private final static Logger LOGGER = LoggerFactory.getLogger(HealthCheckIT.class);

    protected static final String HEALTHCHECK_ENDPOINT = "/health/check";
    private static final String HEALTHCHECK_PID = "org.apache.unomi.healthcheck";

    @Test
    public void testHealthCheck() {
        try {
            // When every probe is LIVE, clients (and HA proxies) expect HTTP 200 — same as Unomi 3.0.
            List<HealthCheckResponse> response = get(HEALTHCHECK_ENDPOINT, new TypeReference<>() {}, 200);
            LOGGER.info("configured persistence provider: {}", getPersistenceBackend().providerId());
            LOGGER.info("health check response: {}", response);
            Assert.assertNotNull(response);
            assertHealthCheckLive(response);
        } catch (Exception e) {
            LOGGER.error("Error while executing health check", e);
            fail("Error while executing health check" + e.getMessage());
        }
    }

    @Test
    public void testConcurrentHealthCheck() {
        final int NB_THREADS = 10;
        final int NB_ITERATIONS = 20;

        ExecutorService executorService = null;
        try {
            executorService = Executors.newFixedThreadPool(NB_THREADS);
            List<Future<List<HealthCheckResponse>>> futures = new ArrayList<>();
            for (int i = 0; i < NB_ITERATIONS; i++) {
                for (int j = 0; j < NB_THREADS; j++) {
                    // UNOMI-924: concurrent calls must not fail with HTTP 500; accept 200 or 206.
                    Future<List<HealthCheckResponse>> future = executorService.submit(
                            () -> get(HEALTHCHECK_ENDPOINT, new TypeReference<>() {}, 200, 206));
                    futures.add(future);
                }
                for (Future<List<HealthCheckResponse>> future : futures) {
                    List<HealthCheckResponse> health = future.get(10, TimeUnit.SECONDS);
                    Assert.assertNotNull("Concurrent health check returned null (unexpected HTTP status)", health);
                    assertHealthCheckLive(health);
                }
                Thread.sleep(10);
            }
            executorService.shutdown();
            Assert.assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
        } catch (Exception e) {
            LOGGER.error("Error while executing concurrent health check", e);
            fail("Error while executing concurrent health check: " + e.getMessage());
        } finally {
            if ( executorService != null ) {
                executorService.shutdownNow();
            }
        }
    }

    private void assertHealthCheckLive(List<HealthCheckResponse> response) {
        Assert.assertNotNull(response);
        assertResponseFormatLike30(response);
        Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("karaf") && r.getStatus() == HealthCheckResponse.Status.LIVE));
        Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("unomi") && r.getStatus() == HealthCheckResponse.Status.LIVE));
        Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("persistence") && r.getStatus() == HealthCheckResponse.Status.LIVE));
        int expectedMinProbes = 3; // karaf, unomi, persistence
        boolean named = persistenceCapabilities().providerNamedHealthProbe();
        boolean cluster = persistenceCapabilities().clusterHealthProbe();
        if (named) {
            expectedMinProbes++;
            String namedProbe = getPersistenceBackend().providerId();
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals(namedProbe) && r.getStatus() == HealthCheckResponse.Status.LIVE));
        }
        if (cluster) {
            expectedMinProbes++;
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("cluster") && r.getStatus() == HealthCheckResponse.Status.LIVE));
        }
        // ES/OS advertise both optional probes → keep historical exact size (5).
        // Other backends may expose extra probes; require at least the expected set.
        if (named && cluster) {
            Assert.assertEquals("Unexpected health probe count: " + response, expectedMinProbes, response.size());
        } else {
            Assert.assertTrue(
                    "Expected at least " + expectedMinProbes + " health probes, got " + response.size() + ": " + response,
                    response.size() >= expectedMinProbes);
        }
    }

    /**
     * Wire format shared with Unomi 3.0: each item has {@code name}, {@code status},
     * {@code collectingTime}; optional {@code data}; items sorted by name.
     */
    private static void assertResponseFormatLike30(List<HealthCheckResponse> response) {
        List<String> names = new ArrayList<>();
        for (HealthCheckResponse item : response) {
            Assert.assertNotNull("Health check item missing name: " + item, item.getName());
            Assert.assertFalse("Health check name must not be blank", item.getName().isBlank());
            Assert.assertNotNull("Health check item missing status: " + item, item.getStatus());
            Assert.assertTrue("collectingTime must be >= 0: " + item, item.getCollectingTime() >= 0);
            names.add(item.getName());
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareTo);
        Assert.assertEquals("Health check items must be sorted by name (Unomi 3.0 servlet behaviour)", sorted, names);
    }

    @Test
    public void testHealthCheck_wrongPasswordRejected() throws Exception {
        final HttpGet httpGet = new HttpGet(getFullUrl(HEALTHCHECK_ENDPOINT));
        try (CloseableHttpResponse response = executeHttpRequest(
                httpGet, AuthType.CUSTOM_BASIC, HEALTHCHECK_AUTH_USER_NAME, "wrong-password")) {
            Assert.assertEquals(401, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testHealthCheck_disabledUnregistersEndpoint() throws Exception {
        Dictionary<String, Object> original = healthCheckConfiguration();
        try {
            updateHealthCheckConfiguration(original, Map.of("healthcheck.enabled", "false"));
            keepTrying("/health/check should be unregistered when the health check is disabled",
                    () -> healthCheckStatus(AuthType.CUSTOM_BASIC, HEALTHCHECK_AUTH_PASSWORD), status -> status == 404,
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            Assert.assertNotEquals(200, healthCheckStatus(AuthType.NONE, null));
        } finally {
            restoreHealthCheckConfiguration(original);
        }
    }

    @Test
    public void testHealthCheck_reducedProviderListAndCredentialsStillRequired() throws Exception {
        Dictionary<String, Object> original = healthCheckConfiguration();
        try {
            updateHealthCheckConfiguration(original, Map.of("healthcheck.providers", "unomi,persistence,notInstalled"));
            keepTrying("Health check should only report karaf and the configured providers",
                    () -> probeNames(get(HEALTHCHECK_ENDPOINT, new TypeReference<List<HealthCheckResponse>>() {}, 200, 206)),
                    names -> Set.of("karaf", "unomi", "persistence").equals(names),
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            Assert.assertEquals(401, healthCheckStatus(AuthType.NONE, null));
            Assert.assertEquals(401, healthCheckStatus(AuthType.CUSTOM_BASIC, "wrong-password"));
        } finally {
            restoreHealthCheckConfiguration(original);
        }
    }

    private Dictionary<String, Object> healthCheckConfiguration() throws IOException {
        Dictionary<String, Object> properties = configurationAdmin.getConfiguration(HEALTHCHECK_PID, null).getProperties();
        Assert.assertNotNull("Missing " + HEALTHCHECK_PID + " configuration", properties);
        Dictionary<String, Object> copy = new Hashtable<>();
        for (Enumeration<String> keys = properties.keys(); keys.hasMoreElements(); ) {
            String key = keys.nextElement();
            copy.put(key, properties.get(key));
        }
        return copy;
    }

    private void updateHealthCheckConfiguration(Dictionary<String, Object> original, Map<String, String> changes) throws IOException {
        Dictionary<String, Object> updated = new Hashtable<>();
        for (Enumeration<String> keys = original.keys(); keys.hasMoreElements(); ) {
            String key = keys.nextElement();
            updated.put(key, original.get(key));
        }
        changes.forEach(updated::put);
        configurationAdmin.getConfiguration(HEALTHCHECK_PID, null).update(updated);
    }

    private void restoreHealthCheckConfiguration(Dictionary<String, Object> original) throws IOException, InterruptedException {
        configurationAdmin.getConfiguration(HEALTHCHECK_PID, null).update(original);
        keepTrying("Health check should be back to its original configuration",
                () -> get(HEALTHCHECK_ENDPOINT, new TypeReference<List<HealthCheckResponse>>() {}, 200),
                response -> {
                    try {
                        assertHealthCheckLive(response);
                        return true;
                    } catch (AssertionError e) {
                        return false;
                    }
                },
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    private int healthCheckStatus(AuthType authType, String password) {
        try {
            HttpGet httpGet = new HttpGet(getFullUrl(HEALTHCHECK_ENDPOINT));
            try (CloseableHttpResponse response = executeHttpRequest(httpGet, authType,
                    authType == AuthType.NONE ? null : HEALTHCHECK_AUTH_USER_NAME, password)) {
                return response.getStatusLine().getStatusCode();
            }
        } catch (Exception e) {
            LOGGER.warn("Health check request failed", e);
            return -1;
        }
    }

    private static Set<String> probeNames(List<HealthCheckResponse> response) {
        return response == null ? null : response.stream().map(HealthCheckResponse::getName).collect(Collectors.toSet());
    }

    protected <T> T get(final String url, TypeReference<T> typeReference, int... acceptedStatuses) {
        int[] statuses = (acceptedStatuses == null || acceptedStatuses.length == 0)
                ? new int[] { 200, 206 }
                : acceptedStatuses;
        CloseableHttpResponse response = null;
        try {
            final HttpGet httpGet = new HttpGet(getFullUrl(url));
            response = executeHttpRequest(httpGet, AuthType.CUSTOM_BASIC, HEALTHCHECK_AUTH_USER_NAME, HEALTHCHECK_AUTH_PASSWORD);
            int code = response.getStatusLine().getStatusCode();
            for (int accepted : statuses) {
                if (code == accepted) {
                    return getObjectMapper().readValue(response.getEntity().getContent(), typeReference);
                }
            }
            LOGGER.error("Unexpected health check HTTP status {} (accepted {})", code, java.util.Arrays.toString(statuses));
            return null;
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
