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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
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
import org.apache.unomi.api.Item;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.api.services.SchemaRegistry;
import org.apache.unomi.lifecycle.BundleWatcher;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.container.internal.JavaVersionUtil;
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
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
public abstract class BaseIT {

    private final static Logger LOGGER = LoggerFactory.getLogger(BaseIT.class);

    protected static final String HTTP_PORT = "8181";
    protected static final String URL = "http://localhost:" + HTTP_PORT;
    protected static final String KARAF_DIR = "target/exam";
    protected static final String UNOMI_KEY = "670c26d1cc413346c3b2fd9ce65dab41";
    protected static final ContentType JSON_CONTENT_TYPE = ContentType.create("application/json");
    protected static final String BASE_URL = "http://localhost";
    protected static final String BASIC_AUTH_USER_NAME = "karaf";
    protected static final String BASIC_AUTH_PASSWORD = "karaf";
    protected static final int REQUEST_TIMEOUT = 60000;

    @Inject
    @Filter(timeout = 600000)
    protected PersistenceService persistenceService;

    @Inject
    @Filter(timeout = 600000)
    protected RulesService rulesService;

    @Inject
    @Filter(timeout = 600000)
    protected DefinitionsService definitionsService;

    @Inject
    protected BundleContext bundleContext;

    @Inject @Filter(timeout = 600000)
    protected BundleWatcher bundleWatcher;

    @Inject
    @Filter(timeout = 600000)
    protected ConfigurationAdmin configurationAdmin;

    @Inject
    @Filter(timeout = 600000)
    protected SchemaRegistry schemaRegistry;

    private CloseableHttpClient httpClient;

    @Before
    public void waitForStartup() throws InterruptedException {
        while (!bundleWatcher.isStartupComplete()) {
            LOGGER.info("Waiting for startup to complete...");
            Thread.sleep(1000);
        }
        httpClient = initHttpClient();
    }

    @After
    public void shutdown() {
        closeHttpClient(httpClient);
        httpClient = null;
    }


    protected void removeItems(final Class<? extends Item> ...classes) throws InterruptedException {
        Condition condition = new Condition(definitionsService.getConditionType("matchAllCondition"));
        for (Class<? extends Item> aClass : classes) {
            persistenceService.removeByQuery(condition, aClass);
        }
        refreshPersistence();
    }

    protected void recreateIndex(final String itemType) {
        if (persistenceService.removeIndex(itemType)) {
            persistenceService.createIndex(itemType);
        }
    }

    protected void refreshPersistence() throws InterruptedException {
        persistenceService.refresh();
        Thread.sleep(1000);
    }

