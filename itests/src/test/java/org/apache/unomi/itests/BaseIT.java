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
import org.apache.unomi.api.services.*;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.utils.ConditionBuilder;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.apache.unomi.itests.tools.httpclient.HttpClientThatWaitsForUnomi;
import org.apache.unomi.itests.tools.LogChecker;
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
import org.ops4j.pax.exam.ConfigurationManager;
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
import java.util.Hashtable;
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
    protected static final String RESOLVER_DEBUG_PROPERTY = "it.unomi.resolver.debug";
    protected static final String ENABLE_LOG_CHECKING_PROPERTY = "it.unomi.log.checking.enabled";

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
    protected LogChecker logChecker;
    private String currentTestName;

    public enum AuthType {
        NONE,           // No authentication
        PUBLIC_KEY,     // X-Unomi-Api-Key header with public key
        PRIVATE_KEY,    // Basic auth with tenant:private_key
        JAAS_ADMIN,     // Basic auth with karaf:karaf
        CUSTOM_BASIC,   // Basic auth with custom username and password
        AUTO            // Automatically determine based on endpoint type
    }

    /**
     * Checks the search engine configuration from system properties.
     * This method should be called early, before any test setup, to ensure
     * the correct search engine is detected and any necessary fixes are applied.
     */
    protected void checkSearchEngine() {
        searchEngine = System.getProperty(SEARCH_ENGINE_PROPERTY, SEARCH_ENGINE_ELASTICSEARCH);
        System.out.println("Check search engine: " + searchEngine);
        
        // Fix elasticsearch-maven-plugin default_template issue before any test setup
        // The plugin creates a default_template with very high priority that overrides all user templates
        // This must be done very early, before Unomi starts or any migration runs
        if (SEARCH_ENGINE_ELASTICSEARCH.equals(searchEngine)) {
            fixDefaultTemplateIfNeeded();
        }
    }

    @Before
    public void waitForStartup() throws InterruptedException {
        // disable retry
        retry = new KarafTestSupport.Retry(false);
        
        // Check search engine and apply any necessary fixes (e.g., default_template deletion)
        checkSearchEngine();

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
        
        // Initialize log checker if enabled
        if (isLogCheckingEnabled()) {
            logChecker = new LogChecker();
            LOGGER.info("Log checking enabled using in-memory appender");
        }
    }
    
    /**
     * Mark log checkpoint before each test
     * This method is called automatically by JUnit before each test method
     */
    @Before
    public void markLogCheckpoint() {
        if (logChecker != null) {
            logChecker.markCheckpoint();
            // Get current test name from stack trace
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stack) {
                String methodName = element.getMethodName();
                if (methodName.startsWith("test") || methodName.startsWith("check")) {
                    currentTestName = element.getClassName() + "." + methodName;
                    break;
                }
            }
            if (currentTestName == null) {
                currentTestName = "unknown";
            }
            LOGGER.debug("Marked log checkpoint for test: {}", currentTestName);
        }
    }

    private void waitForUnomiManagementService() throws InterruptedException {
        final int maxRetries = 5;
        int retryCount = 0;
        UnomiManagementService unomiManagementService = getOsgiService(UnomiManagementService.class, 600000);

        while (unomiManagementService == null && retryCount < maxRetries) {
            LOGGER.info("Waiting for Unomi Management Service to be available... (attempt {}/{})", retryCount + 1, maxRetries);
            Thread.sleep(1000);
            retryCount++;
            unomiManagementService = getOsgiService(UnomiManagementService.class, 600000);
        }

        if (unomiManagementService == null) {
            String errorMsg = String.format("Unomi Management Service was not available after %d retries.", maxRetries);
            LOGGER.error(errorMsg);
            throw new InterruptedException(errorMsg);
        }
    }

    @After
    public void shutdown() {
        // Check logs for unexpected errors/warnings before cleanup
        checkLogsForUnexpectedIssues();
        
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

    
    /**
     * Check logs for unexpected errors and warnings since the last checkpoint
     * This is called automatically after each test
     */
    protected void checkLogsForUnexpectedIssues() {
        if (logChecker == null) {
            return;
        }
        
        try {
            LogChecker.LogCheckResult result = logChecker.checkLogsSinceLastCheckpoint();
            
            if (result.hasUnexpectedIssues()) {
                String summary = result.getSummary();
                String testInfo = currentTestName != null ? "Test: " + currentTestName + "\n" : "";
                
                // Log to console and logger
                System.err.println("\n=== UNEXPECTED LOG ISSUES DETECTED ===");
                System.err.println(testInfo + summary);
                System.err.println("=======================================\n");
                
                LOGGER.warn("Unexpected log issues detected in test {}:\n{}", currentTestName, summary);
                
                // Add to JUnit test output by printing to System.out (captured by JUnit)
                System.out.println("\n=== SERVER-SIDE LOG ISSUES ===");
                System.out.println(testInfo + summary);
                System.out.println("===============================\n");
            }
        } catch (Exception e) {
            LOGGER.error("Error checking logs", e);
        }
    }
    
    /**
     * Check if log checking is enabled
     * Can be controlled via system property: it.unomi.log.checking.enabled
     * Defaults to true
     */
    protected boolean isLogCheckingEnabled() {
        String enabled = System.getProperty(ENABLE_LOG_CHECKING_PROPERTY, "true");
        return Boolean.parseBoolean(enabled);
    }
    
    /**
     * Add a pattern to ignore for log checking
     * Useful for tests that expect certain errors/warnings
     * @param pattern Regex pattern to match against log messages
     */
    protected void addIgnoredLogPattern(String pattern) {
        if (logChecker != null) {
            logChecker.addIgnoredPattern(pattern);
        }
    }
    
    /**
     * Add multiple patterns to ignore for log checking
     * @param patterns List of regex patterns
     */
    protected void addIgnoredLogPatterns(List<String> patterns) {
        if (logChecker != null) {
            logChecker.addIgnoredPatterns(patterns);
        }
    }

    protected String karafData() {
        ConfigurationManager cm = new ConfigurationManager();
        return cm.getProperty("karaf.data");
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
        LOGGER.info("==== Configuring container");
        System.out.println("==== Configuring container");

        searchEngine = System.getProperty(SEARCH_ENGINE_PROPERTY, SEARCH_ENGINE_ELASTICSEARCH);
        LOGGER.info("Search Engine: {}", searchEngine);
        System.out.println("Search Engine: " + searchEngine);

        // Define features option based on search engine
        Option featuresOption;
        if (SEARCH_ENGINE_ELASTICSEARCH.equals(searchEngine)) {
            featuresOption = features(
                    maven().groupId("org.apache.unomi").artifactId("unomi-kar").versionAsInProject().type("xml").classifier("features"),
                    "unomi-base",
                    "unomi-startup",
                    "unomi-elasticsearch-core",
                    "unomi-persistence-core",
                    "unomi-services",
                    "unomi-cxs-privacy-extension-services",
                    "unomi-plugins-base",
                    "unomi-plugins-request",
                    "unomi-plugins-mail",
                    "unomi-plugins-optimization-test",
                    "unomi-rest-api",
                    "unomi-cxs-privacy-extension",
                    "unomi-elasticsearch-conditions",
                    "unomi-plugins-advanced-conditions",
                    "unomi-cxs-lists-extension",
                    "unomi-cxs-geonames-extension",
                    "unomi-shell-dev-commands",
                    "unomi-wab",
                    "unomi-web-tracker",
                    "unomi-healthcheck",
                    "unomi-router-karaf-feature",
                    "unomi-groovy-actions",
                    "unomi-rest-ui",
                    "unomi-startup-complete"
            );
        } else if (SEARCH_ENGINE_OPENSEARCH.equals(searchEngine)) {
            featuresOption = features(
                    maven().groupId("org.apache.unomi").artifactId("unomi-kar").versionAsInProject().type("xml").classifier("features"),
                    "unomi-base",
                    "unomi-startup",
                    "unomi-opensearch-core",
                    "unomi-persistence-core",
                    "unomi-services",
                    "unomi-cxs-privacy-extension-services",
                    "unomi-plugins-base",
                    "unomi-plugins-request",
                    "unomi-plugins-mail",
                    "unomi-plugins-optimization-test",
                    "unomi-rest-api",
                    "unomi-cxs-privacy-extension",
                    "unomi-opensearch-conditions",
                    "unomi-plugins-advanced-conditions",
                    "unomi-cxs-lists-extension",
                    "unomi-cxs-geonames-extension",
                    "unomi-shell-dev-commands",
                    "unomi-wab",
                    "unomi-web-tracker",
                    "unomi-healthcheck",
                    "unomi-router-karaf-feature",
                    "unomi-groovy-actions",
                    "unomi-rest-ui",
                    "unomi-startup-complete"
            );
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
                replaceConfigurationFile("data/tmp/conditions/testIdsConditionLegacy.json", new File("src/test/resources/conditions/testIdsConditionLegacy.json")),
                replaceConfigurationFile("data/tmp/conditions/testIdsConditionNew.json", new File("src/test/resources/conditions/testIdsConditionNew.json")),
                replaceConfigurationFile("data/tmp/conditions/testBooleanConditionLegacy.json", new File("src/test/resources/conditions/testBooleanConditionLegacy.json")),
                replaceConfigurationFile("data/tmp/conditions/testPropertyConditionLegacy.json", new File("src/test/resources/conditions/testPropertyConditionLegacy.json")),
                replaceConfigurationFile("data/tmp/groovy/UpdateAddressAction.groovy", new File("src/test/resources/groovy/UpdateAddressAction.groovy")),

                editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.rootLogger.level", "INFO"),
                editConfigurationFilePut("etc/org.apache.karaf.features.cfg", "serviceRequirements", "disable"),
                editConfigurationFilePut("etc/system.properties", "my.system.property", System.getProperty("my.system.property")),
                editConfigurationFilePut("etc/system.properties", SEARCH_ENGINE_PROPERTY, System.getProperty(SEARCH_ENGINE_PROPERTY, SEARCH_ENGINE_ELASTICSEARCH)),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.graphql.feature.activated", "true"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.elasticsearch.cluster.name", "contextElasticSearchITests"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.elasticsearch.addresses", "localhost:" + getSearchPort()),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.elasticsearch.taskWaitingPollingInterval", "50"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.elasticsearch.rollover.maxDocs", "300"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.cluster.name", "contextElasticSearchITests"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.addresses", "localhost:" + getSearchPort()),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.username", "admin"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.password", "Unomi.1ntegrat10n.Tests"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.sslEnable", "false"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.sslTrustAllCertificates", "true"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.minimalClusterState", "YELLOW"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.migration.tenant.id", TEST_TENANT_ID),

                systemProperty("org.ops4j.pax.exam.rbc.rmi.port").value("1199"),
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
            LOGGER.info("Found system Karaf Debug system property, activating configuration: {}", karafDebug);
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

        // Enable debug logging for Karaf Resolver to diagnose bundle refresh issues (default: disabled)
        boolean enableResolverDebug = Boolean.parseBoolean(System.getProperty(RESOLVER_DEBUG_PROPERTY, "false"));
        if (enableResolverDebug) {
            LOGGER.info("Enabling debug logging for Karaf Resolver and Karaf features service");
            System.out.println("Enabling debug logging for Karaf Resolver and Karaf features service");
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.osgiResolver.name", "org.osgi.service.resolver"));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.osgiResolver.level", "DEBUG"));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.karafFeatures.name", "org.apache.karaf.features"));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.karafFeatures.level", "DEBUG"));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.karafResolver.name", "org.apache.karaf.resolver"));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.karafResolver.level", "DEBUG"));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.osgiFramework.name", "org.osgi.framework"));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.osgiFramework.level", "DEBUG"));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.osgiPackageAdmin.name", "org.osgi.service.packageadmin"));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.osgiPackageAdmin.level", "DEBUG"));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.karafDeployer.name", "org.apache.karaf.features.core"));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.logger.karafDeployer.level", "DEBUG"));
        } else {
            LOGGER.info("Karaf Resolver debug logging is disabled (set -Dit.unomi.resolver.debug=true to enable)");
            System.out.println("Karaf Resolver debug logging is disabled (set -Dit.unomi.resolver.debug=true to enable)");
        }

        searchEngine = System.getProperty(SEARCH_ENGINE_PROPERTY, SEARCH_ENGINE_ELASTICSEARCH);
        LOGGER.info("Search Engine: {}", searchEngine);
        System.out.println("Search Engine: " + searchEngine);

        // Configure in-memory log appender for log checking
        // The InMemoryLogAppender is part of the log4j-extension fragment bundle,
        // which is already included as a startup bundle. It attaches to the Pax Logging
        // Log4j2 bundle early in the startup process, ensuring the appender is discoverable.
        // We only configure it for integration tests, not for the default package.
        if (isLogCheckingEnabled()) {
            LOGGER.info("Configuring in-memory log appender for log checking");
            // Configure the appender in Log4j2
            // The appender is already available via the log4j-extension fragment bundle
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", 
                "log4j2.appender.inMemory.type", "InMemoryLogAppender"));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", 
                "log4j2.appender.inMemory.name", "InMemoryLogAppender"));
            karafOptions.add(editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", 
                "log4j2.rootLogger.appenderRef.inMemory.ref", "InMemoryLogAppender"));
        }

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
     * Updates an OSGi configuration with a single property value and optionally waits for the service to be reregistered.
     * If serviceName is null, the method will not wait for service re-registration.
     *
     * @param serviceName The fully qualified name of the service to wait for, or null to skip waiting
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
     * Updates an OSGi configuration with multiple property values and optionally waits for the service to be reregistered.
     * If serviceName is null, the method will not wait for service re-registration.
     *
     * @param serviceName The fully qualified name of the service to wait for, or null to skip waiting
     * @param configPid   The persistent identifier of the configuration to update
     * @param propsToSet  A map of property names to their new values
     * @throws InterruptedException If the thread is interrupted while waiting for service reregistration
     * @throws IOException          If an error occurs while updating the configuration
     */
    public void updateConfiguration(String serviceName, String configPid, Map<String, Object> propsToSet)
            throws InterruptedException, IOException {
        // Use getConfiguration(pid, null) to create an unbound configuration
        // This ensures the configuration is accessible to all bundles, not just the test bundle
        org.osgi.service.cm.Configuration cfg = configurationAdmin.getConfiguration(configPid, null);
        Dictionary<String, Object> props = cfg.getProperties();

        // Handle case where properties haven't been initialized yet
        final Dictionary<String, Object> finalProps = (props != null) ? props : new Hashtable<>();

        // Add new properties to the dictionary
        for (Map.Entry<String, Object> propToSet : propsToSet.entrySet()) {
            finalProps.put(propToSet.getKey(), propToSet.getValue());
        }

        // If serviceName is null, don't wait for service re-registration
        if (serviceName == null) {
            LOGGER.info("Updating configuration {} without waiting for service restart", configPid);
            LOGGER.debug("Configuration properties being set: {}", finalProps);
            cfg.update(finalProps);
            // Verify the update was applied
            Dictionary<String, Object> updatedProps = cfg.getProperties();
            LOGGER.debug("Configuration properties after update: {}", updatedProps);
            // Give the configuration change handler time to process
            Thread.sleep(1000);
        } else {
            waitForReRegistration(serviceName, () -> {
                try {
                    cfg.update(finalProps);
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
            LOGGER.error("Error while getting url "+url, e);
            e.printStackTrace();
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    LOGGER.error("Error while getting url "+url, e);
                    e.printStackTrace();
                }
            }
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
        CloseableHttpResponse response = null;
        try {
            final HttpDelete httpDelete = new HttpDelete(getFullUrl(url));
            response = executeHttpRequest(httpDelete);
        } catch (IOException e) {
            LOGGER.error("Error executing DELETE request to " + url, e);
            e.printStackTrace();
        } catch (Exception e) {
            LOGGER.error("Error executing DELETE request to " + url, e);
            e.printStackTrace();
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    LOGGER.error("Error executing DELETE request to " + url, e);
                    e.printStackTrace();
                }
            }
        }
        return response;
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

    /**
     * Fixes the default_template created by elasticsearch-maven-plugin that overrides all user templates.
     * The plugin creates a template with index_patterns: ["*"] and very high priority (2147483520),
     * which in ES 8/9 overrides all other templates since composable templates don't merge.
     * This method detects and deletes the template if it has a very high priority.
     * This must be called before Unomi starts since the ES persistence service isn't available yet.
     */
    private void fixDefaultTemplateIfNeeded() {
        String templateName = "default_template";
        String esBaseUrl = "http://localhost:" + getSearchPort();
        String templateUrl = esBaseUrl + "/_index_template/" + templateName;
        
        CloseableHttpClient tempHttpClient = null;
        try {
            // Create a temporary HTTP client for ES requests
            tempHttpClient = initHttpClient(null);
            
            // Check if default_template exists using HEAD request
            HttpHead headRequest = new HttpHead(templateUrl);
            CloseableHttpResponse headResponse = null;
            try {
                headResponse = tempHttpClient.execute(headRequest);
                int statusCode = headResponse.getStatusLine().getStatusCode();
                
                if (statusCode == 404) {
                    // Template doesn't exist, nothing to fix
                    LOGGER.debug("default_template does not exist, no action needed");
                    return;
                } else if (statusCode != 200) {
                    // Unexpected status, log and continue
                    LOGGER.warn("Unexpected status code {} when checking for default_template, skipping fix", statusCode);
                    return;
                }
            } finally {
                if (headResponse != null) {
                    headResponse.close();
                }
            }
            
            // Template exists, get its details to check priority
            HttpGet getRequest = new HttpGet(templateUrl);
            CloseableHttpResponse getResponse = null;
            try {
                getResponse = tempHttpClient.execute(getRequest);
                int statusCode = getResponse.getStatusLine().getStatusCode();
                
                if (statusCode == 200) {
                    String responseBody = IOUtils.toString(getResponse.getEntity().getContent(), "UTF-8");
                    JsonNode jsonNode = objectMapper.readTree(responseBody);
                    
                    // Parse the template response
                    // ES API returns: {"index_templates": [{"name": "default_template", "index_template": {...}}]}
                    if (jsonNode.has("index_templates") && jsonNode.get("index_templates").isArray()) {
                        JsonNode templates = jsonNode.get("index_templates");
                        for (JsonNode template : templates) {
                            if (template.has("name") && templateName.equals(template.get("name").asText())) {
                                JsonNode indexTemplate = template.get("index_template");
                                if (indexTemplate != null && indexTemplate.has("priority")) {
                                    Long priority = indexTemplate.get("priority").asLong();
                                    
                                    // Check if priority is very high (>= 2147480000, near Integer.MAX_VALUE)
                                    // This indicates it's the problematic template from elasticsearch-maven-plugin
                                    if (priority >= 2147480000L) {
                                        LOGGER.warn("Detected default_template with very high priority ({}). " +
                                                "This template from elasticsearch-maven-plugin overrides all user templates in ES 8/9. " +
                                                "Deleting it to allow user templates to work correctly.", priority);
                                        
                                        // Delete the template
                                        HttpDelete deleteRequest = new HttpDelete(templateUrl);
                                        CloseableHttpResponse deleteResponse = null;
                                        try {
                                            deleteResponse = tempHttpClient.execute(deleteRequest);
                                            int deleteStatusCode = deleteResponse.getStatusLine().getStatusCode();
                                            
                                            if (deleteStatusCode == 200) {
                                                // Parse delete response to check acknowledged
                                                String deleteResponseBody = IOUtils.toString(deleteResponse.getEntity().getContent(), "UTF-8");
                                                JsonNode deleteJsonNode = objectMapper.readTree(deleteResponseBody);
                                                boolean acknowledged = deleteJsonNode.has("acknowledged") && 
                                                                       deleteJsonNode.get("acknowledged").asBoolean();
                                                
                                                if (acknowledged) {
                                                    LOGGER.info("Successfully deleted default_template. User templates will now work correctly.");
                                                } else {
                                                    LOGGER.warn("Failed to delete default_template - not acknowledged. User templates may not work correctly.");
                                                }
                                            } else {
                                                LOGGER.warn("Failed to delete default_template - status code: {}. User templates may not work correctly.", deleteStatusCode);
                                            }
                                        } finally {
                                            if (deleteResponse != null) {
                                                deleteResponse.close();
                                            }
                                        }
                                    } else {
                                        LOGGER.debug("default_template exists but has normal priority ({}), no action needed.", priority);
                                    }
                                    break; // Found the template, no need to continue
                                }
                            }
                        }
                    }
                } else {
                    LOGGER.warn("Failed to get default_template details - status code: {}, skipping fix", statusCode);
                }
            } finally {
                if (getResponse != null) {
                    getResponse.close();
                }
            }
        } catch (Exception e) {
            // Log but don't fail startup - this is a best-effort fix for integration tests
            LOGGER.warn("Failed to check/fix default_template: {}. This may affect template application in integration tests.", 
                    e.getMessage(), e);
        } finally {
            if (tempHttpClient != null) {
                closeHttpClient(tempHttpClient);
            }
        }
    }
}
