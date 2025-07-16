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
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.HttpEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.karaf.itests.KarafTestSupport;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.api.services.*;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.utils.ConditionBuilder;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.apache.unomi.itests.tools.httpclient.HttpClientThatWaitsForUnomi;
import org.apache.unomi.lifecycle.BundleWatcher;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.IRouterCamelContext;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.apache.unomi.schema.api.SchemaService;
import org.apache.unomi.services.UserListService;
import org.apache.unomi.shell.services.UnomiManagementService;
import org.apache.unomi.rest.authentication.RestAuthenticationConfig;
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

import static org.ops4j.pax.exam.CoreOptions.maven;
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

    protected static final ContentType JSON_CONTENT_TYPE = ContentType.create("application/json");
    protected static final String BASE_URL = "http://localhost";
    protected static final String BASIC_AUTH_USER_NAME = "karaf";
    protected static final String BASIC_AUTH_PASSWORD = "karaf";
    protected static final int REQUEST_TIMEOUT = 60000;
    protected static final int DEFAULT_TRYING_TIMEOUT = 1000;
    protected static final int DEFAULT_TRYING_TRIES = 10;
    protected static final int DEFAULT_SHOULDBETRUE_TRIES = 5;

    protected static final String SEARCH_ENGINE_PROPERTY = "unomi.search.engine";
    protected static final String SEARCH_ENGINE_HTTPREQUEST_LOG_LEVEL = "unomi.search.engine.httprequest.log.level";
    protected static final String SEARCH_ENGINE_ELASTICSEARCH = "elasticsearch";
    protected static final String SEARCH_ENGINE_OPENSEARCH = "opensearch";

    protected final static ObjectMapper objectMapper;
    protected static boolean unomiStarted = false;
    protected static String searchEngine = SEARCH_ENGINE_ELASTICSEARCH;

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
    protected TenantService tenantService;
    protected SecurityService securityService;
    protected ExecutionContextManager executionContextManager;
    protected RestAuthenticationConfig restAuthenticationConfig;
    protected Tenant testTenant;
    protected ApiKey testPublicKey;
    protected ApiKey testPrivateKey;
    protected SchedulerService schedulerService;
    protected static final String TEST_TENANT_ID = "itTestTenant";

    @Inject
    protected BundleContext bundleContext;

    @Inject
    @Filter(timeout = 600000)
    protected ConfigurationAdmin configurationAdmin;

    protected CloseableHttpClient httpClient;

    public enum AuthType {
        NONE,           // No authentication
        PUBLIC_KEY,     // X-Unomi-Api-Key header with public key
        PRIVATE_KEY,    // Basic auth with tenant:private_key
        JAAS_ADMIN,     // Basic auth with karaf:karaf
        CUSTOM_BASIC,   // Basic auth with custom username and password
        AUTO            // Automatically determine based on endpoint type
    }

    @Before
    public void waitForStartup() throws InterruptedException {
        // disable retry
        retry = new KarafTestSupport.Retry(false);
        searchEngine = System.getProperty(SEARCH_ENGINE_PROPERTY, SEARCH_ENGINE_ELASTICSEARCH);

        // Start Unomi if not already done
        if (!unomiStarted) {
            // We must check that the Unomi Management Service is up and running before launching the
            // command otherwise the start configuration will not be properly populated.
            waitForUnomiManagementService();
            if (SEARCH_ENGINE_ELASTICSEARCH.equals(searchEngine)) {
                LOGGER.info("Starting Unomi with elasticsearch search engine...");
                System.out.println("==== Starting Unomi with elasticsearch search engine...");
                executeCommand("unomi:start");
            } else if (SEARCH_ENGINE_OPENSEARCH.equals(searchEngine)){
                LOGGER.info("Starting Unomi with opensearch search engine...");
                System.out.println("==== Starting Unomi with opensearch search engine...");
                executeCommand("unomi:start " + SEARCH_ENGINE_OPENSEARCH);
            } else {
                LOGGER.error("Unknown search engine: " + searchEngine);
                throw new InterruptedException("Unknown search engine: " + searchEngine);
            }
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
        tenantService = getOsgiService(TenantService.class, 600000);
        schedulerService = getOsgiService(SchedulerService.class, 600000);
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
        securityService = getOsgiService(SecurityService.class, 600000);
        executionContextManager = getOsgiService(ExecutionContextManager.class, 600000);
        restAuthenticationConfig = getOsgiService(RestAuthenticationConfig.class, 600000);

        // Create test tenant if not exists
        if (testTenant == null) {
            testTenant = tenantService.getTenant(TEST_TENANT_ID);
            if (testTenant == null) {
                testTenant = tenantService.createTenant(TEST_TENANT_ID, Collections.emptyMap());
            }
            // Get the API keys
            testPublicKey = tenantService.getApiKey(testTenant.getItemId(), ApiKey.ApiKeyType.PUBLIC);
            testPrivateKey = tenantService.getApiKey(testTenant.getItemId(), ApiKey.ApiKeyType.PRIVATE);

            // Make sure the tenant is available for querying.
            persistenceService.refresh();
        }

        securityService.setCurrentSubject(securityService.createSubject(TEST_TENANT_ID, true));

        executionContextManager.setCurrentContext(executionContextManager.createContext(testTenant.getItemId()));

        // Set up test tenant for HttpClientThatWaitsForUnomi
        HttpClientThatWaitsForUnomi.setTestTenant(testTenant, testPublicKey, testPrivateKey);

        // init httpClient without credentials provider - all auth handled via headers
        httpClient = initHttpClient(null);
    }

    private void waitForUnomiManagementService() throws InterruptedException {
        UnomiManagementService unomiManagementService = getOsgiService(UnomiManagementService.class, 600000);
        while (unomiManagementService == null) {
            LOGGER.info("Waiting for Unomi Management Service to be available...");
            Thread.sleep(1000);
            unomiManagementService = getOsgiService(UnomiManagementService.class, 600000);
        }
    }

    @After
    public void shutdown() {
        if (testTenant != null) {
            try {
                tenantService.deleteTenant(testTenant.getItemId());
                testTenant = null;
                testPublicKey = null;
                testPrivateKey = null;
            } catch (Exception e) {
                LOGGER.error("Error cleaning up test tenant", e);
            }
        }
        closeHttpClient(httpClient);
        httpClient = null;
    }

    protected void removeItems(final Class<? extends Item>... classes) throws InterruptedException {
        if (definitionsService == null) {
            throw new RuntimeException("definitionsService is null");
        }
        if (persistenceService == null) {
            throw new RuntimeException("persistenceService is null");
        }

        ConditionType matchAllConditionType = definitionsService.getConditionType("matchAllCondition");
        if (matchAllConditionType == null) {
            throw new RuntimeException("matchAllCondition type not found");
        }

        Condition condition = new Condition(matchAllConditionType);
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
        return maven().groupId("org.apache.unomi").artifactId("unomi").versionAsInProject().type("tar.gz");
    }

    @Configuration
    public Option[] config() {
        System.out.println("==== Configuring container");

        searchEngine = System.getProperty(SEARCH_ENGINE_PROPERTY, SEARCH_ENGINE_ELASTICSEARCH);
        System.out.println("Search Engine: " + searchEngine);

        // Define features option based on search engine
        Option featuresOption;
        if (SEARCH_ENGINE_ELASTICSEARCH.equals(searchEngine)) {
            featuresOption = features(maven().groupId("org.apache.unomi")
                    .artifactId("unomi-kar").versionAsInProject().type("xml").classifier("features"),
                    "unomi-persistence-elasticsearch", "unomi-services",
                    "unomi-router-karaf-feature", "unomi-groovy-actions",
                    "unomi-web-applications", "unomi-rest-ui", "unomi-healthcheck", "cdp-graphql-feature");
        } else if (SEARCH_ENGINE_OPENSEARCH.equals(searchEngine)) {
            featuresOption = features(maven().groupId("org.apache.unomi")
                    .artifactId("unomi-kar").versionAsInProject().type("xml").classifier("features"),
                    "unomi-persistence-opensearch", "unomi-services",
                    "unomi-router-karaf-feature", "unomi-groovy-actions",
                    "unomi-web-applications", "unomi-rest-ui", "unomi-healthcheck", "cdp-graphql-feature");
        } else {
            throw new IllegalArgumentException("Unknown search engine: " + searchEngine);
        }

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
                editConfigurationFilePut("etc/system.properties", SEARCH_ENGINE_PROPERTY, System.getProperty(SEARCH_ENGINE_PROPERTY, SEARCH_ENGINE_ELASTICSEARCH)),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.graphql.feature.activated", "true"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.elasticsearch.cluster.name", "contextElasticSearchITests"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.elasticsearch.addresses", "localhost:" + getSearchPort()),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.elasticsearch.taskWaitingPollingInterval", "50"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.cluster.name", "contextElasticSearchITests"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.addresses", "localhost:" + getSearchPort()),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.username", "admin"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.password", "Unomi.1ntegrat10n.Tests"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.sslEnable", "false"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.sslTrustAllCertificates", "true"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.minimalClusterState", "YELLOW"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.migration.tenant.id", TEST_TENANT_ID),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.elasticsearch.dumpRequestTypes", "removeByQuery"),

                systemProperty("org.ops4j.pax.exam.rbc.rmi.port").value("1199"),
                systemProperty("org.apache.unomi.hazelcast.group.name").value("cellar"),
                systemProperty("org.apache.unomi.hazelcast.group.password").value("pass"),
                systemProperty("org.apache.unomi.hazelcast.network.port").value("5701"),
                systemProperty("org.apache.unomi.hazelcast.tcp-ip.members").value("127.0.0.1"),
                systemProperty("org.apache.unomi.hazelcast.tcp-ip.interface").value("127.0.0.1"),
                systemProperty("org.apache.unomi.healthcheck.enabled").value("true"),

                featuresOption,  // Add the features option

                configureConsole().startRemoteShell(),
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

        searchEngine = System.getProperty(SEARCH_ENGINE_PROPERTY, SEARCH_ENGINE_ELASTICSEARCH);
        System.out.println("Search Engine: " + searchEngine);

        return Stream.of(super.config(), karafOptions.toArray(new Option[karafOptions.size()])).flatMap(Stream::of).toArray(Option[]::new);
    }

    /**
     * Repeatedly attempts to retrieve a value using the provided supplier and validates it with the predicate.
     * This method is particularly useful for testing asynchronous operations where we need to wait
     * for a specific condition to become true.
     *
     * @param <T>         The type of the value being returned by the supplier and checked by the predicate
     * @param failMessage The message to include in the AssertionError if the maximum number of retries is reached
     * @param call        A supplier function that returns the value to be tested
     * @param predicate   A predicate that tests the value and returns true if the condition is satisfied
     * @param timeout     The time in milliseconds to wait between retry attempts
     * @param retries     The maximum number of retry attempts before failing
     * @return The value that satisfied the predicate condition
     * @throws InterruptedException If the thread is interrupted while sleeping between retries
     * @throws AssertionError       If the maximum number of retries is reached without the predicate being satisfied
     */
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

    /**
     * Repeatedly checks if a value becomes null within a specific number of retries.
     * This is useful for testing operations that should result in the removal or
     * deregistration of elements.
     *
     * @param <T>         The type of value being checked
     * @param failMessage The message to include in the AssertionError if the value doesn't become null
     * @param call        A supplier function that returns the value to check for null
     * @param timeout     The time in milliseconds to wait between retry attempts
     * @param retries     The maximum number of retry attempts before failing
     * @throws InterruptedException If the thread is interrupted while sleeping between retries
     * @throws AssertionError       If the maximum number of retries is reached without the value becoming null
     */
    protected <T> void waitForNullValue(String failMessage, Supplier<T> call, int timeout, int retries) throws InterruptedException {
        int count = 0;
        while (call.get() != null) {
            if (count++ > retries) {
                Assert.fail(failMessage);
            }
            Thread.sleep(timeout);
        }
    }

    /**
     * Verifies that a condition remains true for the entire duration of the test period.
     * This is useful for testing stability of a state or ensuring that a condition doesn't
     * revert back to false after initially becoming true.
     *
     * @param <T>         The type of the value being checked
     * @param failMessage The message to include in the AssertionError if the condition becomes false
     * @param call        A supplier function that returns the value to be tested
     * @param predicate   A predicate that tests the value and should return true for the entire test period
     * @param timeout     The time in milliseconds to wait between validation attempts
     * @param retries     The number of times to check the condition (defines the total test period)
     * @return The final value after all checks have passed
     * @throws InterruptedException If the thread is interrupted while sleeping between checks
     * @throws AssertionError       If the condition becomes false at any point during the test period
     */
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

    /**
     * Retrieves the content of a resource file from the bundle as a string.
     *
     * @param resourcePath The path to the resource within the bundle
     * @return The resource content as a string, or null if the resource cannot be found
     * @throws IOException If an error occurs while reading the resource
     */
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

    /**
     * Retrieves and validates a JSON resource from the bundle, with optional parameter replacement.
     *
     * @param resourcePath The path to the JSON resource within the bundle
     * @param parameters   A map of parameters to replace in the JSON string (format: "###KEY###" -> "value")
     * @return The validated JSON string
     * @throws IOException If an error occurs while reading or validating the JSON
     */
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

    /**
     * Retrieves an OSGi service of the specified type, waiting if necessary until it becomes available.
     *
     * @param <T>          The type of service to retrieve
     * @param serviceClass The class object representing the service interface
     * @return The service instance
     * @throws InterruptedException If the thread is interrupted while waiting for the service
     */
    public <T> T getService(Class<T> serviceClass) throws InterruptedException {
        ServiceReference<T> serviceReference = bundleContext.getServiceReference(serviceClass);
        while (serviceReference == null) {
            LOGGER.info("Waiting for service {} to become available", serviceClass.getName());
            Thread.sleep(1000);
            serviceReference = bundleContext.getServiceReference(serviceClass);
        }
        return bundleContext.getService(serviceReference);
    }

    /**
     * Retrieves an OSGi service of the specified type with the given filter, waiting if necessary until it becomes available.
     *
     * @param <T>          The type of service to retrieve
     * @param serviceClass The class object representing the service interface
     * @param filter       The OSGi filter expression to match the service
     * @return The service instance
     * @throws InterruptedException If the thread is interrupted while waiting for the service
     */
    public <T> T getService(Class<T> serviceClass, String filter) throws InterruptedException {
        try {
            ServiceReference<T>[] serviceReferences = (ServiceReference<T>[]) bundleContext.getServiceReferences(serviceClass.getName(), filter);
            while (serviceReferences == null || serviceReferences.length == 0) {
                LOGGER.info("Waiting for service {} with filter {} to become available", serviceClass.getName(), filter);
                Thread.sleep(1000);
                serviceReferences = (ServiceReference<T>[]) bundleContext.getServiceReferences(serviceClass.getName(), filter);
            }
            return bundleContext.getService(serviceReferences[0]);
        } catch (Exception e) {
            LOGGER.error("Error getting service with filter", e);
            throw new RuntimeException("Error getting service with filter", e);
        }
    }

    /**
     * Updates the local service references by retrieving them again from the OSGi service registry.
     * This is typically needed after configuration changes that might cause service reregistration.
     * All services initialized in waitForStartup() are refreshed to ensure test consistency.
     *
     * @throws InterruptedException If the thread is interrupted while waiting for services
     */
    public void updateServices() throws InterruptedException {
        persistenceService = getService(PersistenceService.class);
        definitionsService = getService(DefinitionsService.class);
        schedulerService = getService(SchedulerService.class);
        rulesService = getService(RulesService.class);
        segmentService = getService(SegmentService.class);
        profileService = getService(ProfileService.class);
        privacyService = getService(PrivacyService.class);
        eventService = getService(EventService.class);
        bundleWatcher = getService(BundleWatcher.class);
        groovyActionsService = getService(GroovyActionsService.class);
        schemaService = getService(SchemaService.class);
        scopeService = getService(ScopeService.class);
        patchService = getService(PatchService.class);
        importConfigurationService = getService(ImportExportConfigurationService.class, "(configDiscriminator=IMPORT)");
        exportConfigurationService = getService(ImportExportConfigurationService.class, "(configDiscriminator=EXPORT)");
        routerCamelContext = getService(IRouterCamelContext.class);
        userListService = getService(UserListService.class);
        topicService = getService(TopicService.class);
        tenantService = getService(TenantService.class);
        securityService = getService(SecurityService.class);
        executionContextManager = getService(ExecutionContextManager.class);
        restAuthenticationConfig = getService(RestAuthenticationConfig.class);
    }

    /**
     * Updates an OSGi configuration with a single property value and waits for the service to be reregistered.
     *
     * @param serviceName The fully qualified name of the service to wait for
     * @param configPid   The persistent identifier of the configuration to update
     * @param propName    The name of the property to update
     * @param propValue   The new value for the property
     * @throws InterruptedException If the thread is interrupted while waiting for service reregistration
     * @throws IOException          If an error occurs while updating the configuration
     */
    public void updateConfiguration(String serviceName, String configPid, String propName, Object propValue)
            throws InterruptedException, IOException {
        Map<String, Object> props = new HashMap<>();
        props.put(propName, propValue);
        updateConfiguration(serviceName, configPid, props);
    }

    /**
     * Updates an OSGi configuration with multiple property values and waits for the service to be reregistered.
     * For persistence configurations, this method handles updates without causing bundle restarts.
     *
     * @param serviceName The fully qualified name of the service to wait for
     * @param configPid   The persistent identifier of the configuration to update
     * @param propsToSet  A map of property names to their new values
     * @throws InterruptedException If the thread is interrupted while waiting for service reregistration
     * @throws IOException          If an error occurs while updating the configuration
     */
    public void updateConfiguration(String serviceName, String configPid, Map<String, Object> propsToSet)
            throws InterruptedException, IOException {
        org.osgi.service.cm.Configuration cfg = configurationAdmin.getConfiguration(configPid);
        Dictionary<String, Object> props = cfg.getProperties();
        for (Map.Entry<String, Object> propToSet : propsToSet.entrySet()) {
            props.put(propToSet.getKey(), propToSet.getValue());
        }

        // For configurations that now handle changes without restarting, don't wait for service re-registration
        if (configPid.contains("persistence") || configPid.contains("org.apache.unomi.services")) {
            LOGGER.info("Updating configuration {} without waiting for service restart", configPid);
            cfg.update(props);
            // Give the configuration change handler time to process
            Thread.sleep(1000);
        } else {
            waitForReRegistration(serviceName, () -> {
                try {
                    cfg.update(props);
                } catch (IOException ignored) {
                }
            });
        }

        waitForStartup();

        // we must update our service objects now
        updateServices();
    }

    /**
     * Waits for a service to be unregistered and then reregistered after a configuration change.
     * This is useful when updating configurations that cause services to restart.
     *
     * @param serviceName The fully qualified name of the service to wait for
     * @param trigger     A runnable that will trigger the service reregistration (e.g., updating configuration)
     * @throws InterruptedException If the thread is interrupted while waiting for service events
     */
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

    /**
     * Converts an OSGi ServiceEvent type to a human-readable string representation.
     *
     * @param serviceEvent The ServiceEvent to convert
     * @return A string representation of the service event type
     */
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

    /**
     * Creates a rule and waits until it has been successfully saved in the system.
     *
     * @param rule The rule to create
     * @throws InterruptedException If the thread is interrupted while waiting for the rule to be saved
     */
    public void createAndWaitForRule(Rule rule) throws InterruptedException {
        rulesService.setRule(rule);
        Query query = new Query();
        ConditionBuilder builder = new ConditionBuilder(definitionsService);
        query.setCondition(builder.matchAll().build());
        query.setForceRefresh(true);
        query.setLimit(1000); // to avoid the default query limit of 10 entries
        keepTrying("Failed waiting for rule to be saved", () -> rulesService.getRuleMetadatas(query),
                (rules) -> rules.getList().stream().anyMatch(r -> r.getId().equals(rule.getMetadata().getId())), 1000,
                100);
        rulesService.refreshRules();
    }

    /**
     * Constructs a full URL by combining the base URL, port, and the provided path.
     *
     * @param url The URL path to append to the base URL and port
     * @return The complete URL string
     * @throws Exception If an error occurs while constructing the URL
     */
    public String getFullUrl(String url) throws Exception {
        return BASE_URL + ":" + getHttpPort() + url;
    }

    /**
     * Performs an HTTP GET request and deserializes the response to the specified class.
     *
     * @param <T>   The type to deserialize the response to
     * @param url   The URL path for the GET request
     * @param clazz The class object for the type to deserialize to
     * @return The deserialized response object, or null if the request failed
     */
    protected <T> T get(final String url, Class<T> clazz) {
        try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(getFullUrl(url)))) {
            if (response.getStatusLine().getStatusCode() == 200) {
                return objectMapper.readValue(response.getEntity().getContent(), clazz);
            } else {
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Error executing GET request to " + url, e);
        }
        return null;
    }

    /**
     * Performs an HTTP POST request with the specified resource as the request body.
     *
     * @param url        The URL path for the POST request
     * @param resource   The resource to use as the request body
     * @param contentType The content type of the request
     * @return The HTTP response, or null if the request failed
     */
    protected CloseableHttpResponse post(final String url, final String resource, ContentType contentType) {
        try {
            final HttpPost request = new HttpPost(getFullUrl(url));

            if (resource != null) {
                final String resourceAsString = resourceAsString(resource);
                request.setEntity(new StringEntity(resourceAsString, contentType));
            }

            return executeHttpRequest(request);
        } catch (Exception e) {
            LOGGER.error("Error executing POST request to " + url, e);
        }
        return null;
    }

    /**
     * Performs an HTTP POST request with the specified resource as the request body using JSON content type.
     *
     * @param url      The URL path for the POST request
     * @param resource The resource to use as the request body
     * @return The HTTP response, or null if the request failed
     */
    protected CloseableHttpResponse post(final String url, final String resource) {
        return post(url, resource, JSON_CONTENT_TYPE);
    }

    /**
     * Performs an HTTP DELETE request.
     *
     * @param url The URL path for the DELETE request
     * @return The HTTP response, or null if the request failed
     */
    protected CloseableHttpResponse delete(final String url) {
        try {
            return executeHttpRequest(new HttpDelete(getFullUrl(url)));
        } catch (IOException e) {
            LOGGER.error("Error executing DELETE request to " + url, e);
        } catch (Exception e) {
            LOGGER.error("Error executing DELETE request to " + url, e);
        }
        return null;
    }

    /**
     * Executes an HTTP request with automatic authentication detection.
     * This is the default method that automatically determines the required authentication.
     *
     * @param request The HTTP request to execute
     * @return The HTTP response
     * @throws IOException If an error occurs while executing the request
     */
    protected CloseableHttpResponse executeHttpRequest(HttpUriRequest request) throws IOException {
        return executeHttpRequest(request, AuthType.AUTO, null, null);
    }

    /**
     * Executes an HTTP request with the specified authentication type.
     *
     * @param request The HTTP request to execute
     * @param authType The authentication type to use
     * @return The HTTP response
     * @throws IOException If an error occurs while executing the request
     */
    protected CloseableHttpResponse executeHttpRequest(HttpUriRequest request, AuthType authType) throws IOException {
        return executeHttpRequest(request, authType, null, null);
    }

    /**
     * Loads a resource from the bundle and returns its content as a string.
     *
     * @param resource The path to the resource within the bundle
     * @return The resource content as a string
     * @throws RuntimeException If an error occurs while reading the resource
     */
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

    /**
     * Initializes an HTTP client with custom SSL settings and optional credentials provider.
     *
     * @param credentialsProvider The credentials provider for basic authentication (can be null)
     * @return The configured HTTP client
     */
    public static CloseableHttpClient initHttpClient(BasicCredentialsProvider credentialsProvider) {
        long requestStartTime = System.currentTimeMillis();
        HttpClientBuilder httpClientBuilder = HttpClients.custom().useSystemProperties();

        // Only set credentials provider if one is provided
        if (credentialsProvider != null) {
            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }

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
            poolingHttpClientConnectionManager.setMaxTotal(50);
            poolingHttpClientConnectionManager.setDefaultMaxPerRoute(20);
            poolingHttpClientConnectionManager.setValidateAfterInactivity(2000);

            httpClientBuilder.setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
                    .setConnectionManager(poolingHttpClientConnectionManager);

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOGGER.error("Error creating SSL Context", e);
        }

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(REQUEST_TIMEOUT)
                .setSocketTimeout(REQUEST_TIMEOUT)
                .setConnectionRequestTimeout(REQUEST_TIMEOUT) // timeout for getting connection from pool
                .build();
        httpClientBuilder.setDefaultRequestConfig(requestConfig);

        if (LOGGER.isDebugEnabled()) {
            long totalRequestTime = System.currentTimeMillis() - requestStartTime;
            LOGGER.debug("Init HttpClient executed in " + totalRequestTime + "ms");
        }

        return httpClientBuilder.build();
    }

    /**
     * Safely closes an HTTP client, handling any exceptions.
     *
     * @param httpClient The HTTP client to close
     */
    public static void closeHttpClient(CloseableHttpClient httpClient) {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (IOException e) {
            LOGGER.error("Could not close httpClient: " + httpClient, e);
        }
    }

    /**
     * Safely closes an HTTP response, handling any exceptions.
     *
     * @param response The HTTP response to close
     */
    public static void closeResponse(CloseableHttpResponse response) {
        try {
            if (response != null) {
                response.close();
            }
        } catch (IOException e) {
            LOGGER.error("Could not close response", e);
        }
    }

    /**
     * Gets the appropriate search engine port based on the configured search engine.
     *
     * @return The port number as a string
     */
    protected static String getSearchPort() {
        String searchEngine = System.getProperty(SEARCH_ENGINE_PROPERTY, SEARCH_ENGINE_ELASTICSEARCH);
        if (SEARCH_ENGINE_OPENSEARCH.equals(searchEngine)) {
            // For OpenSearch, get the port from the system property set by maven-failsafe-plugin
            return System.getProperty("org.apache.unomi.opensearch.addresses", "localhost:9401")
                    .split(":")[1]; // Extract port number from "localhost:9401"
        } else {
            // For Elasticsearch, use the default port or system property if set
            return System.getProperty("elasticsearch.port", "9400");
        }
    }

    /**
     * Executes an HTTP request with the specified authentication type.
     *
     * @param request The HTTP request to execute
     * @param authType The authentication type to use
     * @param userName The user name to use for the custom basic authentication type
     * @param password The password to use for the custom basic authentication type
     * @return The HTTP response
     * @throws IOException If an error occurs while executing the request
     */
    protected CloseableHttpResponse executeHttpRequest(HttpUriRequest request, AuthType authType, String userName, String password) throws IOException {
        // Apply authentication based on type
        switch (authType) {
            case NONE:
                // No authentication headers - explicitly remove any existing auth headers
                request.removeHeaders("Authorization");
                request.removeHeaders("X-Unomi-Api-Key");
                break;
            case PUBLIC_KEY:
                // Remove any existing auth headers first
                request.removeHeaders("Authorization");
                // Only set X-Unomi-Api-Key header if it's not already set
                if (request.getFirstHeader("X-Unomi-Api-Key") == null && testPublicKey != null) {
                    request.setHeader("X-Unomi-Api-Key", testPublicKey.getKey());
                }
                break;
            case PRIVATE_KEY:
                // Remove any existing auth headers first
                request.removeHeaders("X-Unomi-Api-Key");
                // Only set Authorization header if it's not already set
                if (request.getFirstHeader("Authorization") == null && testPrivateKey != null) {
                    String credentials = TEST_TENANT_ID + ":" + testPrivateKey.getKey();
                    request.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()));
                }
                break;
            case JAAS_ADMIN:
                // Remove any existing auth headers first
                request.removeHeaders("X-Unomi-Api-Key");
                // Only set Authorization header if it's not already set
                if (request.getFirstHeader("Authorization") == null) {
                    String credentials = BASIC_AUTH_USER_NAME + ":" + BASIC_AUTH_PASSWORD;
                    request.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()));
                }
                break;
            case CUSTOM_BASIC:
                // Remove any existing auth headers first
                request.removeHeaders("X-Unomi-Api-Key");
                // Only set Authorization header if it's not already set
                if (request.getFirstHeader("Authorization") == null) {
                    String credentials = userName + ":" + password;
                    request.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()));
                }
                break;
            case AUTO:
                // Auto-detect based on an endpoint type
                String path = request.getURI().getPath();
                String method = request.getMethod();

                // Normalize the path for pattern matching - remove /cxs prefix if present and leading slash
                // This matches the behavior of ContainerRequestContext.getUriInfo().getPath()
                String normalizedPath = path.startsWith("/cxs/") ? path.substring(4) : path;
                // Remove leading slash to match ContainerRequestContext.getUriInfo().getPath() behavior
                if (normalizedPath.startsWith("/")) {
                    normalizedPath = normalizedPath.substring(1);
                }
                String methodPath = method + " " + normalizedPath;

                // Check if it's a public endpoint
                boolean isPublic = restAuthenticationConfig.getPublicPathPatterns().stream()
                        .anyMatch(pattern -> pattern.matcher(methodPath).matches());

                if (isPublic) {
                    // Public endpoint - use public key
                    request.removeHeaders("Authorization");
                    // Only set X-Unomi-Api-Key header if it's not already set
                    if (request.getFirstHeader("X-Unomi-Api-Key") == null && testPublicKey != null) {
                        request.setHeader("X-Unomi-Api-Key", testPublicKey.getKey());
                    }
                } else if (normalizedPath.startsWith("/tenants")) {
                    // Admin endpoint - use JAAS admin
                    request.removeHeaders("X-Unomi-Api-Key");
                    // Only set Authorization header if it's not already set
                    if (request.getFirstHeader("Authorization") == null) {
                        String adminCredentials = BASIC_AUTH_USER_NAME + ":" + BASIC_AUTH_PASSWORD;
                        request.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(adminCredentials.getBytes()));
                    }
                } else {
                    // Private endpoint - use private key
                    request.removeHeaders("X-Unomi-Api-Key");
                    // Only set Authorization header if it's not already set
                    if (request.getFirstHeader("Authorization") == null && testPrivateKey != null) {
                        String privateCredentials = TEST_TENANT_ID + ":" + testPrivateKey.getKey();
                        request.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(privateCredentials.getBytes()));
                    }
                }
                break;
        }

        // Execute the request
        CloseableHttpResponse response = httpClient.execute(request);

        // Log errors
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            String content = null;
            if (response.getEntity() != null) {
                // Use BufferedHttpEntity to allow multiple reads of the entity content
                HttpEntity bufferedEntity = new BufferedHttpEntity(response.getEntity());
                response.setEntity(bufferedEntity);
                content = IOUtils.toString(bufferedEntity.getContent(), "UTF-8");
            }
            LOGGER.error("Response status code: {}, reason: {}, content:{}", response.getStatusLine().getStatusCode(),
                    response.getStatusLine().getReasonPhrase(), content);
        }

        return response;
    }
}