    @Configuration
    public Option[] config() throws InterruptedException {

        MavenArtifactUrlReference karafUrl = maven()
                .groupId("org.apache.unomi")
                .artifactId("unomi")
                .type("tar.gz")
                .versionAsInProject();

        List<Option> options = new ArrayList<>();

        Option[] commonOptions = new Option[]{
                karafDistributionConfiguration()
                        .frameworkUrl(karafUrl)
                        .unpackDirectory(new File(KARAF_DIR))
                        .useDeployFolder(true),
                replaceConfigurationFile("etc/org.apache.unomi.router.cfg", new File(
                        "src/test/resources/org.apache.unomi.router.cfg")),
                replaceConfigurationFile("data/tmp/1-basic-test.csv", new File(
                        "src/test/resources/1-basic-test.csv")),
                replaceConfigurationFile("data/tmp/recurrent_import/2-surfers-test.csv", new File(
                        "src/test/resources/2-surfers-test.csv")),
                replaceConfigurationFile("data/tmp/recurrent_import/3-surfers-overwrite-test.csv", new File(
                        "src/test/resources/3-surfers-overwrite-test.csv")),
                replaceConfigurationFile("data/tmp/recurrent_import/4-surfers-delete-test.csv", new File(
                        "src/test/resources/4-surfers-delete-test.csv")),
                replaceConfigurationFile("data/tmp/recurrent_import/5-ranking-test.csv", new File(
                        "src/test/resources/5-ranking-test.csv")),
                replaceConfigurationFile("data/tmp/recurrent_import/6-actors-test.csv", new File(
                        "src/test/resources/6-actors-test.csv")),
                replaceConfigurationFile("data/tmp/testLogin.json", new File(
                        "src/test/resources/testLogin.json")),
                replaceConfigurationFile("data/tmp/testCopyProperties.json", new File(
                        "src/test/resources/testCopyProperties.json")),
                replaceConfigurationFile("data/tmp/testCopyPropertiesWithoutSystemTags.json", new File(
                        "src/test/resources/testCopyPropertiesWithoutSystemTags.json")),
                replaceConfigurationFile("data/tmp/testLoginEventCondition.json", new File(
                        "src/test/resources/testLoginEventCondition.json")),
                replaceConfigurationFile("data/tmp/testClickEventCondition.json", new File(
                        "src/test/resources/testClickEventCondition.json")),
                replaceConfigurationFile("data/tmp/testRuleGroovyAction.json", new File(
                        "src/test/resources/testRuleGroovyAction.json")),
                replaceConfigurationFile("data/tmp/groovy/UpdateAddressAction.groovy", new File(
                        "src/test/resources/groovy/UpdateAddressAction.groovy")),
                keepRuntimeFolder(),
                // configureConsole().ignoreLocalConsole(),
                logLevel(LogLevel.INFO),
                editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", "log4j2.rootLogger.level", "INFO"),
                editConfigurationFilePut("etc/org.apache.karaf.features.cfg", "serviceRequirements", "disable"),
//                editConfigurationFilePut("etc/org.ops4j.pax.web.cfg", "org.osgi.service.http.port", HTTP_PORT),
//                systemProperty("org.osgi.service.http.port").value(HTTP_PORT),
                systemProperty("org.ops4j.pax.exam.rbc.rmi.port").value("1199"),
                systemProperty("org.apache.unomi.itests.elasticsearch.transport.port").value("9500"),
                systemProperty("org.apache.unomi.itests.elasticsearch.cluster.name").value("contextElasticSearchITests"),
                systemProperty("org.apache.unomi.itests.elasticsearch.http.port").value("9400"),
                systemProperty("org.apache.unomi.itests.elasticsearch.bootstrap.seccomp").value("false"),
                systemProperty("org.apache.unomi.hazelcast.group.name").value("cellar"),
                systemProperty("org.apache.unomi.hazelcast.group.password").value("pass"),
                systemProperty("org.apache.unomi.hazelcast.network.port").value("5701"),
                systemProperty("org.apache.unomi.hazelcast.tcp-ip.members").value("127.0.0.1"),
                systemProperty("org.apache.unomi.hazelcast.tcp-ip.interface").value("127.0.0.1"),
                systemProperty("unomi.autoStart").value("true"),
                CoreOptions.bundleStartLevel(100),
                CoreOptions.frameworkStartLevel(100)
        };

        options.addAll(Arrays.asList(commonOptions));

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
            options.add(0, debugConfiguration(port, hold));
        }

        if (JavaVersionUtil.getMajorVersion() >= 9) {
            Option[] jdk9PlusOptions = new Option[]{
                    new VMOption("--add-reads=java.xml=java.logging"),
                    new VMOption("--add-exports=java.base/org.apache.karaf.specs.locator=java.xml,ALL-UNNAMED"),
                    new VMOption("--patch-module"),
                    new VMOption("java.base=lib/endorsed/org.apache.karaf.specs.locator-"
                            + System.getProperty("karaf.version") + ".jar"),
                    new VMOption("--patch-module"), new VMOption("java.xml=lib/endorsed/org.apache.karaf.specs.java.xml-"
                    + System.getProperty("karaf.version") + ".jar"),
                    new VMOption("--add-opens"),
                    new VMOption("java.base/java.security=ALL-UNNAMED"),
                    new VMOption("--add-opens"),
                    new VMOption("java.base/java.net=ALL-UNNAMED"),
                    new VMOption("--add-opens"),
                    new VMOption("java.base/java.lang=ALL-UNNAMED"),
                    new VMOption("--add-opens"),
                    new VMOption("java.base/java.util=ALL-UNNAMED"),
                    new VMOption("--add-opens"),
                    new VMOption("java.naming/javax.naming.spi=ALL-UNNAMED"),
                    new VMOption("--add-opens"),
                    new VMOption("java.rmi/sun.rmi.transport.tcp=ALL-UNNAMED"),
                    new VMOption("--add-exports=java.base/sun.net.www.protocol.http=ALL-UNNAMED"),
                    new VMOption("--add-exports=java.base/sun.net.www.protocol.https=ALL-UNNAMED"),
                    new VMOption("--add-exports=java.base/sun.net.www.protocol.jar=ALL-UNNAMED"),
                    new VMOption("--add-exports=jdk.naming.rmi/com.sun.jndi.url.rmi=ALL-UNNAMED"),
                    new VMOption("-classpath"),
                    new VMOption("lib/jdk9plus/*" + File.pathSeparator + "lib/boot/*")

            };
            options.addAll(Arrays.asList(jdk9PlusOptions));
        }

