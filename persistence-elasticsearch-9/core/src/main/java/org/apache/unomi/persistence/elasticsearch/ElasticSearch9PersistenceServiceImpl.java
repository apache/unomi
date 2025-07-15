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

package org.apache.unomi.persistence.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchVersionInfo;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.analysis.Analyzer;
import co.elastic.clients.elasticsearch._types.analysis.CustomAnalyzer;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.elasticsearch.ilm.*;
import co.elastic.clients.elasticsearch.indices.put_index_template.IndexTemplateMapping;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.BaseAggregate;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("rawtypes")
public class ElasticSearch9PersistenceServiceImpl implements PersistenceService, SynchronousBundleListener {
    private String logLevelRestClient = "ERROR";

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearch9PersistenceServiceImpl.class.getName());

    private String username;
    private String password;
    private List<String> itemsMonthlyIndexed;

    private String elasticSearchAddresses;

    private List<String> rolloverIndices;


    private String rolloverIndexNumberOfShards;
    private String monthlyIndexNumberOfShards;
    private String rolloverIndexNumberOfReplicas;

    private String monthlyIndexNumberOfReplicas;
    private String rolloverIndexMappingTotalFieldsLimit;
    private String monthlyIndexMappingTotalFieldsLimit;
    private String rolloverIndexMaxDocValueFieldsSearch;

    private String monthlyIndexMaxDocValueFieldsSearch;
    private BundleContext bundleContext;

    private static final String ROLLOVER_LIFECYCLE_NAME = "unomi-rollover-policy";

    private List<String> elasticSearchAddressList = new ArrayList<>();
    private Map<String, String> mappings = new HashMap<>();

    private ElasticsearchClient esClient;
    private String indexPrefix;

    private static final Map<String, String> itemTypeIndexNameMap = new HashMap<>();
    private static final Collection<String> systemItems = Arrays.asList("actionType", "campaign", "campaignevent", "goal",
            "userList", "propertyType", "scope", "conditionType", "rule", "scoring", "segment", "groovyAction", "topic",
            "patch", "jsonSchema", "importConfig", "exportConfig", "rulestats");

    static {
        for (String systemItem : systemItems) {
            itemTypeIndexNameMap.put(systemItem, "systemItems");
        }

        itemTypeIndexNameMap.put("profile", "profile");
        itemTypeIndexNameMap.put("persona", "profile");
    }


    private boolean sslTrustAllCertificates = false;

    public void setIndexPrefix(String indexPrefix) {
        this.indexPrefix = indexPrefix;
    }

    public void setElasticSearchAddresses(String elasticSearchAddresses) {
        this.elasticSearchAddresses = elasticSearchAddresses;
        String[] elasticSearchAddressesArray = elasticSearchAddresses.split(",");
        elasticSearchAddressList.clear();
        for (String elasticSearchAddress : elasticSearchAddressesArray) {
            elasticSearchAddressList.add(elasticSearchAddress.trim());
        }
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setSslTrustAllCertificates(boolean sslTrustAllCertificates) {
        this.sslTrustAllCertificates = sslTrustAllCertificates;
    }

    public void setLogLevelRestClient(String logLevelRestClient) {
        this.logLevelRestClient = logLevelRestClient;
    }

    public void setRolloverIndexMappingTotalFieldsLimit(String rolloverIndexMappingTotalFieldsLimit) {
        this.rolloverIndexMappingTotalFieldsLimit = rolloverIndexMappingTotalFieldsLimit;
    }

    public void setRolloverIndexMaxDocValueFieldsSearch(String rolloverIndexMaxDocValueFieldsSearch) {
        this.rolloverIndexMaxDocValueFieldsSearch = rolloverIndexMaxDocValueFieldsSearch;
    }

    @Deprecated
    public void setMonthlyIndexMappingTotalFieldsLimit(String monthlyIndexMappingTotalFieldsLimit) {
        this.monthlyIndexMappingTotalFieldsLimit = monthlyIndexMappingTotalFieldsLimit;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
    @Deprecated
    public void setMonthlyIndexMaxDocValueFieldsSearch(String monthlyIndexMaxDocValueFieldsSearch) {
        this.monthlyIndexMaxDocValueFieldsSearch = monthlyIndexMaxDocValueFieldsSearch;
    }

    public void start() throws Exception {

        // Work around to avoid ES Logs regarding the deprecated [ignore_throttled] parameter
        try {
            Level lvl = Level.toLevel(logLevelRestClient, Level.ERROR);
            //org.apache.log4j.Logger.getLogger("org.elasticsearch.client.RestClient").setLevel(lvl);
        } catch (Exception e) {
            // Never fail because of the set of the logger
        }

        buildClient();

        ElasticsearchVersionInfo versionInfo = esClient.info().version();

        LOGGER.info("Connected to ElasticSearch version: {}", versionInfo.number());

        registerRolloverLifecyclePolicy();

        loadPredefinedMappings(bundleContext, false);

        // on startup
        /*new InClassLoaderExecute<Object>(null, null, this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            public Object execute(Object... args) throws Exception {

                buildClient();

                ElasticsearchVersionInfo versionInfo = esClient.info().version();

                LOGGER.info("Connected to ElasticSearch version: {}", versionInfo.number());

                registerRolloverLifecyclePolicy();

                loadPredefinedMappings(bundleContext, false);
                loadPainlessScripts(bundleContext);

                // load predefined mappings and condition dispatchers of any bundles that were started before this one.
                for (Bundle existingBundle : bundleContext.getBundles()) {
                    if (existingBundle.getBundleContext() != null) {
                        loadPredefinedMappings(existingBundle.getBundleContext(), false);
                        loadPainlessScripts(existingBundle.getBundleContext());
                    }
                }

                if (client != null && bulkProcessor == null) {
                    bulkProcessor = getBulkProcessor();
                }

                // Wait for green
                LOGGER.info("Waiting for GREEN cluster status...");
                client.cluster().health(new ClusterHealthRequest().waitForGreenStatus(), RequestOptions.DEFAULT);
                LOGGER.info("Cluster status is GREEN");

                // We keep in memory the latest available session index to be able to load session using direct GET access on ES
                if (isItemTypeRollingOver(Session.ITEM_TYPE)) {
                    LOGGER.info("Sessions are using rollover indices, loading latest session index available ...");
                    GetAliasesResponse sessionAliasResponse = client.indices().getAlias(new GetAliasesRequest(getIndex(Session.ITEM_TYPE)), RequestOptions.DEFAULT);
                    Map<String, Set<AliasMetaData>> aliases = sessionAliasResponse.getAliases();
                    if (!aliases.isEmpty()) {
                        sessionLatestIndex = new TreeSet<>(aliases.keySet()).last();
                        LOGGER.info("Latest available session index found is: {}", sessionLatestIndex);
                    } else {
                        throw new IllegalStateException("No index found for sessions");
                    }
                }

                return true;
            }
        }.executeInClassLoader();
        */
        bundleContext.addBundleListener(this);

        LOGGER.info("{} service started successfully.", this.getClass().getName());
    }

    public void setRolloverIndexNumberOfReplicas(String rolloverIndexNumberOfReplicas) {
        this.rolloverIndexNumberOfReplicas = rolloverIndexNumberOfReplicas;
    }

    public void setMonthlyIndexNumberOfReplicas(String monthlyIndexNumberOfReplicas) {
        this.monthlyIndexNumberOfReplicas = monthlyIndexNumberOfReplicas;
    }

    private void loadPredefinedMappings(BundleContext bundleContext, boolean forceUpdateMapping) {
        Enumeration<URL> predefinedMappings = bundleContext.getBundle().findEntries("META-INF/cxs/mappings", "*.json", true);
        if (predefinedMappings == null) {
            return;
        }
        while (predefinedMappings.hasMoreElements()) {
            URL predefinedMappingURL = predefinedMappings.nextElement();
            LOGGER.info("Found mapping at {}, loading... ", predefinedMappingURL);
            try {
                final String path = predefinedMappingURL.getPath();
                String name = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                String mappingSource = loadMappingFile(predefinedMappingURL);

                mappings.put(name, mappingSource);

                if (!createIndex(name)) {
                    LOGGER.info("Found index for type {}", name);
                    if (forceUpdateMapping) {
                        LOGGER.info("Updating mapping for {}", name);
                        createMapping(name, mappingSource);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error while loading mapping definition {}", predefinedMappingURL, e);
            }
        }
    }

    private String loadMappingFile(URL predefinedMappingURL) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(predefinedMappingURL.openStream()));

        StringBuilder content = new StringBuilder();
        String l;
        while ((l = reader.readLine()) != null) {
            content.append(l);
        }
        return content.toString();
    }

    public boolean registerRolloverLifecyclePolicy() {
        RolloverAction rolloverAction = new RolloverAction.Builder()
                .maxAge(new Time.Builder().time("7d").build())
                .maxSize("50gb")
                .build();

        Phase hotPhase = new Phase.Builder()
                .actions(new Actions.Builder().rollover(rolloverAction).build())
                .minAge(new Time.Builder().time("0ms").build())
                .build();

        IlmPolicy ilmPolicy = new IlmPolicy.Builder()
                .phases(new Phases.Builder().hot(hotPhase).build())
                .build();
        PutLifecycleRequest request = new PutLifecycleRequest.Builder()
                .policy(ilmPolicy)
                .name(indexPrefix + "-" + ROLLOVER_LIFECYCLE_NAME)
                .build();

        try {
            PutLifecycleResponse response = esClient.ilm().putLifecycle(request);
            return response.acknowledged();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private void buildClient() throws NoSuchFieldException, IllegalAccessException {

        List<URI> uris = elasticSearchAddressList.stream().map(address -> URI.create(address.trim())).collect(Collectors.toList());

        final SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("SSL");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        if (sslTrustAllCertificates) {
            try {
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
            } catch (KeyManagementException e) {
                LOGGER.error("Error creating SSL Context for trust all certificates", e);
            }
        }

        ElasticsearchClient esClient = ElasticsearchClient.of(b -> b
                .hosts(uris)
                .usernameAndPassword(username, password)
                .sslContext(sslTrustAllCertificates ? sslContext : null)
        );

        LOGGER.info("Connecting to ElasticSearch persistence backend using index prefix {}...", indexPrefix);
    }

    public void stop() {
        try {
            esClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T extends Item> List<T> getAllItems(Class<T> clazz) {
        return null;
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(Class<T> clazz, int offset, int size, String sortBy) {
        return null;
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(Class<T> clazz, int offset, int size, String sortBy, String scrollTimeValidity) {
        return null;
    }

    @Override
    public boolean isConsistent(Item item) {
        return false;
    }

    @Override
    public boolean save(Item item) {
        return false;
    }

    @Override
    public boolean save(Item item, boolean useBatching) {
        return false;
    }

    @Override
    public boolean save(Item item, Boolean useBatching, Boolean alwaysOverwrite) {
        return false;
    }

    @Override
    public boolean update(Item item, Date dateHint, Class<?> clazz, Map<?, ?> source) {
        return false;
    }

    @Override
    public boolean update(Item item, Date dateHint, Class<?> clazz, String propertyName, Object propertyValue) {
        return false;
    }

    @Override
    public boolean update(Item item, Date dateHint, Class<?> clazz, Map<?, ?> source, boolean alwaysOverwrite) {
        return false;
    }

    @Override
    public List<String> update(Map<Item, Map> items, Date dateHint, Class clazz) {
        return null;
    }

    @Override
    public boolean updateWithScript(Item item, Date dateHint, Class<?> clazz, String script, Map<String, Object> scriptParams) {
        return false;
    }

    @Override
    public boolean updateWithQueryAndScript(Date dateHint, Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions) {
        return false;
    }

    @Override
    public boolean updateWithQueryAndStoredScript(Class<?>[] classes, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions, boolean waitForComplete) {
        return false;
    }

    @Override
    public boolean updateWithQueryAndStoredScript(Date dateHint, Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions) {
        return false;
    }

    @Override
    public boolean storeScripts(Map<String, String> scripts) {
        return false;
    }

    @Override
    public <T extends Item> T load(String itemId, Class<T> clazz) {
        return null;
    }

    @Override
    public <T extends Item> T load(String itemId, Date dateHint, Class<T> clazz) {
        return null;
    }

    @Override
    public CustomItem loadCustomItem(String itemId, Date dateHint, String customItemType) {
        return null;
    }

    @Override
    public <T extends Item> boolean remove(String itemId, Class<T> clazz) {
        return false;
    }

    @Override
    public boolean removeCustomItem(String itemId, String customItemType) {
        return false;
    }

    @Override
    public <T extends Item> boolean removeByQuery(Condition query, Class<T> clazz) {
        return false;
    }

    @Override
    public boolean saveQuery(String queryName, Condition query) {
        return false;
    }

    @Override
    public boolean removeQuery(String queryName) {
        return false;
    }

    @Override
    public Map<String, Map<String, Object>> getPropertiesMapping(String itemType) {
        return null;
    }

    @Override
    public Map<String, Object> getPropertyMapping(String property, String itemType) {
        return null;
    }

    @Override
    public void setPropertyMapping(PropertyType property, String itemType) {

    }

    @Override
    public void createMapping(String type, String source) {

    }

    @Override
    public boolean testMatch(Condition query, Item item) {
        return false;
    }

    @Override
    public boolean isValidCondition(Condition condition, Item item) {
        return false;
    }

    @Override
    public <T extends Item> List<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz) {
        return null;
    }

    @Override
    public <T extends Item> List<T> query(String fieldName, String[] fieldValues, String sortBy, Class<T> clazz) {
        return null;
    }

    @Override
    public <T extends Item> PartialList<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz, int offset, int size) {
        return null;
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fieldName, String fieldValue, String fulltext, String sortBy, Class<T> clazz, int offset, int size) {
        return null;
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fulltext, String sortBy, Class<T> clazz, int offset, int size) {
        return null;
    }

    @Override
    public <T extends Item> List<T> query(Condition query, String sortBy, Class<T> clazz) {
        return null;
    }

    @Override
    public <T extends Item> PartialList<T> query(Condition query, String sortBy, Class<T> clazz, int offset, int size) {
        return null;
    }

    @Override
    public <T extends Item> PartialList<T> query(Condition query, String sortBy, Class<T> clazz, int offset, int size, String scrollTimeValidity) {
        return null;
    }

    @Override
    public <T extends Item> PartialList<T> continueScrollQuery(Class<T> clazz, String scrollIdentifier, String scrollTimeValidity) {
        return null;
    }

    @Override
    public PartialList<CustomItem> queryCustomItem(Condition query, String sortBy, String customItemType, int offset, int size, String scrollTimeValidity) {
        return null;
    }

    @Override
    public PartialList<CustomItem> continueCustomItemScrollQuery(String customItemType, String scrollIdentifier, String scrollTimeValidity) {
        return null;
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fulltext, Condition query, String sortBy, Class<T> clazz, int offset, int size) {
        return null;
    }

    @Override
    public long queryCount(Condition query, String itemType) {
        return 0;
    }

    @Override
    public long getAllItemsCount(String itemType) {
        return 0;
    }

    @Override
    public Map<String, Long> aggregateQuery(Condition filter, BaseAggregate aggregate, String itemType) {
        return null;
    }

    @Override
    public Map<String, Long> aggregateWithOptimizedQuery(Condition filter, BaseAggregate aggregate, String itemType) {
        return null;
    }

    @Override
    public Map<String, Long> aggregateWithOptimizedQuery(Condition filter, BaseAggregate aggregate, String itemType, int size) {
        return null;
    }

    @Override
    public void refresh() {

    }

    @Override
    public <T extends Item> void refreshIndex(Class<T> clazz, Date dateHint) {

    }

    @Override
    public void purge(Date date) {

    }

    @Override
    public <T extends Item> void purgeTimeBasedItems(int existsNumberOfDays, Class<T> clazz) {

    }

    @Override
    public <T extends Item> PartialList<T> rangeQuery(String s, String from, String to, String sortBy, Class<T> clazz, int offset, int size) {
        return null;
    }

    @Override
    public Map<String, Double> getSingleValuesMetrics(Condition condition, String[] metrics, String field, String type) {
        return null;
    }


    private String getIndexNameForItemType(String itemType) {
        return itemTypeIndexNameMap.getOrDefault(itemType, itemType);
    }

    private String getIndex(String itemType) {
        return (indexPrefix + "-" + getIndexNameForItemType(itemType)).toLowerCase();
    }

    @Override
    public boolean createIndex(String itemType) {
        LOGGER.debug("Create index {}", itemType);
        String index = getIndex(itemType);

        boolean exists;
        try {
            exists = esClient.indices().exists(new ExistsRequest.Builder()
                    .index(index)
                    .build()).value();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!exists) {
            if (isItemTypeRollingOver(itemType)) {
                try {
                    internalCreateRolloverTemplate(itemType);
                    internalCreateRolloverIndex(index);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
               // internalCreateIndex(index, mappings.get(itemType));
            }
        }
        return !exists;
    }


    private void internalCreateRolloverIndex(String indexName) throws IOException {
        CreateIndexResponse response = esClient.indices()
                .create(new CreateIndexRequest.Builder()
                        .index(indexName + "-000001")
                        .aliases(Map.of(indexName, new Alias.Builder().isWriteIndex(true)
                                .build()))
                        .build());
        LOGGER.info("Index created: [{}], acknowledge: [{}], shards acknowledge: [{}]", response.index(),
                response.acknowledged(), response.shardsAcknowledged());
    }

    @Deprecated
    public void setMonthlyIndexNumberOfShards(String monthlyIndexNumberOfShards) {
        this.monthlyIndexNumberOfShards = monthlyIndexNumberOfShards;
    }

    private String getRolloverIndexForQuery(String itemType) {
        return indexPrefix + "-" + itemType.toLowerCase() + "-*";
    }

    public void setRolloverIndexNumberOfShards(String rolloverIndexNumberOfShards) {
        this.rolloverIndexNumberOfShards = rolloverIndexNumberOfShards;
    }

    private void internalCreateRolloverTemplate(String itemName) throws IOException {
        Analyzer analyzer = new Analyzer(new CustomAnalyzer.Builder().tokenizer("keyword").filter("lowercase", "asciifolding").build());

        String rolloverAlias = indexPrefix + "-" + itemName;

        IndexSettings indexSettings = new IndexSettings
                .Builder()
                .analysis(new IndexSettingsAnalysis.Builder().analyzer(Map.of("folding", analyzer)).build())
                .numberOfShards(StringUtils.defaultIfEmpty(rolloverIndexNumberOfShards, monthlyIndexNumberOfShards))
                .numberOfReplicas(StringUtils.defaultIfEmpty(rolloverIndexNumberOfReplicas, monthlyIndexNumberOfReplicas))
                .mapping(new MappingLimitSettings.Builder().totalFields(new MappingLimitSettingsTotalFields.Builder().limit(StringUtils.defaultIfEmpty(rolloverIndexMappingTotalFieldsLimit, monthlyIndexMappingTotalFieldsLimit)).build()).build())
                .maxDocvalueFieldsSearch(Integer.valueOf(StringUtils.defaultIfEmpty(rolloverIndexMaxDocValueFieldsSearch, monthlyIndexMaxDocValueFieldsSearch)))
                .lifecycle(new IndexSettingsLifecycle
                        .Builder()
                        .name(indexPrefix + "-" + ROLLOVER_LIFECYCLE_NAME)
                        .rolloverAlias(rolloverAlias)
                        .build())
                .build();


        if (mappings.get(itemName) == null) {
            LOGGER.warn("Couldn't find mapping for item {}, won't create monthly index template", itemName);
            return;
        }
        TypeMapping mapping = parseMappingFromJson(mappings.get(itemName));

        PutIndexTemplateRequest putIndexTemplateRequest = new PutIndexTemplateRequest.Builder()
                .indexPatterns(Collections.singletonList(getRolloverIndexForQuery(itemName)))
                .priority(1L)
                .template(new IndexTemplateMapping.Builder().settings(indexSettings).mappings(mapping).build())
                .build();

        esClient.indices().putIndexTemplate(putIndexTemplateRequest);
    }

    public static TypeMapping parseMappingFromJson(String jsonMapping) {
        JacksonJsonpMapper mapper = new JacksonJsonpMapper();
        JsonProvider provider = JsonProvider.provider();

        try (JsonParser parser = provider.createParser(new StringReader(jsonMapping))) {
            return TypeMapping._DESERIALIZER.deserialize(parser, mapper);
        }
    }

    @Deprecated
    public void setItemsMonthlyIndexedOverride(String itemsMonthlyIndexedOverride) {
        this.itemsMonthlyIndexed = StringUtils.isNotEmpty(itemsMonthlyIndexedOverride) ? Arrays.asList(itemsMonthlyIndexedOverride.split(",").clone()) : Collections.emptyList();
    }

    private boolean isItemTypeRollingOver(String itemType) {
        return (rolloverIndices != null ? rolloverIndices : itemsMonthlyIndexed).contains(itemType);
    }

    @Override
    public boolean removeIndex(String itemType) {
        return false;
    }

    public void setRolloverIndices(String rolloverIndices) {
        this.rolloverIndices = StringUtils.isNotEmpty(rolloverIndices) ? Arrays.asList(rolloverIndices.split(",").clone()) : null;
    }

    @Override
    public void purge(String scope) {

    }

    @Override
    public void bundleChanged(BundleEvent event) {

    }
}
