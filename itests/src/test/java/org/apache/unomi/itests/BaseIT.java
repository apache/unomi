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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.apache.commons.io.IOUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.karaf.itests.KarafTestSupport;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.*;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.apache.unomi.lifecycle.BundleWatcher;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.IRouterCamelContext;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.apache.unomi.schema.api.SchemaService;
import org.apache.unomi.services.UserListService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
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
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;

/**
 * Base class for integration tests.
 *
 * @author kevan
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public abstract class BaseIT extends KarafTestSupport {

    private final static Logger LOGGER = LoggerFactory.getLogger(BaseIT.class);

    protected static final String UNOMI_KEY = "670c26d1cc413346c3b2fd9ce65dab41";
    protected static final ContentType JSON_CONTENT_TYPE = ContentType.create("application/json");
    protected static final String BASE_URL = "http://localhost";
    protected static final String BASIC_AUTH_USER_NAME = "karaf";
    protected static final String BASIC_AUTH_PASSWORD = "karaf";
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
    protected RulesService rulesService;
    protected DefinitionsService definitionsService;
    protected ProfileService profileService;
    protected PrivacyService privacyService;
    protected EventService eventService;
    protected BundleWatcher bundleWatcher;
    protected GroovyActionsService groovyActionsService;
    protected SegmentService segmentService;
    protected SchemaService schemaService;
    protected ScopeService scopeService;
    protected PatchService patchService;
    protected ImportExportConfigurationService<ImportConfiguration> importConfigurationService;
    protected ImportExportConfigurationService<ExportConfiguration> exportConfigurationService;
    protected IRouterCamelContext routerCamelContext;
    protected UserListService userListService;
    protected TopicService topicService;

    @Inject
    protected BundleContext bundleContext;

    @Inject
    @Filter(timeout = 600000)
    protected ConfigurationAdmin configurationAdmin;

    protected CloseableHttpClient httpClient;

    @Before
    public void waitForStartup() throws InterruptedException {
        // disable retry
        retry = new KarafTestSupport.Retry(false);

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

        // init unomi services that are available once unomi:start have been called
        persistenceService = getOsgiService(PersistenceService.class, 600000);
        rulesService = getOsgiService(RulesService.class, 600000);
        definitionsService = getOsgiService(DefinitionsService.class, 600000);
        profileService = getOsgiService(ProfileService.class, 600000);
        privacyService = getOsgiService(PrivacyService.class, 600000);
        eventService = getOsgiService(EventService.class, 600000);
        groovyActionsService = getOsgiService(GroovyActionsService.class, 600000);
        segmentService = getOsgiService(SegmentService.class, 600000);
        schemaService = getOsgiService(SchemaService.class, 600000);
        scopeService = getOsgiService(ScopeService.class, 600000);
        patchService = getOsgiService(PatchService.class, 600000);
        userListService = getOsgiService(UserListService.class, 600000);
        topicService = getOsgiService(TopicService.class, 600000);
        importConfigurationService = getOsgiService(ImportExportConfigurationService.class, "(configDiscriminator=IMPORT)", 600000);
        exportConfigurationService = getOsgiService(ImportExportConfigurationService.class, "(configDiscriminator=EXPORT)", 600000);
        routerCamelContext = getOsgiService(IRouterCamelContext.class, 600000);

        // init httpClient
        httpClient = initHttpClient(getHttpClientCredentialProvider());
    }

    @After
    public void shutdown() {
        closeHttpClient(httpClient);
        httpClient = null;
    }

    protected void removeItems(final Class<? extends Item>... classes) throws InterruptedException {
        Condition condition = new Condition(definitionsService.getConditionType("matchAllCondition"));
        for (Class<? extends Item> aClass : classes) {
            persistenceService.removeByQuery(condition, aClass);
        }
        refreshPersistence(classes);
    }

    protected void refreshPersistence(final Class<? extends Item>... classes) throws InterruptedException {
        for (Class<? extends Item> aClass : classes) {
            persistenceService.refreshIndex(aClass);
        }
        Thread.sleep(1000);
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

                replaceConfigurationFile("data/tmp/1-basic-test.csv", new File("src/test/resources/1-basic-test.csv")),
                replaceConfigurationFile("data/tmp/recurrent_import/2-surfers-test.csv", new File("src/test/resources/2-surfers-test.csv")),
                replaceConfigurationFile("data/tmp/recurrent_import/3-surfers-overwrite-test.csv", new File("src/test/resources/3-surfers-overwrite-test.csv")),
                replaceConfigurationFile("data/tmp/recurrent_import/4-surfers-delete-test.csv", new File("src/test/resources/4-surfers-delete-test.csv")),
                replaceConfigurationFile("data/tmp/recurrent_import/5-ranking-test.csv", new File("src/test/resources/5-ranking-test.csv")),
                replaceConfigurationFile("data/tmp/recurrent_import/6-actors-test.csv", new File("src/test/resources/6-actors-test.csv")),
                replaceConfigurationFile("data/tmp/testLogin.json", new File("src/test/resources/testLogin.json")),
                replaceConfigurationFile("data/tmp/testCopyProperties.json", new File("src/test/resources/testCopyProperties.json")),
                replaceConfigurationFile("data/tmp/testCopyPropertiesWithoutSystemTags.json", new File("src/test/resources/testCopyPropertiesWithoutSystemTags.json")),
                replaceConfigurationFile("data/tmp/testLoginEventCondition.json", new File("src/test/resources/testLoginEventCondition.json")),
                replaceConfigurationFile("data/tmp/testClickEventCondition.json", new File("src/test/resources/testClickEventCondition.json")),
                replaceConfigurationFile("data/tmp/testRuleGroovyAction.json", new File("src/test/resources/testRuleGroovyAction.json")),
                replaceConfigurationFile("data/tmp/groovy/UpdateAddressAction.groovy", new File("src/test/resources/groovy/UpdateAddressAction.groovy")),

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

    protected <T> T keepTrying(String failMessage, Supplier<T> call, Predicate<T> predicate, int timeout, int retries)
            throws InterruptedException {
        int count = 0;
        T value = null;
        while (value == null || !predicate.test(value)) {
            if (count++ > retries) {
                Assert.fail(failMessage);
            }
            Thread.sleep(timeout);
            value = call.get();
        }
        return value;
    }

    protected <T> void waitForNullValue(String failMessage, Supplier<T> call, int timeout, int retries) throws InterruptedException {
        int count = 0;
        while (call.get() != null) {
            if (count++ > retries) {
                Assert.fail(failMessage);
            }
            Thread.sleep(timeout);
        }
    }

    protected <T> T shouldBeTrueUntilEnd(String failMessage, Supplier<T> call, Predicate<T> predicate, int timeout, int retries)
            throws InterruptedException {
        int count = 0;
        T value = null;
        while (count <= retries) {
            count++;
            value = call.get();
            if (!predicate.test(value)) {
                Assert.fail(failMessage);
            }
            Thread.sleep(timeout);
        }
        return value;
    }

    protected String bundleResourceAsString(final String resourcePath) throws IOException {
        final java.net.URL url = bundleContext.getBundle().getResource(resourcePath);
        if (url != null) {
            try (InputStream stream = url.openStream()) {
                return IOUtils.toString(stream);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }

    protected String getValidatedBundleJSON(final String resourcePath, Map<String, String> parameters) throws IOException {
        String jsonString = bundleResourceAsString(resourcePath);
        if (parameters != null && parameters.size() > 0) {
            for (Map.Entry<String, String> parameterEntry : parameters.entrySet()) {
                jsonString = jsonString.replace("###" + parameterEntry.getKey() + "###", parameterEntry.getValue());
            }
        }
        ObjectMapper objectMapper = CustomObjectMapper.getObjectMapper();
        return objectMapper.writeValueAsString(objectMapper.readTree(jsonString));
    }

    public void updateServices() throws InterruptedException {
        persistenceService = getService(PersistenceService.class);
        definitionsService = getService(DefinitionsService.class);
        rulesService = getService(RulesService.class);
        segmentService = getService(SegmentService.class);
    }

    public void updateConfiguration(String serviceName, String configPid, String propName, Object propValue)
            throws InterruptedException, IOException {
        Map<String, Object> props = new HashMap<>();
        props.put(propName, propValue);
        updateConfiguration(serviceName, configPid, props);
    }

    public void updateConfiguration(String serviceName, String configPid, Map<String, Object> propsToSet)
            throws InterruptedException, IOException {
        org.osgi.service.cm.Configuration cfg = configurationAdmin.getConfiguration(configPid);
        Dictionary<String, Object> props = cfg.getProperties();
        for (Map.Entry<String, Object> propToSet : propsToSet.entrySet()) {
            props.put(propToSet.getKey(), propToSet.getValue());
        }

        waitForReRegistration(serviceName, () -> {
            try {
                cfg.update(props);
            } catch (IOException ignored) {
            }
        });

        waitForStartup();

        // we must update our service objects now
        updateServices();
    }

    public void waitForReRegistration(String serviceName, Runnable trigger) throws InterruptedException {
        CountDownLatch latch1 = new CountDownLatch(2);
        ServiceListener serviceListener = e -> {
            LOGGER.info("Service {} {}", e.getServiceReference().getProperty("objectClass"), serviceEventTypeToString(e));
            if ((e.getType() == ServiceEvent.UNREGISTERING || e.getType() == ServiceEvent.REGISTERED) && ((String[]) e.getServiceReference()
                    .getProperty("objectClass"))[0].equals(serviceName)) {
                latch1.countDown();
            }
        };
        bundleContext.addServiceListener(serviceListener);
        trigger.run();
        latch1.await();
        bundleContext.removeServiceListener(serviceListener);
    }

    public String serviceEventTypeToString(ServiceEvent serviceEvent) {
        switch (serviceEvent.getType()) {
            case ServiceEvent.MODIFIED:
                return "modified";
            case ServiceEvent.REGISTERED:
                return "registered";
            case ServiceEvent.UNREGISTERING:
                return "unregistering";
            case ServiceEvent.MODIFIED_ENDMATCH:
                return "modified endmatch";
            default:
                return "unknown type " + serviceEvent.getType();
        }
    }

    public <T> T getService(Class<T> serviceClass) throws InterruptedException {
        ServiceReference<T> serviceReference = bundleContext.getServiceReference(serviceClass);
        while (serviceReference == null) {
            LOGGER.info("Waiting for service {} to become available", serviceClass.getName());
            Thread.sleep(1000);
            serviceReference = bundleContext.getServiceReference(serviceClass);
        }
        return bundleContext.getService(serviceReference);
    }

    public void createAndWaitForRule(Rule rule) throws InterruptedException {
        rulesService.setRule(rule);
        keepTrying("Failed waiting for rule to be saved", () -> rulesService.getAllRules(),
                (rules) -> rules.stream().anyMatch(r -> r.getItemId().equals(rule.getMetadata().getId())), 1000,
                100);
        rulesService.refreshRules();
    }

    public String getFullUrl(String url) throws Exception {
        return BASE_URL + ":" + getHttpPort() + url;
    }

    protected <T> T get(final String url, Class<T> clazz) {
        CloseableHttpResponse response = null;
        try {
            final HttpGet httpGet = new HttpGet(getFullUrl(url));
            response = executeHttpRequest(httpGet);
            if (response.getStatusLine().getStatusCode() == 200) {
                return objectMapper.readValue(response.getEntity().getContent(), clazz);
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

    protected CloseableHttpResponse post(final String url, final String resource, ContentType contentType) {
        try {
            final HttpPost request = new HttpPost(getFullUrl(url));

            if (resource != null) {
                final String resourceAsString = resourceAsString(resource);
                request.setEntity(new StringEntity(resourceAsString, contentType));
            }

            return executeHttpRequest(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    protected CloseableHttpResponse post(final String url, final String resource) {
        return post(url, resource, JSON_CONTENT_TYPE);
    }

    protected CloseableHttpResponse delete(final String url) {
        CloseableHttpResponse response = null;
        try {
            final HttpDelete httpDelete = new HttpDelete(getFullUrl(url));
            response = executeHttpRequest(httpDelete);
        } catch (IOException e) {
            e.printStackTrace();
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
        return response;
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

    protected String resourceAsString(final String resource) {
        final java.net.URL url = bundleContext.getBundle().getResource(resource);
        try (InputStream stream = url.openStream()) {
            JsonNode node = objectMapper.readTree(stream);
            String value = objectMapper.writeValueAsString(node);
            return value;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static CloseableHttpClient initHttpClient(BasicCredentialsProvider credentialsProvider) {
        long requestStartTime = System.currentTimeMillis();
        HttpClientBuilder httpClientBuilder = HttpClients.custom().useSystemProperties().setDefaultCredentialsProvider(credentialsProvider);

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

    public BasicCredentialsProvider getHttpClientCredentialProvider() {
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(BASIC_AUTH_USER_NAME, BASIC_AUTH_PASSWORD));
        return credsProvider;
    }
}