        return options.toArray(new Option[0]);
    }

    protected <T> T keepTrying(String failMessage, Supplier<T> call, Predicate<T> predicate, int timeout, int retries) throws InterruptedException {
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

    protected String bundleResourceAsString(final String resourcePath) throws IOException {
        final java.net.URL url = bundleContext.getBundle().getResource(resourcePath);
        if (url != null) {
            return IOUtils.toString(url);
        } else {
            return null;
        }
    }

    protected String getValidatedBundleJSON(final String resourcePath, Map<String,String> parameters) throws IOException {
        String jsonString = bundleResourceAsString(resourcePath);
        if (parameters != null && parameters.size() > 0) {
            for (Map.Entry<String,String> parameterEntry : parameters.entrySet()) {
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
        schemaRegistry = getService(SchemaRegistry.class);
    }

    public void updateConfiguration(String serviceName, String configPid, String propName, Object propValue) throws InterruptedException, IOException {
        org.osgi.service.cm.Configuration cfg = configurationAdmin.getConfiguration(configPid);
        Dictionary<String, Object> props = cfg.getProperties();
        props.put(propName, propValue);

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
            if ((e.getType() == ServiceEvent.UNREGISTERING || e.getType() == ServiceEvent.REGISTERED)
                    && ((String[])e.getServiceReference().getProperty("objectClass"))[0].equals(serviceName)) {
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
        refreshPersistence();
        keepTrying("Failed waiting for rule to be saved",
                () -> rulesService.getRule(rule.getMetadata().getId()),
                Objects::nonNull,
                3000,
                100);
        rulesService.refreshRules();
    }

    public String getFullUrl(String url) throws Exception {
        return BASE_URL + ":" + HTTP_PORT + url;
    }

    protected <T> T get(final String url, Class<T> clazz) {
        CloseableHttpResponse response = null;
        try {
            final HttpGet httpGet = new HttpGet(getFullUrl(url));
            response = executeHttpRequest(httpGet);
            if (response.getStatusLine().getStatusCode() == 200) {
                ObjectMapper objectMapper = new ObjectMapper();
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

    protected CloseableHttpResponse post(final String url, final String resource) {
        try {
            final HttpPost request = new HttpPost(getFullUrl(url));

            if (resource != null) {
                final String resourceAsString = resourceAsString(resource);
                request.setEntity(new StringEntity(resourceAsString, JSON_CONTENT_TYPE));
            }

            return executeHttpRequest(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    protected void delete(final String url) {
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
            LOGGER.error("Response status code: {}, reason: {}, content:{}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase(), content);
        }
        return response;
    }

    protected String resourceAsString(final String resource) {
        final java.net.URL url = bundleContext.getBundle().getResource(resource);
        try (InputStream stream = url.openStream()) {
            final ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(objectMapper.readTree(stream));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static CloseableHttpClient initHttpClient() {
        long requestStartTime = System.currentTimeMillis();
        BasicCredentialsProvider credsProvider = null;
        credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(BASIC_AUTH_USER_NAME, BASIC_AUTH_PASSWORD));
        HttpClientBuilder httpClientBuilder = HttpClients.custom().useSystemProperties().setDefaultCredentialsProvider(credsProvider);

        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs,
                                               String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs,
                                               String authType) {
                }
            }}, new SecureRandom());

            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER))
                    .build();

            PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            poolingHttpClientConnectionManager.setMaxTotal(10);

            httpClientBuilder.setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
                    .setConnectionManager(poolingHttpClientConnectionManager);

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOGGER.error("Error creating SSL Context", e);
        }

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(REQUEST_TIMEOUT)
                .setSocketTimeout(REQUEST_TIMEOUT)
                .setConnectionRequestTimeout(REQUEST_TIMEOUT)
                .build();
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

}
