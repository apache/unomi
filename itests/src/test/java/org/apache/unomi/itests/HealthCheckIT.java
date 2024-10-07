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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.apache.commons.io.IOUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.karaf.itests.KarafTestSupport;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.lifecycle.BundleWatcher;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;

/**
 * Health Check Integration Tests
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class HealthCheckIT extends KarafTestSupport {

    private final static Logger LOGGER = LoggerFactory.getLogger(HealthCheckIT.class);

    protected static final String UNOMI_KEY = "670c26d1cc413346c3b2fd9ce65dab41";
    protected static final ContentType JSON_CONTENT_TYPE = ContentType.create("application/json");
    protected static final String BASE_URL = "http://localhost";
    protected static final String HEALTHCHECK_ENDPOINT = "/health/check";
    protected static final String BASIC_AUTH_USER_NAME = "health";
    protected static final String BASIC_AUTH_PASSWORD = "health";
    protected static final int REQUEST_TIMEOUT = 60000;
    protected static final int DEFAULT_TRYING_TIMEOUT = 2000;
    protected static final int DEFAULT_TRYING_TRIES = 30;

    protected final static ObjectMapper objectMapper;
    protected static boolean unomiStarted = false;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JaxbAnnotationModule());
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    protected PersistenceService persistenceService;
    protected DefinitionsService definitionsService;
    protected ProfileService profileService;
    protected EventService eventService;
    protected BundleWatcher bundleWatcher;

    @Inject
    @Filter(timeout = 600000)
    protected ConfigurationAdmin configurationAdmin;

    protected CloseableHttpClient httpClient;

    @Before
    public void before() throws InterruptedException {
        // disable retry
        retry = new Retry(false);

        // init httpClient
        httpClient = initHttpClient();
    }

    @After
    public void shutdown() {
        // Start Unomi if not already done
        if (unomiStarted) {
            executeCommand("unomi:stop");
            unomiStarted = true;
        }

        closeHttpClient(httpClient);
        httpClient = null;
    }

    @Override
    public MavenArtifactUrlReference getKarafDistribution() {
        return CoreOptions.maven().groupId("org.apache.unomi").artifactId("unomi").versionAsInProject().type("tar.gz");
    }

    @Configuration
    public Option[] config() {
        System.out.println("==== Configuring container");
        Option[] options = new Option[]{
                replaceConfigurationFile("etc/org.apache.unomi.router.cfg", new File("src/test/resources/org.apache.unomi.router.cfg")),

                editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.rootLogger.level", "INFO"),
                editConfigurationFilePut("etc/org.apache.karaf.features.cfg", "serviceRequirements", "disable"),
                editConfigurationFilePut("etc/system.properties", "my.system.property", System.getProperty("my.system.property")),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.graphql.feature.activated", "true"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.elasticsearch.cluster.name", "contextElasticSearchITests"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.elasticsearch.addresses", "localhost:9400"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.elasticsearch.taskWaitingPollingInterval", "50"),

                systemProperty("org.ops4j.pax.exam.rbc.rmi.port").value("1199"),
                systemProperty("org.apache.unomi.hazelcast.group.name").value("cellar"),
                systemProperty("org.apache.unomi.hazelcast.group.password").value("pass"),
                systemProperty("org.apache.unomi.hazelcast.network.port").value("5701"),
                systemProperty("org.apache.unomi.hazelcast.tcp-ip.members").value("127.0.0.1"),
                systemProperty("org.apache.unomi.hazelcast.tcp-ip.interface").value("127.0.0.1"),

                logLevel(LogLevel.INFO),
                keepRuntimeFolder(),
                CoreOptions.bundleStartLevel(100),
                CoreOptions.frameworkStartLevel(100)
        };
        List<Option> karafOptions = new ArrayList<>();
        karafOptions.addAll(Arrays.asList(options));

        String karafDebug = System.getProperty("it.karaf.debug");
        if (karafDebug != null) {
            System.out.println("Found system Karaf Debug system property, activating configuration: " + karafDebug);
            String port = "5006";
            boolean hold = true;
            if (karafDebug.trim().length() > 0) {
                String[] debugOptions = karafDebug.split(",");
                for (String debugOption : debugOptions) {
                    String[] debugOptionParts = debugOption.split(":");
                    if ("hold".equals(debugOptionParts[0])) {
                        hold = Boolean.parseBoolean(debugOptionParts[1].trim());
                    }
                    if ("port".equals(debugOptionParts[0])) {
                        port = debugOptionParts[1].trim();
                    }
                }
            }
            karafOptions.add(0, debugConfiguration(port, hold));
        }

        // Jacoco setup
        final String agentFile = System.getProperty("user.dir") + "/target/jacoco/lib/jacocoagent.jar";
        Path path = Paths.get(agentFile);
        if (Files.exists(path)) {
            final String jacocoOption = "-javaagent:" + agentFile + "=destfile=" + System.getProperty("user.dir")
                    + "/target/jacoco.exec,includes=org.apache.unomi.*";
            LOGGER.info("set jacoco java agent: {}", jacocoOption);
            karafOptions.add(new VMOption(jacocoOption));
        } else {
            LOGGER.warn("Unable to set jacoco agent as {} was not found", agentFile);
        }

        String customLogging = System.getProperty("it.karaf.customLogging");
        if (customLogging != null) {
            String[] customLoggingParts = customLogging.split(":");
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.customLogging.name", customLoggingParts[0]));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.customLogging.level", customLoggingParts[1]));
        }

        return Stream.of(super.config(), karafOptions.toArray(new Option[karafOptions.size()])).flatMap(Stream::of).toArray(Option[]::new);
    }

    public String getFullUrl(String url) throws Exception {
        return BASE_URL + ":" + getHttpPort() + url;
    }

    @Test
    public void testHealthCheck() {
        try {
            List<HealthCheckResponse> response = get(HEALTHCHECK_ENDPOINT, new TypeReference<>() {});
            LOGGER.info("Initial health check response: {}", response);
            Assert.assertEquals(5, response.size());
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("karaf") && r.getStatus() == HealthCheckResponse.Status.LIVE));
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("elasticsearch") && r.getStatus() == HealthCheckResponse.Status.LIVE));
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("unomi") && r.getStatus() == HealthCheckResponse.Status.DOWN));
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("cluster") && r.getStatus() == HealthCheckResponse.Status.DOWN));
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("persistence") && r.getStatus() == HealthCheckResponse.Status.DOWN));

            // Start Unomi if not already done
            if (!unomiStarted) {
                executeCommand("unomi:start");
                unomiStarted = true;
            }

            // Wait for startup complete
            bundleWatcher = getOsgiService(BundleWatcher.class, 600000);
            while (!bundleWatcher.isStartupComplete() || !bundleWatcher.allAdditionalBundleStarted()) {
                LOGGER.info("Waiting for startup to complete...");
                Thread.sleep(1000);
            }

            response = get(HEALTHCHECK_ENDPOINT, new TypeReference<>() {});
            LOGGER.info("Unomi started health check response: {}", response);
            Assert.assertEquals(5, response.size());
            Assert.assertTrue(response.stream().allMatch(r -> r.getStatus() == HealthCheckResponse.Status.LIVE));

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
            if (response.getStatusLine().getStatusCode() == 200) {
                return objectMapper.readValue(response.getEntity().getContent(), typeReference);
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    protected CloseableHttpResponse executeHttpRequest(HttpUriRequest request) throws IOException {
        System.out.println("Executing request " + request.getMethod() + " " + request.getURI() + "...");
        CloseableHttpResponse response = httpClient.execute(request);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            String content = null;
            if (response.getEntity() != null) {
                InputStream contentInputStream = response.getEntity().getContent();
                if (contentInputStream != null) {
                    content = IOUtils.toString(response.getEntity().getContent());
                }
            }
            LOGGER.error("Response status code: {}, reason: {}, content:{}", response.getStatusLine().getStatusCode(),
                    response.getStatusLine().getReasonPhrase(), content);
        }
        return response;
    }

    public static CloseableHttpClient initHttpClient() {
        long requestStartTime = System.currentTimeMillis();
        BasicCredentialsProvider credsProvider = null;
        credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(BASIC_AUTH_USER_NAME, BASIC_AUTH_PASSWORD));
        HttpClientBuilder httpClientBuilder = HttpClients.custom().useSystemProperties().setDefaultCredentialsProvider(credsProvider);

        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[] { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            } }, new SecureRandom());

            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER))
                    .build();

            PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager(
                    socketFactoryRegistry);
            poolingHttpClientConnectionManager.setMaxTotal(10);

            httpClientBuilder.setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
                    .setConnectionManager(poolingHttpClientConnectionManager);

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOGGER.error("Error creating SSL Context", e);
        }

        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(REQUEST_TIMEOUT).setSocketTimeout(REQUEST_TIMEOUT)
                .setConnectionRequestTimeout(REQUEST_TIMEOUT).build();
        httpClientBuilder.setDefaultRequestConfig(requestConfig);

        if (LOGGER.isDebugEnabled()) {
            long totalRequestTime = System.currentTimeMillis() - requestStartTime;
            LOGGER.debug("Init HttpClient executed in " + totalRequestTime + "ms");
        }

        return httpClientBuilder.build();
    }

    public static void closeHttpClient(CloseableHttpClient httpClient) {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (IOException e) {
            LOGGER.error("Could not close httpClient: " + httpClient, e);
        }
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
