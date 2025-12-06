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
package org.apache.unomi.services.impl;

import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.query.DateRange;
import org.apache.unomi.api.query.IpRange;
import org.apache.unomi.api.query.NumericRange;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.aggregate.DateRangeAggregate;
import org.apache.unomi.persistence.spi.aggregate.IpRangeAggregate;
import org.apache.unomi.persistence.spi.aggregate.NumericRangeAggregate;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.apache.unomi.services.common.security.KarafSecurityService;
import org.apache.unomi.services.common.security.AuditServiceImpl;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;
import static org.junit.jupiter.api.Assertions.*;

public class InMemoryPersistenceServiceImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryPersistenceServiceImplTest.class);
    private InMemoryPersistenceServiceImpl persistenceService;
    private ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private ExecutionContextManagerImpl executionContextManager;
    private KarafSecurityService securityService;
    private AuditServiceImpl auditService;
    private DefinitionsServiceImpl definitionsService;

    // Test helper class
    public static class TestMetadataItem extends MetadataItem {
        public static final String ITEM_TYPE = "testMetadataItem";
        private Metadata metadata;
        private Map<String, Object> properties = new HashMap<>();
        private String name;
        private Set<String> tags;
        private Double numericValue;

        public TestMetadataItem() {
            setItemType(ITEM_TYPE);
        }

        @Override
        public String getItemType() {
            // If itemType was explicitly set (different from default), use that
            // Otherwise return the default ITEM_TYPE constant
            if (itemType != null && !itemType.equals(ITEM_TYPE)) {
                return itemType;
            }
            return ITEM_TYPE;
        }

        @Override
        public Metadata getMetadata() {
            return metadata;
        }

        public void setMetadata(Metadata metadata) {
            this.metadata = metadata;
        }

        public void setProperty(String name, Object value) {
            properties.put(name, value);
        }

        public Object getProperty(String name) {
            return properties.get(name);
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Set<String> getTags() {
            return tags;
        }

        public void setTags(Set<String> tags) {
            this.tags = tags;
        }

        public Double getNumericValue() {
            return numericValue;
        }

        public void setNumericValue(Double numericValue) {
            this.numericValue = numericValue;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        CustomObjectMapper.getCustomInstance().registerBuiltInItemTypeClass(TestMetadataItem.ITEM_TYPE, TestMetadataItem.class);
        CustomObjectMapper.getCustomInstance().registerBuiltInItemTypeClass(SimpleItem.ITEM_TYPE, SimpleItem.class);
        CustomObjectMapper.getCustomInstance().registerBuiltInItemTypeClass(NestedItem.ITEM_TYPE, NestedItem.class);
        // Clean up any existing persistence service before deleting directory
        if (persistenceService != null) {
            try {
                persistenceService.purge((java.util.Date)null);
                if (persistenceService instanceof InMemoryPersistenceServiceImpl) {
                    ((InMemoryPersistenceServiceImpl) persistenceService).shutdown();
                }
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            persistenceService = null;
        }
        // Use robust directory deletion with retries
        TestHelper.cleanDefaultStorageDirectory(10);
        conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();
        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);
        // Create service with refresh delay enabled by default to simulate ES/OS behavior
        // Tests use TestHelper.retryQueryUntilAvailable() to wait for items to become available
        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);
        
        // Set up minimal definitions service and register dummy action types to prevent warnings
        // when rules with unresolved action types are loaded from persisted files
        setupDummyActionTypes();
    }
    
    /**
     * Sets up a minimal definitions service and registers dummy action types to prevent warnings
     * when rules with unresolved action types are loaded from persisted files.
     * This ensures that any rules loaded from previous test runs have valid action types.
     * 
     * Note: These action types use a no-op executor name. Since this test doesn't set up
     * an ActionExecutorDispatcher, these action types are only used for rule resolution,
     * not execution. If they are ever executed in other contexts, the missing executor
     * will be handled gracefully (returning NO_CHANGE).
     */
    private void setupDummyActionTypes() {
        // Create minimal definitions service
        definitionsService = new DefinitionsServiceImpl();
        definitionsService.setPersistenceService(persistenceService);
        org.osgi.framework.BundleContext bundleContext = TestHelper.createMockBundleContext();
        definitionsService.setBundleContext(bundleContext);
        definitionsService.setContextManager(executionContextManager);
        definitionsService.setTenantService(new org.apache.unomi.services.impl.TestTenantService());
        definitionsService.setCacheService(new org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl());
        definitionsService.setConditionValidationService(TestHelper.createConditionValidationService());
        definitionsService.setTracerService(TestHelper.createTracerService());
        // Create minimal scheduler service for refresh timers
        org.apache.unomi.api.services.SchedulerService schedulerService = TestHelper.createSchedulerService(
            "test-definitions-node", persistenceService, executionContextManager, bundleContext, null, -1, false, false);
        definitionsService.setSchedulerService(schedulerService);
        definitionsService.postConstruct();
        
        // Register common dummy action types that might be referenced in persisted rules
        // These action types are only needed for rule resolution (ParserHelper.resolveActionTypes),
        // not for actual execution. Since this test doesn't set up an ActionExecutorDispatcher,
        // these action types won't be executed. If they are ever executed in other contexts,
        // the ActionExecutorDispatcher will handle missing executors gracefully by returning NO_CHANGE.
        ActionType unknownActionType = TestHelper.createActionType("unknown", "noop");
        definitionsService.setActionType(unknownActionType);
        
        ActionType testActionType = TestHelper.createActionType("test", "noop");
        definitionsService.setActionType(testActionType);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Properly shutdown persistence service to release file handles
        if (persistenceService != null) {
            try {
                persistenceService.purge((java.util.Date)null);
                if (persistenceService instanceof InMemoryPersistenceServiceImpl) {
                    ((InMemoryPersistenceServiceImpl) persistenceService).shutdown();
                }
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            persistenceService = null;
        }
        // Clean up directory after service is shut down
        Path defaultStorageDir = Paths.get(InMemoryPersistenceServiceImpl.DEFAULT_STORAGE_DIR).toAbsolutePath().normalize();
        if (Files.exists(defaultStorageDir)) {
            try {
                // Use robust deletion with retries
                TestHelper.cleanDefaultStorageDirectory(10);
            } catch (Exception e) {
                // Log but don't fail test if cleanup fails
                LOGGER.warn("Failed to clean up storage directory in tearDown: {}", e.getMessage());
            }
        }
    }

    @Nested
    class BasicOperations {
        @Test
        void shouldSaveAndLoadItem() {
            // given
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            profile.setProperty("firstName", "John");

            // when
            boolean saved = persistenceService.save(profile);
            Profile loaded = persistenceService.load("test-profile", Profile.class);

            // then
            assertTrue(saved);
            assertNotNull(loaded);
            assertEquals("John", loaded.getProperty("firstName"));
        }

        @Test
        void shouldRemoveItem() {
            // given
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            persistenceService.save(profile);

            // when
            boolean removed = persistenceService.remove("test-profile", Profile.class);
            Profile loaded = persistenceService.load("test-profile", Profile.class);

            // then
            assertTrue(removed);
            assertNull(loaded);
        }

        @Test
        void shouldHandleVersioning() {
            // Create a test item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-version");
            item.setName("Test Version");

            // Initial save should set version to 1
            persistenceService.save(item);
            assertEquals(1L, item.getVersion());

            // Subsequent saves should increment version
            persistenceService.save(item);
            assertEquals(2L, item.getVersion());

            // Load and verify version persisted
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            assertEquals(2L, loaded.getVersion());
        }

        @Test
        void shouldHandleVersioningWithExplicitVersion() {
            // Create a test item with explicit version
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-explicit-version");
            item.setName("Test Explicit Version");
            item.setVersion(5L);

            // Save should increment existing version
            persistenceService.save(item);
            assertEquals(6L, item.getVersion());

            // Load and verify version persisted
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            assertEquals(6L, loaded.getVersion());
        }
    }

    @Nested
    class ConditionEvaluation {
        @Test
        void shouldEvaluatePropertyCondition() {
            // given
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            profile.setProperty("age", 25);

            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("profilePropertyCondition"));
            condition.setParameter("propertyName", "properties.age");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", 25);

            // when
            boolean result = persistenceService.isValidCondition(condition, profile);

            // then
            assertTrue(result);
        }

        @Test
        void shouldEvaluateMetadataCondition() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            Metadata metadata = new Metadata();
            metadata.setId("test-item");
            metadata.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item.setMetadata(metadata);

            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            condition.setParameter("propertyName", "metadata.tags");
            condition.setParameter("comparisonOperator", "contains");
            condition.setParameter("propertyValue", "tag1");

            // when
            boolean result = persistenceService.isValidCondition(condition, item);

            // then
            assertTrue(result);
        }

        @Test
        void shouldEvaluateBooleanCondition() {
            // given
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            profile.setProperty("age", 25);
            profile.setProperty("active", true);

            Condition ageCondition = new Condition();
            ageCondition.setConditionType(TestConditionEvaluators.getConditionType("profilePropertyCondition"));
            ageCondition.setParameter("propertyName", "properties.age");
            ageCondition.setParameter("comparisonOperator", "equals");
            ageCondition.setParameter("propertyValue", 25);

            Condition activeCondition = new Condition();
            activeCondition.setConditionType(TestConditionEvaluators.getConditionType("profilePropertyCondition"));
            activeCondition.setParameter("propertyName", "properties.active");
            activeCondition.setParameter("comparisonOperator", "equals");
            activeCondition.setParameter("propertyValue", true);

            Condition booleanCondition = new Condition();
            booleanCondition.setConditionType(TestConditionEvaluators.getConditionType("booleanCondition"));
            booleanCondition.setParameter("operator", "and");
            booleanCondition.setParameter("subConditions", Arrays.asList(ageCondition, activeCondition));

            // when
            boolean result = persistenceService.isValidCondition(booleanCondition, profile);

            // then
            assertTrue(result);
        }

        @Test
        void shouldEvaluateMapPropertyCondition() {
            // given
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            Map<String, Object> preferences = new HashMap<>();
            preferences.put("color", "blue");
            profile.setProperty("preferences", preferences);

            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            condition.setParameter("propertyName", "properties.preferences.color");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", "blue");

            // when
            boolean result = persistenceService.isValidCondition(condition, profile);

            // then
            assertTrue(result);
        }
    }

    @Nested
    class FieldMatchingOperations {
        @Test
        void shouldMatchSimpleField() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setName("test-name");
            Metadata metadata = new Metadata();
            metadata.setId("test-item");
            metadata.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item.setMetadata(metadata);
            persistenceService.save(item);

            // when - retry query until item is available (handles refresh delay)
            List<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("name", "test-name", null, TestMetadataItem.class),
                1
            );

            // then
            assertEquals(1, results.size());
            assertEquals("test-item", results.get(0).getItemId());
        }

        @Test
        void shouldMatchNestedProperty() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setProperty("nested.field", "test-value");
            Metadata metadata = new Metadata();
            metadata.setId("test-item");
            metadata.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item.setMetadata(metadata);
            persistenceService.save(item);

            // when - retry query until item is available (handles refresh delay)
            List<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.nested\\.field", "test-value", null, TestMetadataItem.class),
                1
            );

            // then
            assertEquals(1, results.size());
            assertEquals("test-item", results.get(0).getItemId());
        }

        @Test
        void shouldMatchMetadataField() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            Metadata metadata = new Metadata();
            metadata.setId("test-metadata");
            metadata.setName("test-metadata-name");
            item.setMetadata(metadata);
            persistenceService.save(item);

            // when - retry query until item is available (handles refresh delay)
            List<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("metadata.name", "test-metadata-name", null, TestMetadataItem.class),
                1
            );

            // then
            assertEquals(1, results.size());
            assertEquals("test-item", results.get(0).getItemId());
        }

        @Test
        void shouldMatchCollectionField() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            Metadata metadata = new Metadata();
            metadata.setId("test-item");
            metadata.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item.setMetadata(metadata);
            persistenceService.save(item);

            // when - retry query until item is available (handles refresh delay)
            List<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("tags", "tag1", null, TestMetadataItem.class),
                1
            );

            // then
            assertEquals(1, results.size());
            assertEquals("test-item", results.get(0).getItemId());
        }

        @Test
        void shouldHandleNonExistentAndNullFields() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            Map<String, Object> map = new HashMap<>();
            map.put("nested", null);
            item.setProperty("map", map);
            Metadata metadata = new Metadata();
            metadata.setId("test-item");
            metadata.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item.setMetadata(metadata);
            persistenceService.save(item);

            // when - retry queries until item is available (handles refresh delay)
            // Even though we expect 0 results, we should wait for the item to be available
            List<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("nonexistent", "any-value", null, TestMetadataItem.class),
                0
            );
            List<TestMetadataItem> results2 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.map.nested.field", "any-value", null, TestMetadataItem.class),
                0
            );
            List<TestMetadataItem> results3 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("nonexistent.nested.field", "any-value", null, TestMetadataItem.class),
                0
            );

            // then
            assertEquals(0, results1.size());
            assertEquals(0, results2.size());
            assertEquals(0, results3.size());
        }

        @Test
        void shouldHandleComplexDotNotation() {
            // Test both escaped dots and bracket notation in a single comprehensive test
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            Metadata metadata = new Metadata();
            metadata.setId("test-item");
            metadata.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item.setMetadata(metadata);

            // Setup nested structure
            Map<String, Object> level1 = new HashMap<>();
            Map<String, Object> level2 = new HashMap<>();
            level2.put("key.with.dots", "test-value");
            level2.put("regular.key", "another-value");
            level1.put("nested.field", level2);
            item.setProperty("map", level1);

            // Add direct properties with dots
            item.setProperty("direct.key.with.dots", "direct-value");

            persistenceService.save(item);

            // Test different notation styles - retry queries until items are available
            List<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.direct\\.key\\.with\\.dots", "direct-value", null, TestMetadataItem.class),
                1
            );
            List<TestMetadataItem> results2 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.map['nested.field']['key.with.dots']", "test-value", null, TestMetadataItem.class),
                1
            );
            List<TestMetadataItem> results3 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.map.nested\\.field.regular\\.key", "another-value", null, TestMetadataItem.class),
                1
            );

            // Verify all notation styles work
            assertEquals(1, results1.size());
            assertEquals(1, results2.size());
            assertEquals(1, results3.size());
            assertEquals("test-item", results1.get(0).getItemId());
            assertEquals("test-item", results2.get(0).getItemId());
            assertEquals("test-item", results3.get(0).getItemId());
        }

        @Test
        void shouldHandleSpecialFieldTypes() {
            // Test boolean fields and direct field access in one test
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");

            // Test boolean property
            item.setProperty("active", true);

            // Test direct field access
            item.tags = new HashSet<>(Arrays.asList("tag1", "tag2"));
            Metadata metadata = new Metadata();
            metadata.setId("test-item");
            metadata.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item.setMetadata(metadata);

            // Test collection in map
            Map<String, Object> map = new HashMap<>();
            map.put("list.of.items", Arrays.asList("item1", "item2"));
            item.setProperty("nested", map);

            persistenceService.save(item);

            // Verify all special field types - retry queries until items are available
            List<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.active", "true", null, TestMetadataItem.class),
                1
            );
            List<TestMetadataItem> results2 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("tags", "tag1", null, TestMetadataItem.class),
                1
            );
            List<TestMetadataItem> results3 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.nested.list\\.of\\.items", "item1", null, TestMetadataItem.class),
                1
            );

            assertEquals(1, results1.size());
            assertEquals(1, results2.size());
            assertEquals(1, results3.size());
            assertEquals("test-item", results1.get(0).getItemId());
            assertEquals("test-item", results2.get(0).getItemId());
            assertEquals("test-item", results3.get(0).getItemId());
        }
    }

    @Nested
    class TestMatchOperations {
        @Test
        void shouldReturnTrueForNullCondition() {
            // given
            Profile profile = new Profile();
            profile.setItemId("test-profile");

            // when
            boolean result = persistenceService.testMatch(null, profile);

            // then
            assertTrue(result);
        }

        @Test
        void shouldReturnFalseForNullItem() {
            // given
            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));

            // when
            boolean result = persistenceService.testMatch(condition, null);

            // then
            assertFalse(result);
        }

        @Test
        void shouldMatchPropertyCondition() {
            // given
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            profile.setProperty("age", 25);

            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            condition.setParameter("propertyName", "properties.age");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", 25);

            // when
            boolean result = persistenceService.testMatch(condition, profile);

            // then
            assertTrue(result);
        }

        @Test
        void shouldNotMatchPropertyCondition() {
            // given
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            profile.setProperty("age", 25);

            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            condition.setParameter("propertyName", "properties.age");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", 30);

            // when
            boolean result = persistenceService.testMatch(condition, profile);

            // then
            assertFalse(result);
        }

        @Test
        void shouldMatchComplexCondition() {
            // given
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            profile.setProperty("age", 25);
            profile.setProperty("active", true);

            Condition ageCondition = new Condition();
            ageCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            ageCondition.setParameter("propertyName", "properties.age");
            ageCondition.setParameter("comparisonOperator", "equals");
            ageCondition.setParameter("propertyValue", 25);

            Condition activeCondition = new Condition();
            activeCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            activeCondition.setParameter("propertyName", "properties.active");
            activeCondition.setParameter("comparisonOperator", "equals");
            activeCondition.setParameter("propertyValue", true);

            Condition booleanCondition = new Condition();
            booleanCondition.setConditionType(TestConditionEvaluators.getConditionType("booleanCondition"));
            booleanCondition.setParameter("operator", "and");
            booleanCondition.setParameter("subConditions", Arrays.asList(ageCondition, activeCondition));

            // when
            boolean result = persistenceService.testMatch(booleanCondition, profile);

            // then
            assertTrue(result);
        }
    }

    @Nested
    class ScrollOperations {
        @Test
        void shouldSupportScrollQueries() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profile.setProperty("index", i);
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // when - initial query with scroll (retry until items are available)
            PartialList<Profile> firstPage = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query(null, null, Profile.class, 0, 10, "1000"),
                10
            );

            // then - first page
            assertNotNull(firstPage);
            assertEquals(10, firstPage.getList().size());
            assertEquals(25, firstPage.getTotalSize());
            assertNotNull(firstPage.getScrollIdentifier());
            String scrollId = firstPage.getScrollIdentifier();

            // when - continue scroll
            PartialList<Profile> secondPage = persistenceService.continueScrollQuery(Profile.class, "2000", scrollId);

            // then - second page
            assertNotNull(secondPage);
            assertEquals(10, secondPage.getList().size());
            assertEquals(25, secondPage.getTotalSize());
            assertNotNull(secondPage.getScrollIdentifier());

            // when - continue scroll for last page
            PartialList<Profile> lastPage = persistenceService.continueScrollQuery(Profile.class, "2000", secondPage.getScrollIdentifier());

            // then - last page
            assertNotNull(lastPage);
            assertEquals(5, lastPage.getList().size());
            assertEquals(25, lastPage.getTotalSize());
            assertEquals(PartialList.Relation.EQUAL, lastPage.getTotalSizeRelation());
        }

        @Test
        void shouldHandleExpiredScrolls() {
            // given
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            persistenceService.save(profile);

            // when - initial query with very short scroll validity (retry until item is available)
            PartialList<Profile> firstPage = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query(null, null, Profile.class, 0, 1, "1"),
                1
            );

            // Wait for scroll to expire
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // when - try to continue expired scroll
            PartialList<Profile> secondPage = persistenceService.continueScrollQuery(Profile.class, "1000", firstPage.getScrollIdentifier());

            // then
            assertNotNull(secondPage);
            assertTrue(secondPage.getList().isEmpty());
            assertEquals(0, secondPage.getTotalSize());
        }

        @Test
        void shouldHandleInvalidScrollId() {
            // when
            PartialList<Profile> result = persistenceService.continueScrollQuery(Profile.class, "1000", "invalid-scroll-id");

            // then
            assertNotNull(result);
            assertTrue(result.getList().isEmpty());
            assertEquals(0, result.getTotalSize());
        }

        @Test
        void shouldHandleEmptyResultSet() {
            // when - query with condition that matches no items
            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            condition.setParameter("propertyName", "nonexistent");
            condition.setParameter("propertyValue", "value");

            PartialList<Profile> result = persistenceService.query(condition, null, Profile.class, 0, 10, "1000");

            // then
            assertNotNull(result);
            assertTrue(result.getList().isEmpty());
            assertEquals(0, result.getTotalSize());
            assertNull(result.getScrollIdentifier());
        }

        @Test
        void shouldSupportScrollQueriesWithCondition() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profile.setProperty("type", i % 2 == 0 ? "even" : "odd");
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // Create condition to match only even profiles
            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("profilePropertyCondition"));
            condition.setParameter("propertyName", "properties.type");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", "even");

            // when - initial query with scroll (retry until items are available)
            PartialList<Profile> firstPage = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query(condition, null, Profile.class, 0, 3, "1000"),
                3
            );

            // then - first page
            assertNotNull(firstPage);
            assertEquals(3, firstPage.getList().size());
            assertEquals(8, firstPage.getTotalSize()); // 8 even numbers in 0-14
            assertNotNull(firstPage.getScrollIdentifier());

            // when - continue scroll
            PartialList<Profile> secondPage = persistenceService.continueScrollQuery(Profile.class, "1000", firstPage.getScrollIdentifier());

            // then - second page
            assertNotNull(secondPage);
            assertEquals(3, secondPage.getList().size());
            assertEquals(8, secondPage.getTotalSize());

            // when - get last page
            PartialList<Profile> lastPage = persistenceService.continueScrollQuery(Profile.class, "1000", secondPage.getScrollIdentifier());

            // then - last page
            assertNotNull(lastPage);
            assertEquals(2, lastPage.getList().size());
            assertEquals(8, lastPage.getTotalSize());
            assertNull(lastPage.getScrollIdentifier());
        }

        @Test
        void shouldHandleExactPageSize() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // when - initial query with scroll and page size equal to total items (retry until items are available)
            PartialList<Profile> firstPage = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query(null, null, Profile.class, 0, 10, "1000"),
                10
            );

            // then
            assertNotNull(firstPage);
            assertEquals(10, firstPage.getList().size());
            assertEquals(10, firstPage.getTotalSize());
            assertNull(firstPage.getScrollIdentifier()); // No scroll ID needed as all items are returned
        }

        @Test
        void shouldSupportMultipleConcurrentScrolls() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // when - start two scroll queries (retry until items are available)
            PartialList<Profile> firstScroll = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query(null, null, Profile.class, 0, 4, "1000"),
                4
            );
            PartialList<Profile> secondScroll = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query(null, null, Profile.class, 0, 3, "1000"),
                3
            );

            // then - both scrolls should work independently
            assertNotNull(firstScroll.getScrollIdentifier());
            assertNotNull(secondScroll.getScrollIdentifier());
            assertNotEquals(firstScroll.getScrollIdentifier(), secondScroll.getScrollIdentifier());

            // when - continue both scrolls
            PartialList<Profile> firstScrollContinue = persistenceService.continueScrollQuery(Profile.class, "1000", firstScroll.getScrollIdentifier());
            PartialList<Profile> secondScrollContinue = persistenceService.continueScrollQuery(Profile.class, "1000", secondScroll.getScrollIdentifier());

            // then - both scrolls should maintain their own state
            assertEquals(4, firstScrollContinue.getList().size());
            assertEquals(3, secondScrollContinue.getList().size());
        }
    }

    @Nested
    class AggregationOperations {
        @Test
        void shouldAggregateByTerms() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profile.setProperty("category", i % 2 == 0 ? "A" : "B");
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // when - retry aggregation until items are available (handles refresh delay)
            TermsAggregate termsAggregate = new TermsAggregate("properties.category");
            Map<String, Long> results = TestHelper.retryUntil(
                () -> persistenceService.aggregateWithOptimizedQuery(null, termsAggregate, Profile.ITEM_TYPE),
                r -> r != null && r.size() == 2 && r.get("A") != null && r.get("A") == 5L
            );

            // then
            assertNotNull(results);
            assertEquals(2, results.size());
            assertEquals(5L, results.get("A"));
            assertEquals(5L, results.get("B"));
        }

        @Test
        void shouldAggregateByDateRange() {
            // given
            List<Profile> profiles = new ArrayList<>();
            Calendar cal = Calendar.getInstance();
            cal.set(2024, Calendar.JANUARY, 1);
            Date startDate = cal.getTime();

            for (int i = 0; i < 10; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                cal.setTime(startDate);
                cal.add(Calendar.DAY_OF_MONTH, i);
                profile.setProperty("lastVisit", cal.getTime());
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // Create date ranges
            List<DateRange> ranges = new ArrayList<>();
            cal.setTime(startDate);

            DateRange firstWeek = new DateRange();
            firstWeek.setKey("week1");
            firstWeek.setFrom(startDate);
            cal.add(Calendar.DAY_OF_MONTH, 7);
            firstWeek.setTo(cal.getTime());

            DateRange secondWeek = new DateRange();
            secondWeek.setKey("week2");
            secondWeek.setFrom(cal.getTime());
            cal.add(Calendar.DAY_OF_MONTH, 7);
            secondWeek.setTo(cal.getTime());

            ranges.add(firstWeek);
            ranges.add(secondWeek);

            // when - retry aggregation until items are available (handles refresh delay)
            DateRangeAggregate dateRangeAggregate = new DateRangeAggregate("properties.lastVisit", "yyyy-MM-dd", ranges);
            Map<String, Long> results = TestHelper.retryUntil(
                () -> persistenceService.aggregateWithOptimizedQuery(null, dateRangeAggregate, Profile.ITEM_TYPE),
                r -> r != null && r.size() == 2 && r.get("week1") != null && r.get("week1") == 7L
            );

            // then
            assertNotNull(results);
            assertEquals(2, results.size());
            assertEquals(7L, results.get("week1")); // Days 0-6
            assertEquals(3L, results.get("week2")); // Days 7-9
        }

        @Test
        void shouldAggregateByNumericRange() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profile.setProperty("age", 20 + i * 5); // Ages: 20, 25, 30, 35, 40, 45, 50, 55, 60, 65
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // Create numeric ranges
            List<NumericRange> ranges = new ArrayList<>();

            NumericRange young = new NumericRange();
            young.setKey("young");
            young.setFrom(20.0);
            young.setTo(35.0);

            NumericRange middleAged = new NumericRange();
            middleAged.setKey("middleAged");
            middleAged.setFrom(35.0);
            middleAged.setTo(50.0);

            NumericRange senior = new NumericRange();
            senior.setKey("senior");
            senior.setFrom(50.0);
            senior.setTo(70.0);

            ranges.add(young);
            ranges.add(middleAged);
            ranges.add(senior);

            // when - retry aggregation until items are available (handles refresh delay)
            NumericRangeAggregate numericRangeAggregate = new NumericRangeAggregate("properties.age", ranges);
            Map<String, Long> results = TestHelper.retryUntil(
                () -> persistenceService.aggregateWithOptimizedQuery(null, numericRangeAggregate, Profile.ITEM_TYPE),
                r -> r != null && r.size() == 3 && r.get("young") != null && r.get("young") == 3L
            );

            // then
            assertNotNull(results);
            assertEquals(3, results.size());
            assertEquals(3L, results.get("young")); // 20, 25, 30
            assertEquals(3L, results.get("middleAged")); // 35, 40, 45
            assertEquals(4L, results.get("senior")); // 50, 55, 60, 65
        }

        @Test
        void shouldAggregateByIpRange() {
            // given
            List<Profile> profiles = new ArrayList<>();
            String[] ips = {
                "192.168.1.1",
                "192.168.1.100",
                "192.168.2.1",
                "192.168.2.100",
                "10.0.0.1"
            };

            for (int i = 0; i < ips.length; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profile.setProperty("ipAddress", ips[i]);
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // Create IP ranges
            List<IpRange> ranges = new ArrayList<>();

            IpRange subnet1 = new IpRange();
            subnet1.setKey("subnet1");
            subnet1.setFrom("192.168.1.0");
            subnet1.setTo("192.168.1.255");

            IpRange subnet2 = new IpRange();
            subnet2.setKey("subnet2");
            subnet2.setFrom("192.168.2.0");
            subnet2.setTo("192.168.2.255");

            IpRange otherRange = new IpRange();
            otherRange.setKey("other");
            otherRange.setFrom("10.0.0.0");
            otherRange.setTo("10.255.255.255");

            ranges.add(subnet1);
            ranges.add(subnet2);
            ranges.add(otherRange);

            // when - retry aggregation until items are available (handles refresh delay)
            IpRangeAggregate ipRangeAggregate = new IpRangeAggregate("properties.ipAddress", ranges);
            Map<String, Long> results = TestHelper.retryUntil(
                () -> persistenceService.aggregateWithOptimizedQuery(null, ipRangeAggregate, Profile.ITEM_TYPE),
                r -> r != null && r.size() == 3 && r.get("subnet1") != null && r.get("subnet1") == 2L
            );

            // then
            assertNotNull(results);
            assertEquals(3, results.size());
            assertEquals(2L, results.get("subnet1")); // 192.168.1.1, 192.168.1.100
            assertEquals(2L, results.get("subnet2")); // 192.168.2.1, 192.168.2.100
            assertEquals(1L, results.get("other")); // 10.0.0.1
        }

        @Test
        void shouldAggregateWithSizeLimit() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profile.setProperty("category", String.valueOf((char)('A' + (i % 3))));  // Creates categories A, B, C
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // when - retry aggregation until items are available (handles refresh delay)
            TermsAggregate termsAggregate = new TermsAggregate("properties.category");
            Map<String, Long> results = TestHelper.retryUntil(
                () -> persistenceService.aggregateWithOptimizedQuery(null, termsAggregate, Profile.ITEM_TYPE, 2),
                r -> r != null && r.size() == 2
            );

            // then
            assertNotNull(results);
            assertEquals(2, results.size());
            // Should only return the top 2 categories by count
            assertTrue(results.values().stream().allMatch(count -> count >= 3));
        }

        @Test
        void shouldAggregateWithCondition() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profile.setProperty("category", i % 2 == 0 ? "A" : "B");
                profile.setProperty("active", i < 5);  // First 5 profiles are active
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // Create condition to match only active profiles
            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            condition.setParameter("propertyName", "properties.active");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", true);

            // when - retry aggregation until items are available (handles refresh delay)
            TermsAggregate termsAggregate = new TermsAggregate("properties.category");
            Map<String, Long> results = TestHelper.retryUntil(
                () -> persistenceService.aggregateWithOptimizedQuery(condition, termsAggregate, Profile.ITEM_TYPE),
                r -> r != null && r.size() == 2 && r.values().stream().mapToLong(Long::longValue).sum() == 5L
            );

            // then
            assertNotNull(results);
            assertEquals(2, results.size());
            // Should only count active profiles
            assertEquals(5L, results.values().stream().mapToLong(Long::longValue).sum());
        }
    }

    @Nested
    class CountOperations {
        @Test
        void shouldCountAllItemsOfType() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // when - retry query until all items are available (handles refresh delay)
            long count = TestHelper.retryUntil(
                () -> persistenceService.queryCount(null, Profile.ITEM_TYPE),
                c -> c == 10L
            );

            // then
            assertEquals(10, count);
        }

        @Test
        void shouldCountItemsMatchingCondition() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profile.setProperty("active", i % 2 == 0);  // Even numbered profiles are active
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // Create condition to match only active profiles
            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            condition.setParameter("propertyName", "properties.active");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", true);

            // when - retry query until items are available (handles refresh delay)
            long count = TestHelper.retryUntil(
                () -> persistenceService.queryCount(condition, Profile.ITEM_TYPE),
                c -> c == 5L
            );

            // then
            assertEquals(5, count);  // Should count only active profiles
        }

        @Test
        void shouldReturnZeroForNonExistentType() {
            // when
            long count = persistenceService.queryCount(null, "nonexistent-type");

            // then
            assertEquals(0, count);
        }

        @Test
        void shouldReturnZeroForNonMatchingCondition() {
            // given
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            profile.setProperty("active", true);
            persistenceService.save(profile);

            // Create condition that won't match any profiles
            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            condition.setParameter("propertyName", "properties.active");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", false);

            // when
            long count = persistenceService.queryCount(condition, Profile.ITEM_TYPE);

            // then
            assertEquals(0, count);
        }

        @Test
        void shouldRespectTenantIsolationInGetAllItemsCount() {
            // Given - items of different types for different tenants
            String itemType1 = "tenant-test-type1";
            String itemType2 = "tenant-test-type2";

            // Create items in tenant1
            executionContextManager.executeAsTenant("tenant1", () -> {
                for (int i = 0; i < 5; i++) {
                    CustomItem item = new CustomItem();
                    item.setItemId("tenant1-" + itemType1 + "-" + i);
                    item.setItemType(itemType1);
                    persistenceService.save(item);
                }

                for (int i = 0; i < 3; i++) {
                    CustomItem item = new CustomItem();
                    item.setItemId("tenant1-" + itemType2 + "-" + i);
                    item.setItemType(itemType2);
                    persistenceService.save(item);
                }
                return null;
            });

            // Create items in tenant2
            executionContextManager.executeAsTenant("tenant2", () -> {
                for (int i = 0; i < 7; i++) {
                    CustomItem item = new CustomItem();
                    item.setItemId("tenant2-" + itemType1 + "-" + i);
                    item.setItemType(itemType1);
                    persistenceService.save(item);
                }

                for (int i = 0; i < 4; i++) {
                    CustomItem item = new CustomItem();
                    item.setItemId("tenant2-" + itemType2 + "-" + i);
                    item.setItemType(itemType2);
                    persistenceService.save(item);
                }
                return null;
            });

            // When - count items from different tenant contexts (retry until items are available)
            long tenant1Type1Count = executionContextManager.executeAsTenant("tenant1", () ->
                TestHelper.retryUntil(
                    () -> persistenceService.getAllItemsCount(itemType1),
                    c -> c == 5L
                ));

            long tenant1Type2Count = executionContextManager.executeAsTenant("tenant1", () ->
                TestHelper.retryUntil(
                    () -> persistenceService.getAllItemsCount(itemType2),
                    c -> c == 3L
                ));

            long tenant2Type1Count = executionContextManager.executeAsTenant("tenant2", () ->
                TestHelper.retryUntil(
                    () -> persistenceService.getAllItemsCount(itemType1),
                    c -> c == 7L
                ));

            long tenant2Type2Count = executionContextManager.executeAsTenant("tenant2", () ->
                TestHelper.retryUntil(
                    () -> persistenceService.getAllItemsCount(itemType2),
                    c -> c == 4L
                ));

            // Then - counts should reflect tenant isolation
            assertEquals(5, tenant1Type1Count, "Tenant1 should see 5 items of type1");
            assertEquals(3, tenant1Type2Count, "Tenant1 should see 3 items of type2");
            assertEquals(7, tenant2Type1Count, "Tenant2 should see 7 items of type1");
            assertEquals(4, tenant2Type2Count, "Tenant2 should see 4 items of type2");

            // And - tenant1 should not see tenant2's items and vice versa
            executionContextManager.executeAsTenant("tenant1", () -> {
                assertEquals(0, persistenceService.getAllItemsCount("non-existent-type"),
                    "Should return 0 for non-existent item type");
                return null;
            });

            // Test null item type
            executionContextManager.executeAsTenant("tenant1", () -> {
                assertEquals(0, persistenceService.getAllItemsCount(null),
                    "Should handle null item type gracefully");
                return null;
            });
        }
    }

    @Nested
    class FullTextSearchOperations {
        @Test
        void shouldSearchByFullText() {
            // given
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setProperty("description", "This is description that should match");
            item1.setProperty("tags", Arrays.asList("tag1", "tag2"));
            Metadata metadata1 = new Metadata();
            metadata1.setId("item1");
            metadata1.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item1.setMetadata(metadata1);
            persistenceService.save(item1);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setProperty("description", "Another description");
            item2.setProperty("tags", Arrays.asList("tag3", "tag4"));
            Metadata metadata2 = new Metadata();
            metadata2.setId("item2");
            metadata2.setTags(new HashSet<>(Arrays.asList("tag3", "tag4")));
            item2.setMetadata(metadata2);
            persistenceService.save(item2);

            // when - retry query until items are available (handles refresh delay)
            PartialList<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.queryFullText("match", null, TestMetadataItem.class, 0, 10),
                1
            );

            // then
            assertEquals(1, results.getList().size());
            assertEquals("item1", results.getList().get(0).getItemId());
        }

        @Test
        void shouldSearchByFullTextWithFieldCriteria() {
            // given
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setProperty("category", "electronics");
            item1.setProperty("description", "A matching product");
            Metadata metadata1 = new Metadata();
            metadata1.setId("item1");
            metadata1.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item1.setMetadata(metadata1);
            persistenceService.save(item1);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setProperty("category", "electronics");
            item2.setProperty("description", "Another product");
            Metadata metadata2 = new Metadata();
            metadata2.setId("item2");
            metadata2.setTags(new HashSet<>(Arrays.asList("tag3", "tag4")));
            item2.setMetadata(metadata2);
            persistenceService.save(item2);

            // when - retry query until items are available (handles refresh delay)
            PartialList<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.queryFullText(
                    "properties.category", "electronics", "matching", null, TestMetadataItem.class, 0, 10),
                1
            );

            // then
            assertEquals(1, results.getList().size());
            assertEquals("item1", results.getList().get(0).getItemId());
        }

        @Test
        void shouldSearchByFullTextWithCondition() {
            // given
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setProperty("active", true);
            item1.setProperty("description", "A test product");
            Metadata metadata = new Metadata();
            metadata.setId("test-item");
            metadata.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item1.setMetadata(metadata);
            persistenceService.save(item1);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setProperty("active", false);
            item2.setProperty("description", "Another test product");
            Metadata metadata2 = new Metadata();
            metadata2.setId("test-item");
            metadata2.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item2.setMetadata(metadata);
            persistenceService.save(item2);

            // Create condition for active items
            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            condition.setParameter("propertyName", "properties.active");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", true);

            // when - retry query until items are available (handles refresh delay)
            PartialList<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.queryFullText(
                    "test", condition, null, TestMetadataItem.class, 0, 10),
                1
            );

            // then
            assertEquals(1, results.getList().size());
            assertEquals("item1", results.getList().get(0).getItemId());
        }

        @Test
        void shouldHandleNestedProperties() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            Map<String, Object> nested = new HashMap<>();
            nested.put("deepValue", "test nested value");
            item.setProperty("nested", nested);
            Metadata metadata = new Metadata();
            metadata.setId("test-item");
            metadata.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item.setMetadata(metadata);
            persistenceService.save(item);

            // when - retry query until items are available (handles refresh delay)
            PartialList<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.queryFullText(
                    "nested value", null, TestMetadataItem.class, 0, 10),
                1
            );

            // then
            assertEquals(1, results.getList().size());
            assertEquals("item1", results.getList().get(0).getItemId());
        }

        @Test
        void shouldHandlePagination() {
            // given
            for (int i = 0; i < 5; i++) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + i);
                item.setProperty("description", "test item " + i);
                Metadata metadata = new Metadata();
                metadata.setId("test-item");
                metadata.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
                item.setMetadata(metadata);
                persistenceService.save(item);
            }

            // when - retry queries until items are available AND totalSize is correct (handles refresh delay)
            // Use retryUntil to check both list size and totalSize to avoid flakiness
            PartialList<TestMetadataItem> page1 = TestHelper.retryUntil(
                () -> persistenceService.queryFullText("test", null, TestMetadataItem.class, 0, 2),
                result -> result != null && result.getList().size() == 2 && result.getTotalSize() == 5
            );
            PartialList<TestMetadataItem> page2 = TestHelper.retryUntil(
                () -> persistenceService.queryFullText("test", null, TestMetadataItem.class, 2, 2),
                result -> result != null && result.getList().size() == 2 && result.getTotalSize() == 5
            );
            PartialList<TestMetadataItem> page3 = TestHelper.retryUntil(
                () -> persistenceService.queryFullText("test", null, TestMetadataItem.class, 4, 2),
                result -> result != null && result.getList().size() == 1 && result.getTotalSize() == 5
            );

            // then
            assertEquals(2, page1.getList().size(), "Page 1 should have 2 items");
            assertEquals(2, page2.getList().size(), "Page 2 should have 2 items");
            assertEquals(1, page3.getList().size(), "Page 3 should have 1 item");
            assertEquals(5, page1.getTotalSize(), "Page 1 totalSize should be 5");
            assertEquals(5, page2.getTotalSize(), "Page 2 totalSize should be 5");
            assertEquals(5, page3.getTotalSize(), "Page 3 totalSize should be 5");
        }

        @Test
        void shouldHandleEmptyResults() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setProperty("description", "A sample item");
            Metadata metadata = new Metadata();
            metadata.setId("test-item");
            metadata.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item.setMetadata(metadata);
            persistenceService.save(item);

            // when - retry query until item is available (handles refresh delay)
            // Even though we expect 0 results, we should wait for the item to be available
            PartialList<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.queryFullText(
                    "nonexistent", null, TestMetadataItem.class, 0, 10),
                0
            );

            // then
            assertTrue(results.getList().isEmpty());
            assertEquals(0, results.getTotalSize());
        }

        @Test
        void shouldBeCaseInsensitive() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setProperty("description", "TEST Description");
            Metadata metadata = new Metadata();
            metadata.setId("test-item");
            metadata.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
            item.setMetadata(metadata);
            persistenceService.save(item);

            // when - retry queries until item is available (handles refresh delay)
            PartialList<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.queryFullText(
                    "test", null, TestMetadataItem.class, 0, 10),
                1
            );
            PartialList<TestMetadataItem> results2 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.queryFullText(
                    "TEST", null, TestMetadataItem.class, 0, 10),
                1
            );
            PartialList<TestMetadataItem> results3 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.queryFullText(
                    "tEsT", null, TestMetadataItem.class, 0, 10),
                1
            );

            // then
            assertEquals(1, results1.getList().size());
            assertEquals(1, results2.getList().size());
            assertEquals(1, results3.getList().size());
        }

        @Nested
        class GenericItemTests {
            @Test
            void shouldSearchSimpleProperties() {
                // given
                SimpleItem item = new SimpleItem();
                item.setItemId("simple1");
                item.setSimpleProperty("searchable text");
                item.setNumericProperty(42);
                item.setBooleanProperty(true);
                persistenceService.save(item);

                // when - search in string property (retry until item is available)
                PartialList<SimpleItem> results1 = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "searchable", null, SimpleItem.class, 0, 10),
                    1
                );

                // when - search in numeric property (retry until item is available)
                PartialList<SimpleItem> results2 = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "42", null, SimpleItem.class, 0, 10),
                    1
                );

                // when - search in boolean property (retry until item is available)
                PartialList<SimpleItem> results3 = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "true", null, SimpleItem.class, 0, 10),
                    1
                );

                // then
                assertEquals(1, results1.getList().size());
                assertEquals(1, results2.getList().size());
                assertEquals(1, results3.getList().size());
            }

            @Test
            void shouldSearchNestedMapProperties() {
                // given
                NestedItem item = new NestedItem();
                item.setItemId("nested1");
                Map<String, Object> nestedMap = new HashMap<>();
                nestedMap.put("level1", "searchable");
                Map<String, Object> level2 = new HashMap<>();
                level2.put("level2", "nested searchable");
                nestedMap.put("deeper", level2);
                item.setNestedMap(nestedMap);
                persistenceService.save(item);

                // when - search in first level (retry until item is available)
                PartialList<NestedItem> results1 = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "searchable", null, NestedItem.class, 0, 10),
                    1
                );

                // when - search in nested level (retry until item is available)
                PartialList<NestedItem> results2 = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "nested searchable", null, NestedItem.class, 0, 10),
                    1
                );

                // then
                assertEquals(1, results1.getList().size());
                assertEquals(1, results2.getList().size());
            }

            @Test
            void shouldSearchInCollections() {
                // given
                NestedItem item = new NestedItem();
                item.setItemId("collection1");
                item.setStringList(Arrays.asList("first", "second", "third"));
                Set<Map<String, Object>> complexSet = new HashSet<>();
                Map<String, Object> setElement = new HashMap<>();
                setElement.put("key", "searchable value");
                complexSet.add(setElement);
                item.setComplexSet(complexSet);
                persistenceService.save(item);

                // when - search in string list (retry until item is available)
                PartialList<NestedItem> results1 = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "second", null, NestedItem.class, 0, 10),
                    1
                );

                // when - search in complex set (retry until item is available)
                PartialList<NestedItem> results2 = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "searchable value", null, NestedItem.class, 0, 10),
                    1
                );

                // then
                assertEquals(1, results1.getList().size());
                assertEquals(1, results2.getList().size());
            }

            @Test
            void shouldSearchPropertyNames() {
                // given
                NestedItem item = new NestedItem();
                item.setItemId("propnames1");
                Map<String, Object> nestedMap = new HashMap<>();
                nestedMap.put("searchable_key", "some value");
                item.setNestedMap(nestedMap);
                persistenceService.save(item);

                // when - search in property names (retry until item is available)
                PartialList<NestedItem> results = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "searchable_key", null, NestedItem.class, 0, 10),
                    1
                );

                // then
                assertEquals(1, results.getList().size());
            }

            @Test
            void shouldHandleNullValues() {
                // given
                NestedItem item = new NestedItem();
                item.setItemId("nulls1");
                Map<String, Object> nestedMap = new HashMap<>();
                nestedMap.put("nullValue", null);
                nestedMap.put("validValue", "searchable");
                item.setNestedMap(nestedMap);
                item.setStringList(null);
                persistenceService.save(item);

                // when - retry until item is available
                PartialList<NestedItem> results = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "searchable", null, NestedItem.class, 0, 10),
                    1
                );

                // then
                assertEquals(1, results.getList().size());
            }

            @Test
            void shouldHandleSpecialCharacters() {
                // given
                SimpleItem item = new SimpleItem();
                item.setItemId("special1");
                item.setSimpleProperty("Text with special chars: !@#$%^&*()");
                persistenceService.save(item);

                // when - retry until item is available
                PartialList<SimpleItem> results = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "!@#$%", null, SimpleItem.class, 0, 10),
                    1
                );

                // then
                assertEquals(1, results.getList().size());
            }

            @Test
            void shouldHandleEmptyCollections() {
                // given
                NestedItem item = new NestedItem();
                item.setItemId("empty1");
                item.setStringList(new ArrayList<>());
                item.setComplexSet(new HashSet<>());
                item.setNestedMap(new HashMap<>());
                persistenceService.save(item);

                // when - retry query (expects 0 results, but should still wait for refresh)
                PartialList<NestedItem> results = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "nonexistent", null, NestedItem.class, 0, 10),
                    0
                );

                // then
                assertEquals(0, results.getList().size());
            }

            @Test
            void shouldSearchAcrossMultipleItems() {
                // given
                SimpleItem item1 = new SimpleItem();
                item1.setItemId("multi1");
                item1.setSimpleProperty("common text");
                persistenceService.save(item1);

                NestedItem item2 = new NestedItem();
                item2.setItemId("multi2");
                Map<String, Object> nestedMap = new HashMap<>();
                nestedMap.put("key", "common text");
                item2.setNestedMap(nestedMap);
                persistenceService.save(item2);

                // when - search across different item types (retry until items are available)
                PartialList<SimpleItem> results1 = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "common", null, SimpleItem.class, 0, 10),
                    1
                );
                PartialList<NestedItem> results2 = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "common", null, NestedItem.class, 0, 10),
                    1
                );

                // then
                assertEquals(1, results1.getList().size());
                assertEquals(1, results2.getList().size());
            }

            @Test
            void shouldHandleRecursiveStructures() {
                // given
                NestedItem item = new NestedItem();
                item.setItemId("recursive1");
                Map<String, Object> level1 = new HashMap<>();
                Map<String, Object> level2 = new HashMap<>();
                Map<String, Object> level3 = new HashMap<>();
                level3.put("deepest", "searchable");
                level2.put("level3", level3);
                level1.put("level2", level2);
                item.setNestedMap(level1);
                persistenceService.save(item);

                // when - retry until item is available
                PartialList<NestedItem> results = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryFullText(
                        "searchable", null, NestedItem.class, 0, 10),
                    1
                );

                // then
                assertEquals(1, results.getList().size());
            }
        }
    }

    @Nested
    class SingleValuesMetricsOperations {
        @Test
        void shouldHandleNullOrEmptyParameters() {
            // when
            Map<String, Double> result1 = persistenceService.getSingleValuesMetrics(null, null, null, "testType");
            Map<String, Double> result2 = persistenceService.getSingleValuesMetrics(null, new String[0], "field", "testType");
            Map<String, Double> result3 = persistenceService.getSingleValuesMetrics(null, new String[]{"card"}, null, "testType");

            // then
            assertTrue(result1.isEmpty());
            assertTrue(result2.isEmpty());
            assertTrue(result3.isEmpty());
        }

        @Test
        void shouldCalculateAllMetricsForNumericValues() {
            // given
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setNumericValue(10.0);
            item1.setItemType(TestMetadataItem.ITEM_TYPE);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setNumericValue(20.0);
            item2.setItemType(TestMetadataItem.ITEM_TYPE);

            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setNumericValue(30.0);
            item3.setItemType(TestMetadataItem.ITEM_TYPE);

            persistenceService.save(item1);
            persistenceService.save(item2);
            persistenceService.save(item3);

            String[] metrics = {"card", "sum", "min", "max", "avg"};

            // when - retry metrics calculation until items are available (handles refresh delay)
            Map<String, Double> results = TestHelper.retryUntil(
                () -> persistenceService.getSingleValuesMetrics(
                    null, metrics, "numericValue", TestMetadataItem.ITEM_TYPE),
                r -> r != null && r.get("_card") != null && r.get("_card") == 3.0
            );

            // then
            assertEquals(3.0, results.get("_card"), 0.001);
            assertEquals(60.0, results.get("_sum"), 0.001);
            assertEquals(10.0, results.get("_min"), 0.001);
            assertEquals(30.0, results.get("_max"), 0.001);
            assertEquals(20.0, results.get("_avg"), 0.001);
        }

        @Test
        void shouldHandleNullValues() {
            // given
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setNumericValue(10.0);
            item1.setItemType(TestMetadataItem.ITEM_TYPE);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setNumericValue(null);
            item2.setItemType(TestMetadataItem.ITEM_TYPE);

            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setNumericValue(30.0);
            item3.setItemType(TestMetadataItem.ITEM_TYPE);

            persistenceService.save(item1);
            persistenceService.save(item2);
            persistenceService.save(item3);

            String[] metrics = {"card", "sum", "min", "max", "avg"};

            // when - retry metrics calculation until items are available (handles refresh delay)
            Map<String, Double> results = TestHelper.retryUntil(
                () -> persistenceService.getSingleValuesMetrics(
                    null, metrics, "numericValue", TestMetadataItem.ITEM_TYPE),
                r -> r != null && r.get("_card") != null && r.get("_card") == 2.0
            );

            // then
            assertEquals(2.0, results.get("_card"), 0.001); // Only counts non-null values
            assertEquals(40.0, results.get("_sum"), 0.001);
            assertEquals(10.0, results.get("_min"), 0.001);
            assertEquals(30.0, results.get("_max"), 0.001);
            assertEquals(20.0, results.get("_avg"), 0.001);
        }

        @Test
        void shouldHandleEmptyResultSet() {
            // given
            String[] metrics = {"card", "sum", "min", "max", "avg"};

            // when
            Map<String, Double> results = persistenceService.getSingleValuesMetrics(
                null, metrics, "numericValue", TestMetadataItem.ITEM_TYPE);

            // then
            assertEquals(0.0, results.get("_card"), 0.001);
            // ES/OS implementations don't return these metrics for empty sets
            assertFalse(results.containsKey("_sum"));
            assertFalse(results.containsKey("_min"));
            assertFalse(results.containsKey("_max"));
            assertFalse(results.containsKey("_avg"));
        }

        @Test
        void shouldFilterByCondition() {
            // given
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setNumericValue(10.0);
            item1.setProperty("category", "A");
            item1.setItemType(TestMetadataItem.ITEM_TYPE);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setNumericValue(20.0);
            item2.setProperty("category", "B");
            item2.setItemType(TestMetadataItem.ITEM_TYPE);

            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setNumericValue(30.0);
            item3.setProperty("category", "A");
            item3.setItemType(TestMetadataItem.ITEM_TYPE);

            persistenceService.save(item1);
            persistenceService.save(item2);
            persistenceService.save(item3);

            // Create condition for category A
            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            condition.setParameter("propertyName", "properties.category");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", "A");

            String[] metrics = {"card", "sum", "min", "max", "avg"};

            // when - retry metrics calculation until items are available (handles refresh delay)
            Map<String, Double> results = TestHelper.retryUntil(
                () -> persistenceService.getSingleValuesMetrics(
                    condition, metrics, "numericValue", TestMetadataItem.ITEM_TYPE),
                r -> r != null && r.get("_card") != null && r.get("_card") == 2.0
            );

            // then
            assertEquals(2.0, results.get("_card"), 0.001);
            assertEquals(40.0, results.get("_sum"), 0.001);
            assertEquals(10.0, results.get("_min"), 0.001);
            assertEquals(30.0, results.get("_max"), 0.001);
            assertEquals(20.0, results.get("_avg"), 0.001);
        }

        @Test
        void shouldHandleNonNumericValues() {
            // given
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setProperty("stringValue", "value1");
            item1.setItemType(TestMetadataItem.ITEM_TYPE);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setProperty("stringValue", "value2");
            item2.setItemType(TestMetadataItem.ITEM_TYPE);

            persistenceService.save(item1);
            persistenceService.save(item2);

            String[] metrics = {"card", "sum", "min", "max", "avg"};

            // when - retry metrics calculation until items are available (handles refresh delay)
            Map<String, Double> results = TestHelper.retryUntil(
                () -> persistenceService.getSingleValuesMetrics(
                    null, metrics, "properties.stringValue", TestMetadataItem.ITEM_TYPE),
                r -> r != null && r.get("_card") != null && r.get("_card") == 2.0
            );

            // then
            assertEquals(2.0, results.get("_card"), 0.001);
            // ES/OS implementations don't return numeric metrics for non-numeric fields
            assertFalse(results.containsKey("_sum"));
            assertFalse(results.containsKey("_min"));
            assertFalse(results.containsKey("_max"));
            assertFalse(results.containsKey("_avg"));
        }

        @Test
        void shouldHandleSpecificMetricsRequest() {
            // given
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setNumericValue(10.0);
            item1.setItemType(TestMetadataItem.ITEM_TYPE);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setNumericValue(20.0);
            item2.setItemType(TestMetadataItem.ITEM_TYPE);

            persistenceService.save(item1);
            persistenceService.save(item2);

            String[] metrics = {"min", "max"}; // Only request min and max

            // when - retry metrics calculation until items are available (handles refresh delay)
            Map<String, Double> results = TestHelper.retryUntil(
                () -> persistenceService.getSingleValuesMetrics(
                    null, metrics, "numericValue", TestMetadataItem.ITEM_TYPE),
                r -> r != null && r.size() == 2 && r.get("_min") != null
            );

            // then
            assertEquals(2, results.size());
            assertEquals(10.0, results.get("_min"), 0.001);
            assertEquals(20.0, results.get("_max"), 0.001);
            assertFalse(results.containsKey("_card"));
            assertFalse(results.containsKey("_sum"));
            assertFalse(results.containsKey("_avg"));
        }

        @Test
        void shouldHandleInvalidMetricNames() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setNumericValue(10.0);
            item.setItemType(TestMetadataItem.ITEM_TYPE);
            persistenceService.save(item);

            String[] metrics = {"invalid_metric", "card", "another_invalid"};

            // when - retry metrics calculation until items are available (handles refresh delay)
            Map<String, Double> results = TestHelper.retryUntil(
                () -> persistenceService.getSingleValuesMetrics(
                    null, metrics, "numericValue", TestMetadataItem.ITEM_TYPE),
                r -> r != null && r.size() == 1 && r.get("_card") != null && r.get("_card") == 1.0
            );

            // then
            assertEquals(1, results.size());
            assertEquals(1.0, results.get("_card"), 0.001);
        }

        @Test
        void shouldHandleNestedFieldPath() {
            // given
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            Map<String, Object> nestedMap = new HashMap<>();
            nestedMap.put("value", 10.0);
            item1.setProperty("nested", nestedMap);
            item1.setItemType(TestMetadataItem.ITEM_TYPE);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            nestedMap = new HashMap<>();
            nestedMap.put("value", 20.0);
            item2.setProperty("nested", nestedMap);
            item2.setItemType(TestMetadataItem.ITEM_TYPE);

            persistenceService.save(item1);
            persistenceService.save(item2);

            String[] metrics = {"card", "sum", "min", "max", "avg"};

            // when - retry metrics calculation until items are available (handles refresh delay)
            Map<String, Double> results = TestHelper.retryUntil(
                () -> persistenceService.getSingleValuesMetrics(
                    null, metrics, "properties.nested.value", TestMetadataItem.ITEM_TYPE),
                r -> r != null && r.get("_card") != null && r.get("_card") == 2.0
            );

            // then
            assertEquals(2.0, results.get("_card"), 0.001);
            assertEquals(30.0, results.get("_sum"), 0.001);
            assertEquals(10.0, results.get("_min"), 0.001);
            assertEquals(20.0, results.get("_max"), 0.001);
            assertEquals(15.0, results.get("_avg"), 0.001);
        }
    }

    @Nested
    class PaginationTests {
        @Test
        void shouldHandleNegativeSizeInQuery() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // when - query with size = -1 (retry until items are available)
            PartialList<Profile> result = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query(null, null, Profile.class, 0, -1),
                10
            );

            // then
            assertEquals(10, result.getList().size());
            assertEquals(10, result.getTotalSize());
            assertEquals(-1, result.getPageSize());
            assertEquals(0, result.getOffset());
        }

        @Test
        void shouldHandleNegativeSizeWithOffset() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // when - query with size = -1 and offset = 5 (retry until items are available)
            PartialList<Profile> result = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query(null, null, Profile.class, 5, -1),
                5
            );

            // then
            assertEquals(5, result.getList().size());
            assertEquals(10, result.getTotalSize());
            assertEquals(-1, result.getPageSize());
            assertEquals(5, result.getOffset());
        }

        @Test
        void shouldHandleNegativeSizeInScrollQuery() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // when - initial query with scroll and size = -1 (retry until items are available)
            PartialList<Profile> result = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query(null, null, Profile.class, 0, -1, "1000"),
                10
            );

            // then
            assertEquals(10, result.getList().size());
            assertEquals(10, result.getTotalSize());
            assertEquals(-1, result.getPageSize());
            assertNull(result.getScrollIdentifier()); // No scroll needed when getting all items
        }

        @Test
        void shouldHandleNegativeSizeInGetAllItems() {
            // given
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Profile profile = new Profile();
                profile.setItemId("profile-" + i);
                profiles.add(profile);
                persistenceService.save(profile);
            }

            // when - getAllItems with size = -1 (retry until items are available)
            PartialList<Profile> result = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.getAllItems(Profile.class, 0, -1, null),
                10
            );

            // then
            assertEquals(10, result.getList().size());
            assertEquals(10, result.getTotalSize());
            assertEquals(-1, result.getPageSize());
            assertEquals(0, result.getOffset());
        }
    }

    @Nested
    class FileStorageOperations {
        private Path tempDir;

        @BeforeEach
        void setUp() throws IOException {
            tempDir = Files.createTempDirectory("unomi-test");
            persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher, tempDir.toString(), true);
        }

        @AfterEach
        void tearDown() throws IOException {
            if (tempDir != null && Files.exists(tempDir)) {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
            }
        }

        @Test
        void shouldPersistItemToFile() throws IOException {
            // given
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            profile.setProperty("firstName", "John");

            // when
            persistenceService.save(profile);

            // then
            Path expectedPath = tempDir.resolve(Profile.ITEM_TYPE)
                .resolve(executionContextManager.getCurrentContext().getTenantId())
                .resolve("test-profile.json");
            assertTrue(Files.exists(expectedPath));
            String content = Files.readString(expectedPath);
            assertTrue(content.contains("John"));
        }

        @Test
        void shouldLoadPersistedItemsOnStartup() throws IOException {
            // given
            // First persistence service instance to save items
            persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher, tempDir.toString(), true, true, true);
            Profile profile1 = new Profile();
            profile1.setItemId("profile1");
            profile1.setProperty("name", "John");
            Profile profile2 = new Profile();
            profile2.setItemId("profile2");
            profile2.setProperty("name", "Jane");
            persistenceService.save(profile1);
            persistenceService.save(profile2);

            // when
            // Create new persistence service instance that should load persisted items
            persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher, tempDir.toString(), true, false, true);

            // then
            Profile loaded1 = persistenceService.load("profile1", Profile.class);
            Profile loaded2 = persistenceService.load("profile2", Profile.class);
            assertNotNull(loaded1);
            assertNotNull(loaded2);
            assertEquals("John", loaded1.getProperty("name"));
            assertEquals("Jane", loaded2.getProperty("name"));
        }

        @Test
        void shouldHandleFileStorageDisabled() {
            // given
            persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher, tempDir.toString(), false);
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            profile.setProperty("firstName", "John");

            // when
            persistenceService.save(profile);

            // then
            Path expectedPath = tempDir.resolve(Profile.ITEM_TYPE)
                .resolve(executionContextManager.getCurrentContext().getTenantId())
                .resolve("test_profile.json");
            assertFalse(Files.exists(expectedPath));

            // Verify in-memory operation still works
            Profile loaded = persistenceService.load("test-profile", Profile.class);
            assertNotNull(loaded);
            assertEquals("John", loaded.getProperty("firstName"));
        }

        @Test
        void shouldHandleSpecialCharactersInPaths() {
            // given
            persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher, tempDir.toString(), true);
            Profile profile = new Profile();
            profile.setItemId("test/profile:with?special*chars");
            profile.setProperty("data", "test");

            // when
            persistenceService.save(profile);

            // then
            Path expectedPath = tempDir.resolve(Profile.ITEM_TYPE)
                .resolve(executionContextManager.getCurrentContext().getTenantId())
                .resolve("test_profile_with_special_chars.json");
            assertTrue(Files.exists(expectedPath));

            // Verify item can be loaded
            Profile loaded = persistenceService.load("test/profile:with?special*chars", Profile.class);
            assertNotNull(loaded);
            assertEquals("test", loaded.getProperty("data"));
        }

        @Test
        void shouldDeleteFileWhenItemRemoved() {
            // given
            persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher, tempDir.toString(), true);
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            persistenceService.save(profile);

            Path expectedPath = tempDir.resolve(Profile.ITEM_TYPE)
                .resolve(executionContextManager.getCurrentContext().getTenantId())
                .resolve("test-profile.json");
            assertTrue(Files.exists(expectedPath));

            // when
            persistenceService.remove("test-profile", Profile.class);

            // then
            assertFalse(Files.exists(expectedPath));
            assertNull(persistenceService.load("test-profile", Profile.class));
        }

        @Test
        void shouldCleanupEmptyDirectories() {
            // given
            persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher, tempDir.toString(), true);
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            persistenceService.save(profile);

            Path tenantDir = tempDir.resolve(Profile.ITEM_TYPE)
                .resolve(executionContextManager.getCurrentContext().getTenantId());
            assertTrue(Files.exists(tenantDir));

            // when
            persistenceService.remove("test-profile", Profile.class);

            // then
            assertFalse(Files.exists(tenantDir));
        }

        @Test
        void shouldHandleMultipleTenantsAndTypes() throws IOException {
            // given
            persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher, tempDir.toString(), true);

            // Create items for default tenant
            Profile profile1 = new Profile();
            profile1.setItemId("profile1");
            persistenceService.save(profile1);

            // Create items for another tenant
            executionContextManager.executeAsTenant("tenant2", () -> {
                Profile profile2 = new Profile();
                profile2.setItemId("profile2");
                persistenceService.save(profile2);
                return null;
            });

            // Create different type for default tenant
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            persistenceService.save(item);

            // then
            assertTrue(Files.exists(tempDir.resolve(Profile.ITEM_TYPE)
                .resolve(SYSTEM_TENANT)
                .resolve("profile1.json")));
            assertTrue(Files.exists(tempDir.resolve(Profile.ITEM_TYPE)
                .resolve("tenant2")
                .resolve("profile2.json")));
            assertTrue(Files.exists(tempDir.resolve(TestMetadataItem.ITEM_TYPE)
                .resolve(SYSTEM_TENANT)
                .resolve("item1.json")));
        }

        @Test
        void shouldHandleFileSystemErrors() throws IOException {
            // given
            Path readOnlyDir = tempDir.resolve("readonly");
            try {
                Files.createDirectory(readOnlyDir);
                readOnlyDir.toFile().setReadOnly();

                // Try to create persistence service with read-only directory
                assertThrows(RuntimeException.class, () -> {
                    new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher,
                        readOnlyDir.resolve("data").toString(), true);
                });

            } finally {
                readOnlyDir.toFile().setWritable(true);
            }
        }

        @Test
        void shouldHandleDotsInFileNames() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test.item.with.dots");
            item.setProperty("test", "value");
            persistenceService.save(item);

            // when
            TestMetadataItem loaded = persistenceService.load("test.item.with.dots", TestMetadataItem.class);

            // then
            assertNotNull(loaded);
            assertEquals("value", loaded.getProperty("test"));

            // Verify file was created with underscores
            Path expectedPath = tempDir
                .resolve(TestMetadataItem.ITEM_TYPE)
                .resolve(SYSTEM_TENANT)
                .resolve("test_item_with_dots.json");
            assertTrue(Files.exists(expectedPath), "File should exist at: " + expectedPath);
        }
    }

    @Nested
    class PropertyConditionEvaluatorTests {
        @Test
        void shouldHandleNumericComparisons() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setNumericValue(42.5);
            persistenceService.save(item);

            // Test integer comparison
            Condition intCondition = new Condition();
            intCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            intCondition.setParameter("propertyName", "numericValue");
            intCondition.setParameter("comparisonOperator", "greaterThan");
            intCondition.setParameter("propertyValueInteger", 40);

            // Test double comparison
            Condition doubleCondition = new Condition();
            doubleCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            doubleCondition.setParameter("propertyName", "numericValue");
            doubleCondition.setParameter("comparisonOperator", "lessThan");
            doubleCondition.setParameter("propertyValueDouble", 43.0);

            // when
            boolean intResult = persistenceService.testMatch(intCondition, item);
            boolean doubleResult = persistenceService.testMatch(doubleCondition, item);

            // then
            assertTrue(intResult);
            assertTrue(doubleResult);
        }

        @Test
        void shouldHandleDateComparisons() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            Calendar cal = Calendar.getInstance();
            cal.set(2024, Calendar.JANUARY, 15);
            item.setProperty("date", cal.getTime());
            persistenceService.save(item);

            // Test exact date comparison
            Condition dateCondition = new Condition();
            dateCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            dateCondition.setParameter("propertyName", "properties.date");
            dateCondition.setParameter("comparisonOperator", "equals");
            dateCondition.setParameter("propertyValueDate", cal.getTime());

            // Test date expression
            Calendar futureDate = Calendar.getInstance();
            futureDate.set(2024, Calendar.DECEMBER, 31);
            Condition dateExprCondition = new Condition();
            dateExprCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            dateExprCondition.setParameter("propertyName", "properties.date");
            dateExprCondition.setParameter("comparisonOperator", "lessThan");
            dateExprCondition.setParameter("propertyValueDateExpr", futureDate.getTime());

            // when
            boolean dateResult = persistenceService.testMatch(dateCondition, item);
            boolean dateExprResult = persistenceService.testMatch(dateExprCondition, item);

            // then
            assertTrue(dateResult);
            assertTrue(dateExprResult);
        }

        @Test
        void shouldHandleModernDateTypes() {
            // given - test that modern Java date/time types are properly converted
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item-modern-dates");
            // Use UTC consistently to avoid timezone mismatches
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.set(2024, Calendar.JANUARY, 15, 10, 30, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date testDate = cal.getTime();
            item.setProperty("date", testDate);
            persistenceService.save(item);

            // Test with OffsetDateTime
            java.time.OffsetDateTime offsetDateTime = java.time.OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, java.time.ZoneOffset.UTC);
            Condition offsetDateTimeCondition = new Condition();
            offsetDateTimeCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            offsetDateTimeCondition.setParameter("propertyName", "properties.date");
            offsetDateTimeCondition.setParameter("comparisonOperator", "equals");
            offsetDateTimeCondition.setParameter("propertyValueDate", offsetDateTime);

            // Test with ZonedDateTime
            java.time.ZonedDateTime zonedDateTime = java.time.ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, java.time.ZoneId.of("UTC"));
            Condition zonedDateTimeCondition = new Condition();
            zonedDateTimeCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            zonedDateTimeCondition.setParameter("propertyName", "properties.date");
            zonedDateTimeCondition.setParameter("comparisonOperator", "equals");
            zonedDateTimeCondition.setParameter("propertyValueDate", zonedDateTime);

            // Test with Instant
            java.time.Instant instant = testDate.toInstant();
            Condition instantCondition = new Condition();
            instantCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            instantCondition.setParameter("propertyName", "properties.date");
            instantCondition.setParameter("comparisonOperator", "equals");
            instantCondition.setParameter("propertyValueDate", instant);

            // when
            boolean offsetDateTimeResult = persistenceService.testMatch(offsetDateTimeCondition, item);
            boolean zonedDateTimeResult = persistenceService.testMatch(zonedDateTimeCondition, item);
            boolean instantResult = persistenceService.testMatch(instantCondition, item);

            // then - all modern date types should work correctly
            assertTrue(offsetDateTimeResult, "OffsetDateTime should be properly converted and matched");
            assertTrue(zonedDateTimeResult, "ZonedDateTime should be properly converted and matched");
            assertTrue(instantResult, "Instant should be properly converted and matched");
        }

        @Test
        void shouldHandleLegacyDateFormats() {
            // given - test backward compatibility with migrated datasets from older Unomi/Elasticsearch versions
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item-legacy-dates");
            // Use UTC consistently to avoid timezone mismatches
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.set(2024, Calendar.JANUARY, 15, 10, 30, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date testDate = cal.getTime();
            item.setProperty("date", testDate);
            persistenceService.save(item);

            // Test with epoch milliseconds (common in older Elasticsearch versions)
            long epochMillis = testDate.getTime();
            Condition epochMillisCondition = new Condition();
            epochMillisCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            epochMillisCondition.setParameter("propertyName", "properties.date");
            epochMillisCondition.setParameter("comparisonOperator", "equals");
            epochMillisCondition.setParameter("propertyValueDate", String.valueOf(epochMillis));

            // Test with ISO-8601 string format (case-insensitive - legacy systems might use lowercase)
            String isoDateLowercase = "2024-01-15t10:30:00z";
            Condition isoLowercaseCondition = new Condition();
            isoLowercaseCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            isoLowercaseCondition.setParameter("propertyName", "properties.date");
            isoLowercaseCondition.setParameter("comparisonOperator", "equals");
            isoLowercaseCondition.setParameter("propertyValueDate", isoDateLowercase);

            // when
            boolean epochMillisResult = persistenceService.testMatch(epochMillisCondition, item);
            boolean isoLowercaseResult = persistenceService.testMatch(isoLowercaseCondition, item);

            // then - all legacy formats should work correctly
            assertTrue(epochMillisResult, "Epoch milliseconds string should be properly parsed and matched");
            assertTrue(isoLowercaseResult, "Case-insensitive ISO date format should be properly parsed and matched");
        }

        @Test
        void shouldHandleCollectionOperations() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setTags(new HashSet<>(Arrays.asList("tag1", "tag2", "tag3")));
            persistenceService.save(item);

            // Test hasSomeOf
            Condition hasSomeOfCondition = new Condition();
            hasSomeOfCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            hasSomeOfCondition.setParameter("propertyName", "tags");
            hasSomeOfCondition.setParameter("comparisonOperator", "hasSomeOf");
            hasSomeOfCondition.setParameter("propertyValues", Arrays.asList("tag1", "tag4"));

            // Test hasNoneOf
            Condition hasNoneOfCondition = new Condition();
            hasNoneOfCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            hasNoneOfCondition.setParameter("propertyName", "tags");
            hasNoneOfCondition.setParameter("comparisonOperator", "hasNoneOf");
            hasNoneOfCondition.setParameter("propertyValues", Arrays.asList("tag4", "tag5"));

            // Test all
            Condition allCondition = new Condition();
            allCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            allCondition.setParameter("propertyName", "tags");
            allCondition.setParameter("comparisonOperator", "all");
            allCondition.setParameter("propertyValues", Arrays.asList("tag1", "tag2"));

            // when
            boolean hasSomeOfResult = persistenceService.testMatch(hasSomeOfCondition, item);
            boolean hasNoneOfResult = persistenceService.testMatch(hasNoneOfCondition, item);
            boolean allResult = persistenceService.testMatch(allCondition, item);

            // then
            assertTrue(hasSomeOfResult);
            assertTrue(hasNoneOfResult);
            assertTrue(allResult);
        }

        @Test
        void shouldHandleGeoDistanceCalculations() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            Map<String, Double> location = new HashMap<>();
            location.put("lat", 40.7128);
            location.put("lon", -74.0060);
            item.setProperty("location", location);
            persistenceService.save(item);

            // Test distance condition
            Condition distanceCondition = new Condition();
            distanceCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            distanceCondition.setParameter("propertyName", "properties.location");
            distanceCondition.setParameter("comparisonOperator", "distance");
            distanceCondition.setParameter("unit", "km");
            distanceCondition.setParameter("distance", 10.0);
            distanceCondition.setParameter("center", "40.7128,-74.0060");

            // when
            boolean result = persistenceService.testMatch(distanceCondition, item);

            // then
            assertTrue(result);
        }

        @Test
        void shouldHandleDayComparisons() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            Calendar cal = Calendar.getInstance();
            cal.set(2024, Calendar.JANUARY, 15, 14, 30, 0); // 2:30 PM
            item.setProperty("timestamp", cal.getTime());
            persistenceService.save(item);

            // Test isDay condition
            Condition isDayCondition = new Condition();
            isDayCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            isDayCondition.setParameter("propertyName", "properties.timestamp");
            isDayCondition.setParameter("comparisonOperator", "isDay");
            isDayCondition.setParameter("propertyValueDate", cal.getTime());

            // Test isNotDay condition with different day
            cal.add(Calendar.DAY_OF_MONTH, 1);
            Condition isNotDayCondition = new Condition();
            isNotDayCondition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            isNotDayCondition.setParameter("propertyName", "properties.timestamp");
            isNotDayCondition.setParameter("comparisonOperator", "isNotDay");
            isNotDayCondition.setParameter("propertyValueDate", cal.getTime());

            // when
            boolean isDayResult = persistenceService.testMatch(isDayCondition, item);
            boolean isNotDayResult = persistenceService.testMatch(isNotDayCondition, item);

            // then
            assertTrue(isDayResult);
            assertTrue(isNotDayResult);
        }
    }

    @Nested
    class ScriptExecutionTests {

        @Test
        public void testUpdatePastEventOccurrencesScript() {
            // Create a test profile
            Profile profile = new Profile();
            profile.setItemId("test-profile-id");
            persistenceService.save(profile);

            // Create script parameters
            Map<String, Object> pastEventKeyValue = new HashMap<>();
            pastEventKeyValue.put("pastEventKey", "test-event");
            pastEventKeyValue.put("valueToAdd", 5L);

            Map<String, Object> scriptParams = new HashMap<>();
            scriptParams.put(profile.getItemId(), pastEventKeyValue);

            // Execute script
            boolean result = persistenceService.updateWithScript(profile, Profile.class,
                    "updatePastEventOccurences", scriptParams);
            assertTrue(result);

            // Verify the update
            Profile updatedProfile = persistenceService.load(profile.getItemId(), Profile.class);
            assertNotNull(updatedProfile);

            @SuppressWarnings("unchecked")
            Map<String, Object> systemProperties = updatedProfile.getSystemProperties();
            assertNotNull(systemProperties);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pastEvents = (List<Map<String, Object>>) systemProperties.get("pastEvents");
            assertNotNull(pastEvents);
            assertEquals(1, pastEvents.size());

            Map<String, Object> pastEvent = pastEvents.get(0);
            assertEquals("test-event", pastEvent.get("key"));
            assertEquals(5L, pastEvent.get("count"));
        }

        @Test
        public void testUpdateProfileIdScript() {
            // Create a test session
            Session session = new Session();
            session.setItemId("test-session-id");

            Profile profile = new Profile();
            profile.setItemId("old-profile-id");
            session.setProfile(profile);

            persistenceService.save(session);

            // Create script parameters
            Map<String, Object> scriptParams = new HashMap<>();
            scriptParams.put("profileId", "new-profile-id");

            // Execute script
            boolean result = persistenceService.updateWithScript(session, Session.class,
                    "updateProfileId", scriptParams);
            assertTrue(result);

            // Verify the update
            Session updatedSession = persistenceService.load(session.getItemId(), Session.class);
            assertNotNull(updatedSession);
            assertEquals("new-profile-id", updatedSession.getProfileId());
            assertEquals("new-profile-id", updatedSession.getProfile().getItemId());
        }

        @Test
        public void testUpdateWithQueryAndScript() {
            // Create test profiles
            Profile profile1 = new Profile();
            profile1.setItemId("test-profile-1");
            persistenceService.save(profile1);

            Profile profile2 = new Profile();
            profile2.setItemId("test-profile-2");
            persistenceService.save(profile2);

            // Create script parameters for both profiles
            Map<String, Object> pastEventKeyValue1 = new HashMap<>();
            pastEventKeyValue1.put("pastEventKey", "test-event");
            pastEventKeyValue1.put("valueToAdd", 5L);

            Map<String, Object> pastEventKeyValue2 = new HashMap<>();
            pastEventKeyValue2.put("pastEventKey", "test-event");
            pastEventKeyValue2.put("valueToAdd", 3L);

            Map<String, Object> scriptParams1 = new HashMap<>();
            scriptParams1.put(profile1.getItemId(), pastEventKeyValue1);

            Map<String, Object> scriptParams2 = new HashMap<>();
            scriptParams2.put(profile2.getItemId(), pastEventKeyValue2);

            // Create conditions that match each profile
            Condition condition1 = new Condition();
            ConditionType conditionType = new ConditionType();
            conditionType.setItemId("matchAllCondition");
            conditionType.setQueryBuilder("matchAllConditionQueryBuilder");
            conditionType.setConditionEvaluator("matchAllConditionEvaluator");
            condition1.setConditionType(conditionType);

            // Execute scripts
            String[] scripts = new String[]{"updatePastEventOccurences"};
            Map<String, Object>[] scriptParamsArray = new Map[]{scriptParams1};
            Condition[] conditions = new Condition[]{condition1};

            boolean result = persistenceService.updateWithQueryAndScript(Profile.class, scripts, scriptParamsArray, conditions);
            assertTrue(result);

            // Verify updates
            Profile updatedProfile1 = persistenceService.load(profile1.getItemId(), Profile.class);
            assertNotNull(updatedProfile1);

            @SuppressWarnings("unchecked")
            Map<String, Object> systemProperties1 = updatedProfile1.getSystemProperties();
            assertNotNull(systemProperties1);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pastEvents1 = (List<Map<String, Object>>) systemProperties1.get("pastEvents");
            assertNotNull(pastEvents1);
            assertEquals(1, pastEvents1.size());
            assertEquals(5L, pastEvents1.get(0).get("count"));
        }

        @Test
        public void testStoreScripts() {
            Map<String, String> scripts = new HashMap<>();
            scripts.put("test-script", "ctx._source.test = params.value");

            // Store scripts should always return true for in-memory implementation
            assertTrue(persistenceService.storeScripts(scripts));
        }

        @Test
        void shouldHandleVersioningInScriptUpdates() {
            // Create a test profile
            Profile profile = new Profile();
            profile.setItemId("test-profile-version");
            persistenceService.save(profile);
            assertEquals(1L, profile.getVersion());

            // Create script parameters
            Map<String, Object> pastEventKeyValue = new HashMap<>();
            pastEventKeyValue.put("pastEventKey", "test-event");
            pastEventKeyValue.put("valueToAdd", 5L);

            Map<String, Object> scriptParams = new HashMap<>();
            scriptParams.put(profile.getItemId(), pastEventKeyValue);

            // Execute script update
            boolean result = persistenceService.updateWithScript(profile, Profile.class,
                    "updatePastEventOccurences", scriptParams);
            assertTrue(result);

            // Verify version was incremented
            Profile updatedProfile = persistenceService.load(profile.getItemId(), Profile.class);
            assertNotNull(updatedProfile);
            assertEquals(2L, updatedProfile.getVersion());
        }

        @Test
        void shouldHandleVersioningInQueryAndScriptUpdates() {
            // Create test profiles
            Profile profile1 = new Profile();
            profile1.setItemId("test-profile-version-1");
            persistenceService.save(profile1);
            assertEquals(1L, profile1.getVersion());

            Profile profile2 = new Profile();
            profile2.setItemId("test-profile-version-2");
            persistenceService.save(profile2);
            assertEquals(1L, profile2.getVersion());

            // Create script parameters
            Map<String, Object> pastEventKeyValue = new HashMap<>();
            pastEventKeyValue.put("pastEventKey", "test-event");
            pastEventKeyValue.put("valueToAdd", 5L);

            Map<String, Object> scriptParams = new HashMap<>();
            scriptParams.put(profile1.getItemId(), pastEventKeyValue);
            scriptParams.put(profile2.getItemId(), pastEventKeyValue);

            // Create condition that matches both profiles
            Condition condition = new Condition();
            ConditionType conditionType = new ConditionType();
            conditionType.setItemId("matchAllCondition");
            conditionType.setQueryBuilder("matchAllConditionQueryBuilder");
            conditionType.setConditionEvaluator("matchAllConditionEvaluator");
            condition.setConditionType(conditionType);

            // Execute script update
            String[] scripts = new String[]{"updatePastEventOccurences"};
            Map<String, Object>[] scriptParamsArray = new Map[]{scriptParams};
            Condition[] conditions = new Condition[]{condition};

            boolean result = persistenceService.updateWithQueryAndScript(Profile.class, scripts, scriptParamsArray, conditions);
            assertTrue(result);

            // Verify versions were incremented
            Profile updatedProfile1 = persistenceService.load(profile1.getItemId(), Profile.class);
            Profile updatedProfile2 = persistenceService.load(profile2.getItemId(), Profile.class);
            assertNotNull(updatedProfile1);
            assertNotNull(updatedProfile2);
            assertEquals(2L, updatedProfile1.getVersion());
            assertEquals(2L, updatedProfile2.getVersion());
        }
    }

    @Nested
    class PropertyMappingTests {
        @Test
        void shouldSetAndGetPropertyMapping() {
            // Create a property type with some test data
            PropertyType propertyType = new PropertyType();
            propertyType.setMetadata(new Metadata());
            propertyType.getMetadata().setId("test-metadata-id");
            propertyType.getMetadata().setName("Test Property");
            propertyType.getMetadata().setDescription("A test property");
            propertyType.setItemId("testProperty");
            propertyType.setValueTypeId("string");
            propertyType.setTarget("profiles");

            persistenceService.setPropertyMapping(propertyType, "testItemType");

            // Verify the mapping structure
            Map<String, Map<String, Object>> mapping = persistenceService.getPropertiesMapping("testItemType");
            assertNotNull(mapping);
            assertTrue(mapping.containsKey("properties"));

            // Get and verify the property mapping
            Map<String, Object> propertyMapping = persistenceService.getPropertyMapping("testProperty", "testItemType");
            assertNotNull(propertyMapping);

            // Verify the converted map contains all the expected fields
            assertEquals("testProperty", propertyMapping.get("itemId"));
            assertEquals("string", propertyMapping.get("type")); // valueTypeId is mapped to type at JSON serialization
            assertEquals("profiles", propertyMapping.get("target"));

            // Verify metadata was properly converted
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) propertyMapping.get("metadata");
            assertNotNull(metadata);
            assertEquals("test-metadata-id", metadata.get("id"));
            assertEquals("Test Property", metadata.get("name"));
            assertEquals("A test property", metadata.get("description"));
        }

        @Test
        void shouldHandleNonExistentPropertyMapping() {
            assertNull(persistenceService.getPropertyMapping("nonexistent", "testItemType"));
            assertNull(persistenceService.getPropertiesMapping("nonexistentType"));
        }
    }

    @Nested
    class RangeQueryTests {
        @Test
        void shouldHandleNumericRangeQueries() {
            // given
            List<TestMetadataItem> items = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + i);
                item.setNumericValue((double) i);
                items.add(item);
                persistenceService.save(item);
            }

            // when - query with both bounds (retry until items are available)
            PartialList<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.rangeQuery(
                    "numericValue", "2", "4", "numericValue:asc", TestMetadataItem.class, 0, -1),
                3
            );

            // then
            assertEquals(3, results.getList().size());
            assertEquals(2.0, results.getList().get(0).getNumericValue());
            assertEquals(3.0, results.getList().get(1).getNumericValue());
            assertEquals(4.0, results.getList().get(2).getNumericValue());

            // when - query with lower bound only (retry until items are available)
            results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.rangeQuery(
                    "numericValue", "4", null, "numericValue:asc", TestMetadataItem.class, 0, -1),
                2
            );

            // then
            assertEquals(2, results.getList().size());
            assertEquals(4.0, results.getList().get(0).getNumericValue());
            assertEquals(5.0, results.getList().get(1).getNumericValue());

            // when - query with upper bound only (retry until items are available)
            results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.rangeQuery(
                    "numericValue", null, "2", "numericValue:asc", TestMetadataItem.class, 0, -1),
                2
            );

            // then
            assertEquals(2, results.getList().size());
            assertEquals(1.0, results.getList().get(0).getNumericValue());
            assertEquals(2.0, results.getList().get(1).getNumericValue());
        }

        @Test
        void shouldHandleStringRangeQueries() {
            // given
            List<TestMetadataItem> items = new ArrayList<>();
            for (char c = 'A'; c <= 'E'; c++) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + c);
                item.setName(String.valueOf(c));
                items.add(item);
                persistenceService.save(item);
            }

            // when - query with both bounds (retry until items are available)
            PartialList<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.rangeQuery(
                    "name", "B", "D", "name:asc", TestMetadataItem.class, 0, -1),
                3
            );

            // then
            assertEquals(3, results.getList().size());
            assertEquals("B", results.getList().get(0).getName());
            assertEquals("C", results.getList().get(1).getName());
            assertEquals("D", results.getList().get(2).getName());

            // when - query with lower bound only (retry until items are available)
            results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.rangeQuery(
                    "name", "D", null, "name:asc", TestMetadataItem.class, 0, -1),
                2
            );

            // then
            assertEquals(2, results.getList().size());
            assertEquals("D", results.getList().get(0).getName());
            assertEquals("E", results.getList().get(1).getName());

            // when - query with upper bound only (retry until items are available)
            results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.rangeQuery(
                    "name", null, "B", "name:asc", TestMetadataItem.class, 0, -1),
                2
            );

            // then
            assertEquals(2, results.getList().size());
            assertEquals("A", results.getList().get(0).getName());
            assertEquals("B", results.getList().get(1).getName());
        }

        @Test
        void shouldHandlePaginationInRangeQueries() {
            // given
            List<TestMetadataItem> items = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + i);
                item.setNumericValue((double) i);
                items.add(item);
                persistenceService.save(item);
            }

            // when - first page (retry until items are available)
            PartialList<TestMetadataItem> page1 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.rangeQuery(
                    "numericValue", "1", "5", "numericValue:asc", TestMetadataItem.class, 0, 2),
                2
            );

            // then
            assertEquals(2, page1.getList().size());
            assertEquals(1.0, page1.getList().get(0).getNumericValue());
            assertEquals(2.0, page1.getList().get(1).getNumericValue());

            // when - second page (retry until items are available)
            PartialList<TestMetadataItem> page2 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.rangeQuery(
                    "numericValue", "1", "5", "numericValue:asc", TestMetadataItem.class, 2, 2),
                2
            );

            // then
            assertEquals(2, page2.getList().size());
            assertEquals(3.0, page2.getList().get(0).getNumericValue());
            assertEquals(4.0, page2.getList().get(1).getNumericValue());

            // when - last page (retry until items are available)
            PartialList<TestMetadataItem> page3 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.rangeQuery(
                    "numericValue", "1", "5", "numericValue:asc", TestMetadataItem.class, 4, 2),
                1
            );

            // then
            assertEquals(1, page3.getList().size());
            assertEquals(5.0, page3.getList().get(0).getNumericValue());
        }

        @Test
        void shouldHandleNonExistentFieldInRangeQueries() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setNumericValue(1.0);
            persistenceService.save(item);

            // when
            PartialList<TestMetadataItem> results = persistenceService.rangeQuery(
                "nonexistentField", "1", "5", "numericValue:asc", TestMetadataItem.class, 0, -1);

            // then
            assertEquals(0, results.getList().size());
        }

        @Test
        void shouldHandleInvalidRangeValues() {
            // given
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setNumericValue(1.0);
            persistenceService.save(item);

            // when - invalid numeric range
            PartialList<TestMetadataItem> results = persistenceService.rangeQuery(
                "numericValue", "invalid", "5", "numericValue:asc", TestMetadataItem.class, 0, -1);

            // then
            assertEquals(0, results.getList().size());
        }
    }

    @Nested
    class UpdateOperationTests {
        @Test
        void shouldUpdateItemWithSourceMap() {
            // Create and save initial item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setName("Initial Name");
            item.setNumericValue(1.0);
            persistenceService.save(item);

            // Create update map
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", "Updated Name");
            updates.put("numericValue", 2.0);

            // Perform update
            boolean result = persistenceService.update(item, null, TestMetadataItem.class, updates);
            assertTrue(result);

            // Verify updates
            TestMetadataItem updated = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            assertEquals("Updated Name", updated.getName());
            assertEquals(2.0, updated.getNumericValue());
            assertEquals(2, updated.getVersion());
        }

        @Test
        void shouldUpdateItemWithSingleProperty() {
            // Create and save initial item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setName("Initial Name");
            persistenceService.save(item);

            // Perform update
            boolean result = persistenceService.update(item, null, TestMetadataItem.class, "name", "Updated Name");
            assertTrue(result);

            // Verify update
            TestMetadataItem updated = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            assertEquals("Updated Name", updated.getName());
            assertEquals(2, updated.getVersion());
        }

        @Test
        void shouldUpdateItemWithSourceMapAndNoScriptCall() {
            // Create and save initial item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setName("Initial Name");
            persistenceService.save(item);

            // Create update map
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", "Updated Name");

            // Perform update
            boolean result = persistenceService.update(item, null, TestMetadataItem.class, updates, true);
            assertTrue(result);

            // Verify update
            TestMetadataItem updated = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            assertEquals("Updated Name", updated.getName());
            assertEquals(2, updated.getVersion());
        }

        @Test
        void shouldUpdateMultipleItems() {
            // Create and save initial items
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("test-item-1");
            item1.setName("Item 1");
            persistenceService.save(item1);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("test-item-2");
            item2.setName("Item 2");
            persistenceService.save(item2);

            // Create updates map
            Map<Item, Map> updates = new HashMap<>();
            updates.put(item1, Collections.singletonMap("name", "Updated Item 1"));
            updates.put(item2, Collections.singletonMap("name", "Updated Item 2"));

            // Perform updates
            List<String> failedUpdates = persistenceService.update(updates, null, TestMetadataItem.class);
            assertTrue(failedUpdates.isEmpty());

            // Verify updates
            TestMetadataItem updated1 = persistenceService.load(item1.getItemId(), TestMetadataItem.class);
            TestMetadataItem updated2 = persistenceService.load(item2.getItemId(), TestMetadataItem.class);
            assertEquals("Updated Item 1", updated1.getName());
            assertEquals("Updated Item 2", updated2.getName());
            assertEquals(2, updated1.getVersion());
            assertEquals(2, updated2.getVersion());
        }

        @Test
        void shouldHandleNonExistentItem() {
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("non-existent");

            Map<String, Object> updates = new HashMap<>();
            updates.put("name", "Updated Name");

            boolean result = persistenceService.update(item, null, TestMetadataItem.class, updates);
            assertFalse(result);
        }

        @Test
        void shouldHandleInvalidPropertyName() {
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setName("Initial Name");
            persistenceService.save(item);

            boolean result = persistenceService.update(item, null, TestMetadataItem.class, "nonExistentProperty", "value");
            assertFalse(result);

            TestMetadataItem unchanged = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            assertEquals("Initial Name", unchanged.getName());
            assertEquals(item.getVersion(), unchanged.getVersion());
        }
    }

    @Nested
    class FileStorageConcurrencyTests {
        @Test
        void shouldHandleConcurrentFileOperations() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(threadCount);
            List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

            // Start all threads but wait for them to be ready
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                new Thread(() -> {
                    try {
                        // Wait for all threads to be ready before starting operations
                        startLatch.await();
                        
                        TestMetadataItem item = new TestMetadataItem();
                        item.setItemId("concurrent-item-" + index);
                        persistenceService.save(item);
                        persistenceService.load(item.getItemId(), TestMetadataItem.class);
                        persistenceService.remove(item.getItemId(), TestMetadataItem.class);
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        completionLatch.countDown();
                    }
                }).start();
            }

            // Release all threads to start operations concurrently
            // The startLatch.await() in each thread ensures all threads are ready before proceeding
            startLatch.countDown();
            
            // Wait for all threads to complete with a longer timeout
            boolean completed = completionLatch.await(10, TimeUnit.SECONDS);
            assertTrue(completed, "Test timed out - not all threads completed within 10 seconds");
            assertTrue(exceptions.isEmpty(), "Concurrent operations should not throw exceptions. Found: " + exceptions);
        }

    }

    @Nested
    class FieldAccessPatternTests {
        private TestMetadataItem createTestItem(String id, Map<String, Object> properties) {
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId(id);
            item.setProperties(properties);
            return item;
        }

        @Test
        void shouldHandleDotNotationAccess() {
            // Setup test data
            Map<String, Object> nestedMap = new HashMap<>();
            nestedMap.put("simple", "value");
            nestedMap.put("nested.key", "nested-value");

            Map<String, Object> properties = new HashMap<>();
            properties.put("map", nestedMap);

            TestMetadataItem item = createTestItem("test1", properties);
            persistenceService.save(item);

            // Test simple dot notation
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            Map<String, Object> loadedProps = loaded.getProperties();
            Map<String, Object> loadedMap = (Map<String, Object>) loadedProps.get("map");

            assertEquals("value", loadedMap.get("simple"));
            assertEquals("nested-value", loadedMap.get("nested.key"));
        }

        @Test
        void shouldHandleBackslashEscapedDots() {
            // Setup test data
            Map<String, Object> properties = new HashMap<>();
            properties.put("user.name", "test-user");
            properties.put("complex.key.value", "complex-value");

            TestMetadataItem item = createTestItem("test2", properties);
            persistenceService.save(item);

            // Test accessing properties with dots
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            Map<String, Object> loadedProps = loaded.getProperties();

            assertEquals("test-user", loadedProps.get("user.name"));
            assertEquals("complex-value", loadedProps.get("complex.key.value"));
        }

        @Test
        void shouldHandleMixedNotationAccess() {
            // Setup test data
            Map<String, Object> nestedMap = new HashMap<>();
            nestedMap.put("key.with.dots", "dotted-value");

            List<String> array = Arrays.asList("first", "second", "third");

            Map<String, Object> properties = new HashMap<>();
            properties.put("map", nestedMap);
            properties.put("array", array);

            TestMetadataItem item = createTestItem("test3", properties);
            persistenceService.save(item);

            // Test accessing nested properties
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            Map<String, Object> loadedProps = loaded.getProperties();
            Map<String, Object> loadedMap = (Map<String, Object>) loadedProps.get("map");
            List<String> loadedArray = (List<String>) loadedProps.get("array");

            assertEquals("dotted-value", loadedMap.get("key.with.dots"));
            assertEquals("second", loadedArray.get(1));
        }

        @Test
        void shouldHandleNullAndNonExistentFields() {
            // Setup test data
            Map<String, Object> properties = new HashMap<>();
            properties.put("nullValue", null);

            TestMetadataItem item = createTestItem("test4", properties);
            persistenceService.save(item);

            // Test null and non-existent fields
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            Map<String, Object> loadedProps = loaded.getProperties();

            assertNull(loadedProps.get("nullValue"));
            assertNull(loadedProps.get("nonexistent.field"));
        }

        @Test
        void shouldHandleArrayAccess() {
            // Setup test data
            List<Map<String, Object>> array = new ArrayList<>();
            Map<String, Object> element = new HashMap<>();
            element.put("key.with.dots", "array-value");
            array.add(element);

            Map<String, Object> properties = new HashMap<>();
            properties.put("array", array);

            TestMetadataItem item = createTestItem("test5", properties);
            persistenceService.save(item);

            // Test array access
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            Map<String, Object> loadedProps = loaded.getProperties();
            List<Map<String, Object>> loadedArray = (List<Map<String, Object>>) loadedProps.get("array");
            Map<String, Object> loadedElement = loadedArray.get(0);

            assertEquals("array-value", loadedElement.get("key.with.dots"));
        }
    }

    @Nested
    class SortingOperationsTests {
        @Test
        void shouldSortQueryResultsBySimpleProperty() {
            // given
            List<TestMetadataItem> items = new ArrayList<>();
            for (int i = 3; i >= 1; i--) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + i);
                item.setProperty("simple", "value");
                item.setName("Name" + i);
                items.add(item);
                persistenceService.save(item);
            }

            // when - ascending order (retry until items are available)
            List<TestMetadataItem> ascResults = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.simple", "value", "name:asc", TestMetadataItem.class),
                3
            );

            // then
            assertEquals(3, ascResults.size());
            assertEquals("Name1", ascResults.get(0).getName());
            assertEquals("Name2", ascResults.get(1).getName());
            assertEquals("Name3", ascResults.get(2).getName());

            // when - descending order (retry until items are available)
            List<TestMetadataItem> descResults = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.simple", "value", "name:desc", TestMetadataItem.class),
                3
            );

            // then
            assertEquals(3, descResults.size());
            assertEquals("Name3", descResults.get(0).getName());
            assertEquals("Name2", descResults.get(1).getName());
            assertEquals("Name1", descResults.get(2).getName());
        }

        @Test
        void shouldSortQueryResultsByNumericProperty() {
            // given
            List<TestMetadataItem> items = new ArrayList<>();
            for (int i = 3; i >= 1; i--) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + i);
                item.setProperty("simple", "value");
                item.setNumericValue((double) i);
                items.add(item);
                persistenceService.save(item);
            }

            // when - ascending order (retry until items are available)
            List<TestMetadataItem> ascResults = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.simple", "value", "numericValue:asc", TestMetadataItem.class),
                3
            );

            // then
            assertEquals(3, ascResults.size());
            assertEquals(1.0, ascResults.get(0).getNumericValue());
            assertEquals(2.0, ascResults.get(1).getNumericValue());
            assertEquals(3.0, ascResults.get(2).getNumericValue());

            // when - descending order (retry until items are available)
            List<TestMetadataItem> descResults = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.simple", "value", "numericValue:desc", TestMetadataItem.class),
                3
            );

            // then
            assertEquals(3, descResults.size());
            assertEquals(3.0, descResults.get(0).getNumericValue());
            assertEquals(2.0, descResults.get(1).getNumericValue());
            assertEquals(1.0, descResults.get(2).getNumericValue());
        }

        @Test
        void shouldHandleNullValuesInSorting() {
            // given
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setName("Name1");
            item1.setProperty("simple", "value");
            item1.setNumericValue(1.0);
            persistenceService.save(item1);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setName("Name2");
            item2.setProperty("simple", "value");
            item2.setNumericValue(null);
            persistenceService.save(item2);

            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setName("Name3");
            item3.setProperty("simple", "value");
            item3.setNumericValue(3.0);
            persistenceService.save(item3);

            // when - ascending order (retry until items are available)
            List<TestMetadataItem> ascResults = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.simple", "value", "numericValue:asc", TestMetadataItem.class),
                3
            );

            // then
            assertEquals(3, ascResults.size());
            assertNull(ascResults.get(0).getNumericValue());
            assertEquals(1.0, ascResults.get(1).getNumericValue());
            assertEquals(3.0, ascResults.get(2).getNumericValue());

            // when - descending order (retry until items are available)
            List<TestMetadataItem> descResults = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.simple", "value", "numericValue:desc", TestMetadataItem.class),
                3
            );

            // then
            assertEquals(3, descResults.size());
            assertEquals(3.0, descResults.get(0).getNumericValue());
            assertEquals(1.0, descResults.get(1).getNumericValue());
            assertNull(descResults.get(2).getNumericValue());
        }

        @Test
        void shouldSortQueryResultsWithCondition() {
            // given
            List<TestMetadataItem> items = new ArrayList<>();
            for (int i = 3; i >= 1; i--) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + i);
                item.setName("Name" + i);
                item.setProperty("active", i % 2 == 0);
                items.add(item);
                persistenceService.save(item);
            }

            // Create condition for active items
            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            condition.setParameter("propertyName", "properties.active");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", true);

            // when - retry until items are available
            List<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query(condition, "name:asc", TestMetadataItem.class),
                1
            );

            // then
            assertEquals(1, results.size());
            assertEquals("Name2", results.get(0).getName());
        }

        @Test
        void shouldSortPaginatedQueryResults() {
            // given
            List<TestMetadataItem> items = new ArrayList<>();
            for (int i = 5; i >= 1; i--) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + i);
                item.setProperty("simple", "value");
                item.setName("Name" + i);
                items.add(item);
                persistenceService.save(item);
            }

            // when - first page (retry until items are available)
            PartialList<TestMetadataItem> page1 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.simple", "value", "name:asc", TestMetadataItem.class, 0, 2),
                2
            );

            // then
            assertEquals(2, page1.getList().size());
            assertEquals("Name1", page1.getList().get(0).getName());
            assertEquals("Name2", page1.getList().get(1).getName());

            // when - second page (retry until items are available)
            PartialList<TestMetadataItem> page2 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.simple", "value", "name:asc", TestMetadataItem.class, 2, 2),
                2
            );

            // then
            assertEquals(2, page2.getList().size());
            assertEquals("Name3", page2.getList().get(0).getName());
            assertEquals("Name4", page2.getList().get(1).getName());

            // when - last page (retry until items are available)
            PartialList<TestMetadataItem> page3 = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.query("properties.simple", "value", "name:asc", TestMetadataItem.class, 4, 2),
                1
            );

            // then
            assertEquals(1, page3.getList().size());
            assertEquals("Name5", page3.getList().get(0).getName());
        }
    }

    @Nested
    class IndexAndPurgeOperations {

        @Test
        void shouldPurgeItemsByDate() {
            // Create items with different creation dates
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setScope("scope1");
            Calendar cal1 = Calendar.getInstance();
            cal1.add(Calendar.DAY_OF_YEAR, -10); // 10 days ago
            item1.setCreationDate(cal1.getTime());
            persistenceService.save(item1);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setScope("scope1");
            Calendar cal2 = Calendar.getInstance();
            cal2.add(Calendar.DAY_OF_YEAR, -5); // 5 days ago
            item2.setCreationDate(cal2.getTime());
            persistenceService.save(item2);

            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setScope("scope1");
            Calendar cal3 = Calendar.getInstance();
            cal3.add(Calendar.DAY_OF_YEAR, -1); // 1 day ago
            item3.setCreationDate(cal3.getTime());
            persistenceService.save(item3);

            // Purge items older than 7 days
            Calendar purgeDate = Calendar.getInstance();
            purgeDate.add(Calendar.DAY_OF_YEAR, -7);
            persistenceService.purge(purgeDate.getTime());

            // Check that only item1 was purged
            assertNull(persistenceService.load("item1", TestMetadataItem.class));
            assertNotNull(persistenceService.load("item2", TestMetadataItem.class));
            assertNotNull(persistenceService.load("item3", TestMetadataItem.class));
        }

        @Test
        void shouldPurgeItemsByScope() {
            // Create items with different scopes
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setScope("scope1");
            persistenceService.save(item1);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setScope("scope2");
            persistenceService.save(item2);

            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setScope("scope1");
            persistenceService.save(item3);

            // Purge items with scope1
            persistenceService.purge("scope1");

            // Check that only scope1 items were purged
            assertNull(persistenceService.load("item1", TestMetadataItem.class));
            assertNotNull(persistenceService.load("item2", TestMetadataItem.class));
            assertNull(persistenceService.load("item3", TestMetadataItem.class));
        }

        @Test
        void shouldCreateAndRemoveIndex() {
            // Create an index
            boolean created = persistenceService.createIndex(TestMetadataItem.ITEM_TYPE);

            // Verify the index was created
            assertTrue(created);

            // Create items with the test item type
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            persistenceService.save(item1);

            // Remove the index
            boolean removed = persistenceService.removeIndex(TestMetadataItem.ITEM_TYPE);

            // Verify the index was removed
            assertTrue(removed);

            // Check that only the item with the specified item type was removed
            assertNull(persistenceService.load("item1", TestMetadataItem.class));
        }

        @Test
        void shouldCreateMapping() {
            // Create a mapping
            String itemType = "testItemType";
            String mappingConfig = "{\"properties\":{\"field1\":{\"type\":\"keyword\"},\"field2\":{\"type\":\"text\"}}}";

            // Verify no exception is thrown
            assertDoesNotThrow(() -> persistenceService.createMapping(itemType, mappingConfig));

            // Verify the mapping was stored
            Map<String, Map<String, Object>> mapping = persistenceService.getPropertiesMapping(itemType);
            assertNotNull(mapping, "Mapping should not be null");

            // Verify mapping contains expected properties structure
            assertTrue(mapping.containsKey("properties"), "Mapping should contain 'properties' key");
        }

        @Test
        void shouldHandleNullArgumentsGracefully() {
            // Test purge with null date
            assertDoesNotThrow(() -> persistenceService.purge((Date) null));

            // Test purge with null scope
            assertDoesNotThrow(() -> persistenceService.purge((String) null));

            // Test refresh index with null class
            assertDoesNotThrow(() -> persistenceService.refreshIndex(null, null));

            // Test create index with null item type
            assertFalse(persistenceService.createIndex(null));

            // Test remove index with null item type
            assertFalse(persistenceService.removeIndex(null));

            // Test create mapping with null arguments
            assertThrows(IllegalArgumentException.class, () -> persistenceService.createMapping(null, "config"));
            assertThrows(IllegalArgumentException.class, () -> persistenceService.createMapping("type", null));
        }

        @Test
        void shouldPurgeTimeBasedItems() {
            // Create items with different creation dates
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            Calendar cal1 = Calendar.getInstance();
            cal1.add(Calendar.DAY_OF_YEAR, -30); // 30 days ago
            item1.setCreationDate(cal1.getTime());
            persistenceService.save(item1);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            Calendar cal2 = Calendar.getInstance();
            cal2.add(Calendar.DAY_OF_YEAR, -15); // 15 days ago
            item2.setCreationDate(cal2.getTime());
            persistenceService.save(item2);

            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            Calendar cal3 = Calendar.getInstance();
            cal3.add(Calendar.DAY_OF_YEAR, -5); // 5 days ago
            item3.setCreationDate(cal3.getTime());
            persistenceService.save(item3);

            // Purge items older than 10 days
            persistenceService.purgeTimeBasedItems(10, TestMetadataItem.class);

            // Check that items older than 10 days were purged
            assertNull(persistenceService.load("item1", TestMetadataItem.class));
            assertNull(persistenceService.load("item2", TestMetadataItem.class));
            assertNotNull(persistenceService.load("item3", TestMetadataItem.class));
        }

        @Test
        void shouldHandleRefreshOperationsSafely() {
            // Test refresh
            assertDoesNotThrow(() -> persistenceService.refresh());

            // Test refresh index with a specific class
            assertDoesNotThrow(() -> persistenceService.refreshIndex(TestMetadataItem.class, new Date()));
        }

        @Test
        void shouldRespectTenantIsolationInPurgeOperations() throws Exception {
            // Create items for different tenants
            TestMetadataItem itemTenant1 = executionContextManager.executeAsTenant("tenant1", () -> {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item1");
                item.setScope("scope1");
                persistenceService.save(item);
                return item;
            });

            TestMetadataItem itemTenant2 = executionContextManager.executeAsTenant("tenant2", () -> {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item2");
                item.setScope("scope1");
                persistenceService.save(item);
                return item;
            });

            // Verify both items were saved
            executionContextManager.executeAsTenant("tenant1", () -> {
                assertNotNull(persistenceService.load(itemTenant1.getItemId(), TestMetadataItem.class));
                return null;
            });

            executionContextManager.executeAsTenant("tenant2", () -> {
                assertNotNull(persistenceService.load(itemTenant2.getItemId(), TestMetadataItem.class));
                return null;
            });

            // Purge items with scope1 but only in tenant1's context
            executionContextManager.executeAsTenant("tenant1", () -> {
                persistenceService.purge("scope1");
                return null;
            });

            // Verify item from tenant1 is gone but tenant2's item is still there
            executionContextManager.executeAsTenant("tenant1", () -> {
                assertNull(persistenceService.load(itemTenant1.getItemId(), TestMetadataItem.class));
                return null;
            });

            executionContextManager.executeAsTenant("tenant2", () -> {
                assertNotNull(persistenceService.load(itemTenant2.getItemId(), TestMetadataItem.class));
                return null;
            });
        }

        @Test
        void shouldRespectTenantIsolationInTimeBasedPurge() throws Exception {
            // Create items for different tenants with dates in the past
            Calendar pastCal = Calendar.getInstance();
            pastCal.add(Calendar.DAY_OF_YEAR, -10);
            Date pastDate = pastCal.getTime();

            TestMetadataItem itemTenant1 = executionContextManager.executeAsTenant("tenant1", () -> {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item1");
                item.setCreationDate(pastDate);
                persistenceService.save(item);
                return item;
            });

            TestMetadataItem itemTenant2 = executionContextManager.executeAsTenant("tenant2", () -> {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item2");
                item.setCreationDate(pastDate);
                persistenceService.save(item);
                return item;
            });

            // Verify both items were saved
            executionContextManager.executeAsTenant("tenant1", () -> {
                assertNotNull(persistenceService.load(itemTenant1.getItemId(), TestMetadataItem.class));
                return null;
            });

            executionContextManager.executeAsTenant("tenant2", () -> {
                assertNotNull(persistenceService.load(itemTenant2.getItemId(), TestMetadataItem.class));
                return null;
            });

            // Purge items older than 5 days but only in tenant1's context
            executionContextManager.executeAsTenant("tenant1", () -> {
                persistenceService.purgeTimeBasedItems(5, TestMetadataItem.class);
                return null;
            });

            // Verify item from tenant1 is gone but tenant2's item is still there
            executionContextManager.executeAsTenant("tenant1", () -> {
                assertNull(persistenceService.load(itemTenant1.getItemId(), TestMetadataItem.class));
                return null;
            });

            executionContextManager.executeAsTenant("tenant2", () -> {
                assertNotNull(persistenceService.load(itemTenant2.getItemId(), TestMetadataItem.class));
                return null;
            });
        }
    }

    @Nested
    class CustomItemOperations {

        private CustomItem createCustomItem(String id, String itemType, Map<String, Object> properties) {
            CustomItem item = new CustomItem(id, itemType);
            if (properties != null) {
                item.getProperties().putAll(properties);
            }
            return item;
        }

        @Test
        void shouldSaveAndLoadCustomItem() {
            // Given
            String itemType = "testCustomType";
            String itemId = "customItem1";
            Map<String, Object> properties = new HashMap<>();
            properties.put("prop1", "value1");
            properties.put("prop2", 42);

            CustomItem item = createCustomItem(itemId, itemType, properties);

            // When
            persistenceService.save(item);
            CustomItem loaded = persistenceService.loadCustomItem(itemId, itemType);

            // Then
            assertNotNull(loaded);
            assertEquals(itemId, loaded.getItemId());
            assertEquals(itemType, loaded.getItemType());
            assertEquals("value1", loaded.getProperties().get("prop1"));
            assertEquals(42, loaded.getProperties().get("prop2"));
        }

        @Test
        void shouldRespectTenantIsolationInLoadCustomItem() {
            // Given
            String itemType = "testCustomType";
            String itemId = "customItemTenant";
            Map<String, Object> properties = new HashMap<>();
            properties.put("prop1", "tenant1Value");

            final CustomItem itemTenant1 = createCustomItem(itemId, itemType, properties);

            // First save in tenant1 context
            executionContextManager.executeAsTenant("tenant1", () -> {
                persistenceService.save(itemTenant1);
                return null;
            });

            // Then try to load in tenant2 context
            CustomItem loadedInOtherTenant = executionContextManager.executeAsTenant("tenant2", () -> {
                return persistenceService.loadCustomItem(itemId, itemType);
            });

            // And load in original tenant context
            CustomItem loadedInOriginalTenant = executionContextManager.executeAsTenant("tenant1", () -> {
                return persistenceService.loadCustomItem(itemId, itemType);
            });

            // Then
            assertNull(loadedInOtherTenant, "Item should not be accessible in different tenant");
            assertNotNull(loadedInOriginalTenant, "Item should be accessible in original tenant");
            assertEquals("tenant1Value", loadedInOriginalTenant.getProperties().get("prop1"));
        }

        @Test
        void shouldRemoveCustomItem() {
            // Given
            String itemType = "testCustomType";
            String itemId = "customItemToRemove";

            CustomItem item = createCustomItem(itemId, itemType, null);
            persistenceService.save(item);

            // When
            boolean removeResult = persistenceService.removeCustomItem(itemId, itemType);
            CustomItem afterRemove = persistenceService.loadCustomItem(itemId, itemType);

            // Then
            assertTrue(removeResult);
            assertNull(afterRemove);
        }

        @Test
        void shouldRespectTenantIsolationInRemoveCustomItem() {
            // Given
            String itemType = "testCustomType";
            String itemId = "customItemMultiTenant";

            final CustomItem itemTenant1 = createCustomItem(itemId, itemType, null);

            // Save in tenant1 context
            executionContextManager.executeAsTenant("tenant1", () -> {
                persistenceService.save(itemTenant1);
                return null;
            });

            // Try to remove in tenant2 context
            boolean removeResultWrongTenant = executionContextManager.executeAsTenant("tenant2", () -> {
                return persistenceService.removeCustomItem(itemId, itemType);
            });

            // Verify item still exists in tenant1
            CustomItem stillExistsInTenant1 = executionContextManager.executeAsTenant("tenant1", () -> {
                return persistenceService.loadCustomItem(itemId, itemType);
            });

            // Then remove in correct tenant
            boolean removeResultCorrectTenant = executionContextManager.executeAsTenant("tenant1", () -> {
                return persistenceService.removeCustomItem(itemId, itemType);
            });

            // Then
            assertFalse(removeResultWrongTenant, "Should not be able to remove item from different tenant");
            assertNotNull(stillExistsInTenant1, "Item should still exist in original tenant");
            assertTrue(removeResultCorrectTenant, "Should be able to remove item in original tenant");

            // Verify item is gone in tenant1
            CustomItem afterRemoveInTenant1 = executionContextManager.executeAsTenant("tenant1", () -> {
                return persistenceService.loadCustomItem(itemId, itemType);
            });
            assertNull(afterRemoveInTenant1);
        }

        @Test
        void shouldQueryCustomItems() {
            // Given
            String itemType = "testQueryCustomType";

            // Create multiple items
            for (int i = 0; i < 10; i++) {
                Map<String, Object> props = new HashMap<>();
                props.put("index", i);
                props.put("even", i % 2 == 0);

                CustomItem item = createCustomItem("queryItem" + i, itemType, props);
                persistenceService.save(item);
            }

            // Create a condition to match only even items
            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
            condition.setParameter("propertyName", "properties.even");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", true);

            // When - retry query until items are available (handles refresh delay)
            PartialList<CustomItem> results = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.queryCustomItem(condition, null, itemType, 1, 3, null),
                3
            );

            // Then
            assertEquals(5, results.getTotalSize(), "Should find 5 items with even index");
            assertEquals(3, results.getList().size(), "Should return 3 items with offset 1");
            assertEquals(1, results.getOffset());

            // Verify the returned items have the expected property values
            assertTrue((Boolean) results.getList().get(0).getProperties().get("even"));
            assertTrue((Boolean) results.getList().get(1).getProperties().get("even"));
            assertTrue((Boolean) results.getList().get(2).getProperties().get("even"));
        }

        @Test
        void shouldSupportCustomItemScrollQueries() {
            // Given
            String itemType = "testScrollCustomType";

            // Create multiple items
            for (int i = 0; i < 20; i++) {
                Map<String, Object> props = new HashMap<>();
                props.put("index", i);

                CustomItem item = createCustomItem("scrollItem" + i, itemType, props);
                persistenceService.save(item);
            }

            // When - Start a scroll query (retry until items are available)
            PartialList<CustomItem> firstPage = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.queryCustomItem(null, "index:asc", itemType, 0, 5, "1m"),
                5
            );
            String scrollId = firstPage.getScrollIdentifier();

            assertNotNull(scrollId, "Should have a scroll identifier");
            assertEquals(5, firstPage.getList().size());

            // When - Continue the scroll query
            PartialList<CustomItem> secondPage = persistenceService.continueCustomItemScrollQuery(itemType, scrollId, "1m");
            String scrollId2 = secondPage.getScrollIdentifier();

            // Then
            assertNotNull(scrollId2, "Should have a scroll identifier for second page");
            assertEquals(5, secondPage.getList().size());

            // Verify these are different items than the first page
            Set<String> firstPageIds = firstPage.getList().stream()
                    .map(Item::getItemId)
                    .collect(Collectors.toSet());

            Set<String> secondPageIds = secondPage.getList().stream()
                    .map(Item::getItemId)
                    .collect(Collectors.toSet());

            assertTrue(Collections.disjoint(firstPageIds, secondPageIds),
                    "First and second page should contain different items");

            // Complete the scroll
            PartialList<CustomItem> thirdPage = persistenceService.continueCustomItemScrollQuery(itemType, scrollId2, "1m");
            PartialList<CustomItem> fourthPage = persistenceService.continueCustomItemScrollQuery(itemType, thirdPage.getScrollIdentifier(), "1m");

            // Final page, should be no more scroll ID
            assertNull(fourthPage.getScrollIdentifier(), "Last page should not have a scroll identifier");
        }

        @Test
        void shouldRespectTenantIsolationInQueryCustomItem() {
            // Given - items for two different tenants
            String itemType = "testMultiTenantQueryType";

            // Create items in tenant1
            executionContextManager.executeAsTenant("tenant1", () -> {
                for (int i = 0; i < 5; i++) {
                    Map<String, Object> props = new HashMap<>();
                    props.put("tenant", "tenant1");
                    props.put("index", i);

                    CustomItem item = createCustomItem("tenant1Item" + i, itemType, props);
                    persistenceService.save(item);
                }
                return null;
            });

            // Create items in tenant2
            executionContextManager.executeAsTenant("tenant2", () -> {
                for (int i = 0; i < 7; i++) {
                    Map<String, Object> props = new HashMap<>();
                    props.put("tenant", "tenant2");
                    props.put("index", i);

                    CustomItem item = createCustomItem("tenant2Item" + i, itemType, props);
                    persistenceService.save(item);
                }
                return null;
            });

            // When - query from tenant1 (retry until items are available)
            PartialList<CustomItem> tenant1Results = executionContextManager.executeAsTenant("tenant1", () -> {
                return TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryCustomItem(null, null, itemType, 0, 100, null),
                    5
                );
            });

            // When - query from tenant2 (retry until items are available)
            PartialList<CustomItem> tenant2Results = executionContextManager.executeAsTenant("tenant2", () -> {
                return TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryCustomItem(null, null, itemType, 0, 100, null),
                    7
                );
            });

            // Then
            assertEquals(5, tenant1Results.getTotalSize(), "Tenant1 should only see its 5 items");
            assertEquals(7, tenant2Results.getTotalSize(), "Tenant2 should only see its 7 items");

            // Verify tenant isolation in scroll queries (retry until items are available)
            PartialList<CustomItem> tenant1ScrollResults = executionContextManager.executeAsTenant("tenant1", () -> {
                PartialList<CustomItem> firstPage = TestHelper.retryQueryUntilAvailable(
                    () -> persistenceService.queryCustomItem(null, "index:asc", itemType, 0, 2, "1m"),
                    2
                );
                String scrollId = firstPage.getScrollIdentifier();
                return persistenceService.continueCustomItemScrollQuery(itemType, scrollId, "1m");
            });

            assertEquals(2, tenant1ScrollResults.getList().size(), "Tenant1 should get correct page size in scroll query");

            for (CustomItem item : tenant1ScrollResults.getList()) {
                assertEquals("tenant1", item.getProperties().get("tenant"), "Items should belong to tenant1");
            }
        }

        @Test
        void shouldRespectScrollTimeValidity() throws InterruptedException {
            // Given
            String itemType = "testScrollTimeValidityType";

            // Create multiple items
            for (int i = 0; i < 10; i++) {
                Map<String, Object> props = new HashMap<>();
                props.put("index", i);

                CustomItem item = createCustomItem("validityItem" + i, itemType, props);
                persistenceService.save(item);
            }

            // When - Start a scroll query with very short validity (100ms) (retry until items are available)
            Condition matchAllCondition = new Condition(TestConditionEvaluators.getConditionType("matchAllCondition"));
            PartialList<CustomItem> firstPage = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.queryCustomItem(matchAllCondition, "index:asc", itemType, 0, 3, "100ms"),
                3
            );
            String scrollId = firstPage.getScrollIdentifier();

            assertNotNull(scrollId, "Should have a scroll identifier");
            assertEquals(3, firstPage.getList().size());

            // Wait for scroll to expire
            Thread.sleep(200);

            // When - Try to continue the expired scroll query
            PartialList<CustomItem> secondPage = persistenceService.continueCustomItemScrollQuery(itemType, scrollId, "100ms");

            // Then - Should return empty result as scroll has expired
            assertEquals(0, secondPage.getList().size(), "Should return empty list for expired scroll");
            assertNull(secondPage.getScrollIdentifier(), "Should not have a scroll identifier for expired scroll");

            // When - Start a new scroll query with longer validity (retry until items are available)
            PartialList<CustomItem> newFirstPage = TestHelper.retryQueryUntilAvailable(
                () -> persistenceService.queryCustomItem(null, "index:asc", itemType, 0, 3, "10s"),
                3
            );
            String newScrollId = newFirstPage.getScrollIdentifier();

            // Then - Continue the scroll immediately should work
            PartialList<CustomItem> newSecondPage = persistenceService.continueCustomItemScrollQuery(itemType, newScrollId, "10s");
            assertNotNull(newSecondPage.getScrollIdentifier(), "Should have a scroll identifier for valid scroll");
            assertEquals(3, newSecondPage.getList().size(), "Should return items for valid scroll");
        }
    }

    @Nested
    class VersioningAndConcurrencyTests {

        @Test
        void shouldHandleVersioning() {
            // Create a test item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-version");
            item.setName("Test Version");

            // Initial save should set version to 1
            persistenceService.save(item);
            assertEquals(1L, item.getVersion());

            // Subsequent saves should increment version
            persistenceService.save(item);
            assertEquals(2L, item.getVersion());

            // Load and verify version persisted
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            assertEquals(2L, loaded.getVersion());
        }

        @Test
        void shouldHandleVersioningWithExplicitVersion() {
            // Create a test item with explicit version
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-explicit-version");
            item.setName("Test Explicit Version");
            item.setVersion(5L);

            // Save should increment existing version
            persistenceService.save(item);
            assertEquals(6L, item.getVersion());

            // Load and verify version persisted
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            assertEquals(6L, loaded.getVersion());
        }

        @Test
        void shouldGenerateAndIncrementSequenceNumber() {
            // Create a test item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-seq-no");
            item.setName("Test Sequence Number");

            // Initial save should set sequence number to 1
            persistenceService.save(item);
            assertNotNull(item.getSystemMetadata("_seq_no"));
            assertEquals(1L, ((Number) item.getSystemMetadata("_seq_no")).longValue());

            // Each save should increment sequence number
            persistenceService.save(item);
            assertEquals(2L, ((Number) item.getSystemMetadata("_seq_no")).longValue());

            persistenceService.save(item);
            assertEquals(3L, ((Number) item.getSystemMetadata("_seq_no")).longValue());

            // Load and verify sequence number persisted
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            assertEquals(3L, ((Number) loaded.getSystemMetadata("_seq_no")).longValue());
        }

        @Test
        void shouldSetPrimaryTerm() {
            // Create a test item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-primary-term");
            item.setName("Test Primary Term");

            // Initial save should set primary term to 1
            persistenceService.save(item);
            assertNotNull(item.getSystemMetadata("_primary_term"));
            assertEquals(1L, ((Number) item.getSystemMetadata("_primary_term")).longValue());

            // Primary term shouldn't change on regular updates
            persistenceService.save(item);
            assertEquals(1L, ((Number) item.getSystemMetadata("_primary_term")).longValue());

            // Load and verify primary term persisted
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            assertEquals(1L, ((Number) loaded.getSystemMetadata("_primary_term")).longValue());
        }

        @Test
        void shouldRejectUpdateWithIncorrectSequenceNumber() {
            // Create a test item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-seq-conflict");
            item.setName("Test Sequence Conflict");

            // Save the item to get a sequence number
            persistenceService.save(item);
            Long initialSeqNo = ((Number) item.getSystemMetadata("_seq_no")).longValue();
            Long initialPrimaryTerm = ((Number) item.getSystemMetadata("_primary_term")).longValue();

            // Create a different instance with the same ID but wrong sequence number
            TestMetadataItem conflictItem = new TestMetadataItem();
            conflictItem.setItemId(item.getItemId());
            conflictItem.setName("Conflicting Update");
            conflictItem.setSystemMetadata("_seq_no", initialSeqNo - 1); // Use wrong sequence number
            conflictItem.setSystemMetadata("_primary_term", initialPrimaryTerm);

            // Try to save with incorrect sequence number, should fail
            boolean saveResult = persistenceService.save(conflictItem);
            assertFalse(saveResult, "Save should fail with incorrect sequence number");

            // Original item should still be there unchanged
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            assertEquals(item.getName(), loaded.getName());
            assertEquals(initialSeqNo, ((Number) loaded.getSystemMetadata("_seq_no")).longValue());
        }

        @Test
        void shouldRejectUpdateWithIncorrectPrimaryTerm() {
            // Create a test item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-term-conflict");
            item.setName("Test Primary Term Conflict");

            // Save the item to get a primary term
            persistenceService.save(item);
            Long initialSeqNo = ((Number) item.getSystemMetadata("_seq_no")).longValue();
            Long initialPrimaryTerm = ((Number) item.getSystemMetadata("_primary_term")).longValue();

            // Create a different instance with the same ID but wrong primary term
            TestMetadataItem conflictItem = new TestMetadataItem();
            conflictItem.setItemId(item.getItemId());
            conflictItem.setName("Conflicting Term Update");
            conflictItem.setSystemMetadata("_seq_no", initialSeqNo);
            conflictItem.setSystemMetadata("_primary_term", initialPrimaryTerm + 1); // Use wrong primary term

            // Try to save with incorrect primary term, should fail
            boolean saveResult = persistenceService.save(conflictItem);
            assertFalse(saveResult, "Save should fail with incorrect primary term");

            // Original item should still be there unchanged
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            assertEquals(item.getName(), loaded.getName());
            assertEquals(initialPrimaryTerm, ((Number) loaded.getSystemMetadata("_primary_term")).longValue());
        }

        @Test
        void shouldAllowUpdateWithCorrectSequenceNumberAndPrimaryTerm() {
            // Create a test item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-correct-seq");
            item.setName("Test Correct Sequence");

            // Save the item to get a sequence number and primary term
            persistenceService.save(item);
            Long initialSeqNo = ((Number) item.getSystemMetadata("_seq_no")).longValue();
            Long initialPrimaryTerm = ((Number) item.getSystemMetadata("_primary_term")).longValue();

            // Create a different instance with the same ID and correct sequence number
            TestMetadataItem updateItem = new TestMetadataItem();
            updateItem.setItemId(item.getItemId());
            updateItem.setName("Updated Name");
            updateItem.setSystemMetadata("_seq_no", initialSeqNo);
            updateItem.setSystemMetadata("_primary_term", initialPrimaryTerm);

            // Try to save with correct sequence number and primary term, should succeed
            boolean saveResult = persistenceService.save(updateItem);
            assertTrue(saveResult, "Save should succeed with correct sequence number and primary term");

            // Item should be updated with new name and incremented sequence number
            TestMetadataItem loaded = persistenceService.load(item.getItemId(), TestMetadataItem.class);
            assertEquals(updateItem.getName(), loaded.getName());
            assertEquals(initialSeqNo + 1, ((Number) loaded.getSystemMetadata("_seq_no")).longValue());
            assertEquals(initialPrimaryTerm, ((Number) loaded.getSystemMetadata("_primary_term")).longValue());
        }
    }

    @Nested
    class RefreshDelaySimulationTests {
        @Test
        void shouldNotReturnItemsImmediatelyAfterSaveWhenRefreshDelayEnabled() throws InterruptedException {
            // given - create persistence service with refresh delay enabled (default)
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save an item
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            profile.setProperty("firstName", "John");
            serviceWithDelay.save(profile);
            
            // then - item should not be immediately available in queries (simulating Elasticsearch behavior)
            List<Profile> queryResults = serviceWithDelay.query(null, null, Profile.class);
            assertTrue(queryResults.isEmpty(), "Item should not be immediately available in queries after save");
            
            // but load by ID should work immediately (Elasticsearch get by ID works immediately)
            Profile loaded = serviceWithDelay.load("test-profile", Profile.class);
            assertNotNull(loaded, "Load by ID should work immediately even with refresh delay");
            assertEquals("John", loaded.getProperty("firstName"));
        }
        
        @Test
        void shouldReturnItemsAfterRefreshInterval() throws InterruptedException {
            // given - create persistence service with short refresh interval for testing
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher, 
                    InMemoryPersistenceServiceImpl.DEFAULT_STORAGE_DIR, true, true, true, true, 100L);
            
            // when - save an item
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            serviceWithDelay.save(profile);
            
            // then - item should not be immediately available
            List<Profile> queryResults = serviceWithDelay.query(null, null, Profile.class);
            assertTrue(queryResults.isEmpty(), "Item should not be immediately available");
            
            // wait for refresh interval to pass
            Thread.sleep(150);
            
            // now item should be available (retry until available)
            queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, Profile.class),
                1
            );
            assertEquals(1, queryResults.size(), "Item should be available after refresh interval");
            assertEquals("test-profile", queryResults.get(0).getItemId());
        }
        
        @Test
        void shouldReturnItemsImmediatelyAfterExplicitRefresh() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save an item
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            serviceWithDelay.save(profile);
            
            // then - item should not be immediately available
            List<Profile> queryResults = serviceWithDelay.query(null, null, Profile.class);
            assertTrue(queryResults.isEmpty(), "Item should not be immediately available");
            
            // when - explicitly refresh
            serviceWithDelay.refresh();
            
            // then - item should now be available (retry until available)
            queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, Profile.class),
                1
            );
            assertEquals(1, queryResults.size(), "Item should be available after explicit refresh");
            assertEquals("test-profile", queryResults.get(0).getItemId());
        }
        
        @Test
        void shouldReturnItemsImmediatelyAfterRefreshIndex() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save an item
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            serviceWithDelay.save(profile);
            
            // then - item should not be immediately available
            List<Profile> queryResults = serviceWithDelay.query(null, null, Profile.class);
            assertTrue(queryResults.isEmpty(), "Item should not be immediately available");
            
            // when - explicitly refresh index
            serviceWithDelay.refreshIndex(Profile.class, null);
            
            // then - item should now be available (retry until available)
            queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, Profile.class),
                1
            );
            assertEquals(1, queryResults.size(), "Item should be available after refreshIndex");
            assertEquals("test-profile", queryResults.get(0).getItemId());
        }
        
        @Test
        void shouldReturnItemsImmediatelyWhenRefreshDelayDisabled() {
            // given - create persistence service with refresh delay disabled
            InMemoryPersistenceServiceImpl serviceWithoutDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher,
                    InMemoryPersistenceServiceImpl.DEFAULT_STORAGE_DIR, true, true, true, false, 1000L);
            
            // when - save an item
            Profile profile = new Profile();
            profile.setItemId("test-profile");
            serviceWithoutDelay.save(profile);
            
            // then - item should be immediately available (no delay simulation)
            List<Profile> queryResults = serviceWithoutDelay.query(null, null, Profile.class);
            assertEquals(1, queryResults.size(), "Item should be immediately available when refresh delay is disabled");
            assertEquals("test-profile", queryResults.get(0).getItemId());
        }
        
        @Test
        void shouldFilterMultipleItemsByRefreshStatus() throws InterruptedException {
            // given - create persistence service with short refresh interval
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher,
                    InMemoryPersistenceServiceImpl.DEFAULT_STORAGE_DIR, true, true, true, true, 200L);
            
            // when - save multiple items
            Profile profile1 = new Profile();
            profile1.setItemId("profile1");
            serviceWithDelay.save(profile1);
            
            Thread.sleep(250); // Wait for first item to be refreshed
            
            Profile profile2 = new Profile();
            profile2.setItemId("profile2");
            serviceWithDelay.save(profile2);
            
            // then - only first item should be available (retry until available)
            List<Profile> queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, Profile.class),
                1
            );
            assertEquals(1, queryResults.size(), "Only refreshed items should be available");
            assertEquals("profile1", queryResults.get(0).getItemId());
            
            // wait for second item to be refreshed
            Thread.sleep(250);
            queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, Profile.class),
                2
            );
            assertEquals(2, queryResults.size(), "Both items should be available after refresh");
        }
        
        @Test
        void shouldRespectRefreshDelayInQueryCount() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save items
            Profile profile1 = new Profile();
            profile1.setItemId("profile1");
            serviceWithDelay.save(profile1);
            
            Profile profile2 = new Profile();
            profile2.setItemId("profile2");
            serviceWithDelay.save(profile2);
            
            // then - count should be 0 (items not yet refreshed)
            long count = serviceWithDelay.queryCount(null, Profile.ITEM_TYPE);
            assertEquals(0, count, "Count should not include unrefreshed items");
            
            // when - refresh
            serviceWithDelay.refresh();
            
            // then - count should include all items (retry until available)
            count = TestHelper.retryUntil(
                () -> serviceWithDelay.queryCount(null, Profile.ITEM_TYPE),
                c -> c == 2L
            );
            assertEquals(2, count, "Count should include all refreshed items");
        }
        
        @Test
        void shouldRespectRefreshDelayInGetAllItemsCount() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save items
            Profile profile1 = new Profile();
            profile1.setItemId("profile1");
            serviceWithDelay.save(profile1);
            
            Profile profile2 = new Profile();
            profile2.setItemId("profile2");
            serviceWithDelay.save(profile2);
            
            // then - count should be 0 (items not yet refreshed)
            long count = serviceWithDelay.getAllItemsCount(Profile.ITEM_TYPE);
            assertEquals(0, count, "Count should not include unrefreshed items");
            
            // when - refresh
            serviceWithDelay.refresh();
            
            // then - count should include all items (retry until available)
            count = TestHelper.retryUntil(
                () -> serviceWithDelay.getAllItemsCount(Profile.ITEM_TYPE),
                c -> c == 2L
            );
            assertEquals(2, count, "Count should include all refreshed items");
        }
        
        @Test
        void shouldShutdownRefreshThread() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - shutdown
            serviceWithDelay.shutdown();
            
            // then - should not throw exception and thread should be stopped
            // (we can't directly verify thread state, but shutdown should complete without error)
            assertTrue(true, "Shutdown should complete without error");
        }
        
        @Test
        void shouldDeleteItemsImmediatelyRegardlessOfRefreshStatus() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save items
            Profile profile1 = new Profile();
            profile1.setItemId("profile1");
            profile1.setProperty("name", "Profile 1");
            serviceWithDelay.save(profile1);
            
            Profile profile2 = new Profile();
            profile2.setItemId("profile2");
            profile2.setProperty("name", "Profile 2");
            serviceWithDelay.save(profile2);
            
            // then - items should not be immediately available in queries
            List<Profile> queryResults = serviceWithDelay.query(null, null, Profile.class);
            assertTrue(queryResults.isEmpty(), "Items should not be immediately available in queries");
            
            // when - delete an item (should work immediately, regardless of refresh status)
            boolean deleted = serviceWithDelay.remove("profile1", Profile.class);
            assertTrue(deleted, "Delete should succeed immediately");
            
            // then - deleted item should not be loadable
            Profile loaded = serviceWithDelay.load("profile1", Profile.class);
            assertNull(loaded, "Deleted item should not be loadable");
            
            // and - after refresh, deleted item should still not appear in queries (retry until available)
            serviceWithDelay.refresh();
            queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, Profile.class),
                1
            );
            assertEquals(1, queryResults.size(), "Only non-deleted item should be available");
            assertEquals("profile2", queryResults.get(0).getItemId());
        }
        
        @Test
        void shouldDeleteByQueryAllMatchingItemsRegardlessOfRefreshStatus() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save multiple items
            Profile profile1 = new Profile();
            profile1.setItemId("profile1");
            profile1.setProperty("category", "test");
            serviceWithDelay.save(profile1);
            
            Profile profile2 = new Profile();
            profile2.setItemId("profile2");
            profile2.setProperty("category", "test");
            serviceWithDelay.save(profile2);
            
            Profile profile3 = new Profile();
            profile3.setItemId("profile3");
            profile3.setProperty("category", "other");
            serviceWithDelay.save(profile3);
            
            // then - items should not be immediately available in queries
            List<Profile> queryResults = serviceWithDelay.query(null, null, Profile.class);
            assertTrue(queryResults.isEmpty(), "Items should not be immediately available");
            
            // when - delete by query (should work on all matching items, not just refreshed ones)
            Condition condition = new Condition();
            condition.setConditionType(TestConditionEvaluators.getConditionType("profilePropertyCondition"));
            condition.setParameter("propertyName", "properties.category");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", "test");
            
            // Note: removeByQuery should work on all items regardless of refresh status
            // In Elasticsearch, deleteByQuery works on all matching documents
            boolean deleted = serviceWithDelay.removeByQuery(condition, Profile.class);
            assertTrue(deleted, "Delete by query should succeed");
            
            // then - deleted items should not be loadable
            assertNull(serviceWithDelay.load("profile1", Profile.class), "Deleted item should not be loadable");
            assertNull(serviceWithDelay.load("profile2", Profile.class), "Deleted item should not be loadable");
            assertNotNull(serviceWithDelay.load("profile3", Profile.class), "Non-matching item should still be loadable");
            
            // and - after refresh, deleted items should still not appear (retry until available)
            serviceWithDelay.refresh();
            queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, Profile.class),
                1
            );
            assertEquals(1, queryResults.size(), "Only non-deleted item should be available");
            assertEquals("profile3", queryResults.get(0).getItemId());
        }
        
        @Test
        void shouldCleanupPendingRefreshItemsOnDelete() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save and immediately delete an item
            Profile profile = new Profile();
            profile.setItemId("profile1");
            serviceWithDelay.save(profile);
            serviceWithDelay.remove("profile1", Profile.class);
            
            // then - item should be deleted and not in pending refresh list
            // (this prevents memory leaks and ensures deleted items don't appear after refresh)
            serviceWithDelay.refresh();
            List<Profile> queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, Profile.class),
                0
            );
            assertTrue(queryResults.isEmpty(), "Deleted item should not appear even after refresh");
        }
        
        @Test
        void shouldPurgeItemsWithRefreshDelay() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save items with different creation dates
            Calendar oldDate = Calendar.getInstance();
            oldDate.add(Calendar.DAY_OF_YEAR, -10);
            
            TestMetadataItem oldItem = new TestMetadataItem();
            oldItem.setItemId("old-item");
            oldItem.setCreationDate(oldDate.getTime());
            serviceWithDelay.save(oldItem);
            
            TestMetadataItem newItem = new TestMetadataItem();
            newItem.setItemId("new-item");
            newItem.setCreationDate(new Date());
            serviceWithDelay.save(newItem);
            
            // then - items should not be immediately available
            List<TestMetadataItem> queryResults = serviceWithDelay.query(null, null, TestMetadataItem.class);
            assertTrue(queryResults.isEmpty(), "Items should not be immediately available");
            
            // when - purge items older than 7 days
            Calendar purgeDate = Calendar.getInstance();
            purgeDate.add(Calendar.DAY_OF_YEAR, -7);
            serviceWithDelay.purge(purgeDate.getTime());
            
            // then - old item should be deleted (even though not refreshed)
            assertNull(serviceWithDelay.load("old-item", TestMetadataItem.class), "Old item should be purged");
            assertNotNull(serviceWithDelay.load("new-item", TestMetadataItem.class), "New item should not be purged");
            
            // and - after refresh, only new item should appear (retry until available)
            serviceWithDelay.refresh();
            queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, queryResults.size(), "Only new item should be available after refresh");
            assertEquals("new-item", queryResults.get(0).getItemId());
        }
        
        @Test
        void shouldRemoveIndexWithRefreshDelay() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save items
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            serviceWithDelay.save(item1);
            
            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            serviceWithDelay.save(item2);
            
            // then - items should not be immediately available
            List<TestMetadataItem> queryResults = serviceWithDelay.query(null, null, TestMetadataItem.class);
            assertTrue(queryResults.isEmpty(), "Items should not be immediately available");
            
            // when - remove index
            boolean removed = serviceWithDelay.removeIndex(TestMetadataItem.ITEM_TYPE);
            assertTrue(removed, "Index should be removed");
            
            // then - all items should be deleted (even though not refreshed)
            assertNull(serviceWithDelay.load("item1", TestMetadataItem.class), "Item should be deleted");
            assertNull(serviceWithDelay.load("item2", TestMetadataItem.class), "Item should be deleted");
            
            // and - after refresh, no items should appear (retry until available)
            serviceWithDelay.refresh();
            queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                0
            );
            assertTrue(queryResults.isEmpty(), "No items should be available after index removal");
        }
        
        @Test
        void shouldRemoveCustomItemWithRefreshDelay() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save custom item
            CustomItem customItem = new CustomItem();
            customItem.setItemId("custom1");
            customItem.setItemType("testCustomType");
            serviceWithDelay.save(customItem);
            
            // then - item should not be immediately available in queries
            PartialList<CustomItem> queryResults = serviceWithDelay.queryCustomItem(null, null, "testCustomType", 0, 10, null);
            assertEquals(0, queryResults.getTotalSize(), "Item should not be immediately available");
            
            // but - load by ID should work
            CustomItem loaded = serviceWithDelay.loadCustomItem("custom1", "testCustomType");
            assertNotNull(loaded, "Load by ID should work immediately");
            
            // when - remove custom item
            boolean removed = serviceWithDelay.removeCustomItem("custom1", "testCustomType");
            assertTrue(removed, "Custom item should be removed");
            
            // then - item should not be loadable
            assertNull(serviceWithDelay.loadCustomItem("custom1", "testCustomType"), "Deleted item should not be loadable");
            
            // and - after refresh, item should still not appear (retry until available)
            serviceWithDelay.refresh();
            queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.queryCustomItem(null, null, "testCustomType", 0, 10, null),
                0
            );
            assertEquals(0, queryResults.getTotalSize(), "Deleted item should not appear after refresh");
        }
        
        @Test
        void shouldHandleUpdateOfAlreadyRefreshedItem() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save and refresh an item
            Profile profile = new Profile();
            profile.setItemId("profile1");
            profile.setProperty("name", "Original");
            serviceWithDelay.save(profile);
            serviceWithDelay.refresh();
            
            // then - item should be available (retry until available)
            List<Profile> queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, Profile.class),
                1
            );
            assertEquals(1, queryResults.size(), "Item should be available after refresh");
            assertEquals("Original", queryResults.get(0).getProperty("name"));
            
            // when - update the item (this adds it back to pendingRefreshItems)
            profile.setProperty("name", "Updated");
            serviceWithDelay.save(profile);
            
            // then - updated item should not be immediately available in queries
            // The item was removed from refreshedIndexes when we saved it again,
            // and added back to pendingRefreshItems, so it needs refresh again
            queryResults = serviceWithDelay.query(null, null, Profile.class);
            assertTrue(queryResults.isEmpty(), "Updated item should not be immediately available until refreshed");
            
            // when - refresh again
            serviceWithDelay.refresh();
            
            // then - updated item should be available with new value (retry until available)
            queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, Profile.class),
                1
            );
            assertEquals(1, queryResults.size(), "Updated item should be available after refresh");
            assertEquals("Updated", queryResults.get(0).getProperty("name"), "Updated value should be visible");
        }
    }

    @Nested
    class RefreshPolicyTests {
        @Test
        void shouldRespectFalseRefreshPolicy() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set FALSE refresh policy for a custom item type
            serviceWithDelay.setRefreshPolicy("testItem", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            
            // and - save an item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setItemType("testItem");
            serviceWithDelay.save(item);
            
            // then - item should not be immediately available (FALSE = wait for automatic refresh)
            List<TestMetadataItem> queryResults = serviceWithDelay.query(null, null, TestMetadataItem.class);
            assertTrue(queryResults.isEmpty(), "Item with FALSE refresh policy should not be immediately available");
            
            // but - load by ID should work
            TestMetadataItem loaded = serviceWithDelay.load("test-item", TestMetadataItem.class);
            assertNotNull(loaded, "Load by ID should work immediately even with FALSE refresh policy");
        }
        
        @Test
        void shouldRespectTrueRefreshPolicy() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set TRUE refresh policy for a custom item type
            serviceWithDelay.setRefreshPolicy("testItem", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            
            // and - save an item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setItemType("testItem");
            serviceWithDelay.save(item);
            
            // then - item should be immediately available (TRUE = immediate refresh) - retry until available
            List<TestMetadataItem> queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, queryResults.size(), "Item with TRUE refresh policy should be immediately available");
            assertEquals("test-item", queryResults.get(0).getItemId());
        }
        
        @Test
        void shouldRespectWaitForRefreshPolicy() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set WAIT_FOR refresh policy for a custom item type
            serviceWithDelay.setRefreshPolicy("testItem", InMemoryPersistenceServiceImpl.RefreshPolicy.WAIT_FOR);
            
            // and - save an item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setItemType("testItem");
            serviceWithDelay.save(item);
            
            // then - item should be immediately available (WAIT_FOR = wait for refresh, which completes immediately in-memory) - retry until available
            List<TestMetadataItem> queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, queryResults.size(), "Item with WAIT_FOR refresh policy should be immediately available");
            assertEquals("test-item", queryResults.get(0).getItemId());
        }
        
        @Test
        void shouldSetRefreshPolicyFromJson() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set refresh policies from JSON (Elasticsearch/OpenSearch format)
            String json = "{\"event\":\"WAIT_FOR\",\"rule\":\"FALSE\",\"scheduledTask\":\"TRUE\"}";
            serviceWithDelay.setItemTypeToRefreshPolicy(json);
            
            // then - policies should be set correctly
            TestMetadataItem eventItem = new TestMetadataItem();
            eventItem.setItemId("event1");
            eventItem.setItemType("event");
            serviceWithDelay.save(eventItem);
            
            // Event with WAIT_FOR should be immediately available (retry until available)
            List<TestMetadataItem> eventResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, eventResults.size(), "Event with WAIT_FOR policy should be immediately available");
            
            TestMetadataItem ruleItem = new TestMetadataItem();
            ruleItem.setItemId("rule1");
            ruleItem.setItemType("rule");
            serviceWithDelay.save(ruleItem);
            
            // Rule with FALSE should not be immediately available (retry to ensure only event is visible)
            List<TestMetadataItem> ruleResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                1
            );
            // Should still only see the event, not the rule
            assertEquals(1, ruleResults.size(), "Rule with FALSE policy should not be immediately available");
            assertEquals("event1", ruleResults.get(0).getItemId());
        }
        
        @Test
        void shouldParseElasticsearchRefreshPolicyValues() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set refresh policies using Elasticsearch string values
            String json = "{\"item1\":\"NONE\",\"item2\":\"IMMEDIATE\",\"item3\":\"WAIT_UNTIL\"}";
            serviceWithDelay.setItemTypeToRefreshPolicy(json);
            
            // then - policies should be parsed correctly
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setItemType("item1");
            serviceWithDelay.save(item1);
            List<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                0
            );
            assertTrue(results1.isEmpty(), 
                    "NONE policy should not make item immediately available");
            
            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setItemType("item2");
            serviceWithDelay.save(item2);
            // After saving item2, we should see at least 1 item (item2 with IMMEDIATE policy)
            List<TestMetadataItem> results2 = TestHelper.retryUntil(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                r -> r != null && r.size() >= 1 && r.stream().anyMatch(i -> "item2".equals(i.getItemId()))
            );
            assertTrue(results2.size() >= 1, 
                    "IMMEDIATE policy should make item immediately available");
            assertTrue(results2.stream().anyMatch(i -> "item2".equals(i.getItemId())),
                    "Item2 should be in results");
            
            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setItemType("item3");
            serviceWithDelay.save(item3);
            // After saving item3, we should see at least 2 items (item2 and item3 with WAIT_UNTIL policy)
            List<TestMetadataItem> results3 = TestHelper.retryUntil(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                r -> r != null && r.size() >= 2 && r.stream().anyMatch(i -> "item3".equals(i.getItemId()))
            );
            assertTrue(results3.size() >= 2, 
                    "WAIT_UNTIL policy should make item immediately available");
            assertTrue(results3.stream().anyMatch(i -> "item3".equals(i.getItemId())),
                    "Item3 should be in results");
        }
        
        @Test
        void shouldParseOpenSearchRefreshPolicyValues() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set refresh policies using OpenSearch string values
            String json = "{\"item1\":\"False\",\"item2\":\"True\",\"item3\":\"WaitFor\"}";
            serviceWithDelay.setItemTypeToRefreshPolicy(json);
            
            // then - policies should be parsed correctly
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setItemType("item1");
            serviceWithDelay.save(item1);
            List<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                0
            );
            assertTrue(results1.isEmpty(), 
                    "False policy should not make item immediately available");
            
            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setItemType("item2");
            serviceWithDelay.save(item2);
            // After saving item2, we should see at least 1 item (item2 with True policy)
            List<TestMetadataItem> results2 = TestHelper.retryUntil(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                r -> r != null && r.size() >= 1 && r.stream().anyMatch(i -> "item2".equals(i.getItemId()))
            );
            assertTrue(results2.size() >= 1, 
                    "True policy should make item immediately available");
            assertTrue(results2.stream().anyMatch(i -> "item2".equals(i.getItemId())),
                    "Item2 should be in results");
            
            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setItemType("item3");
            serviceWithDelay.save(item3);
            // After saving item3, we should see at least 2 items (item2 and item3 with WaitFor policy)
            List<TestMetadataItem> results3 = TestHelper.retryUntil(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                r -> r != null && r.size() >= 2 && r.stream().anyMatch(i -> "item3".equals(i.getItemId()))
            );
            assertTrue(results3.size() >= 2, 
                    "WaitFor policy should make item immediately available");
            assertTrue(results3.stream().anyMatch(i -> "item3".equals(i.getItemId())),
                    "Item3 should be in results");
        }
        
        @Test
        void shouldDefaultToFalseRefreshPolicy() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save an item without setting a refresh policy
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            serviceWithDelay.save(item);
            
            // then - item should not be immediately available (defaults to FALSE) - retry to ensure it's not available
            List<TestMetadataItem> queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                0
            );
            assertTrue(queryResults.isEmpty(), "Item with default refresh policy (FALSE) should not be immediately available");
        }
        
        @Test
        void shouldHandleMixedRefreshPolicies() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set different refresh policies for different item types
            serviceWithDelay.setRefreshPolicy("immediateType", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            serviceWithDelay.setRefreshPolicy("waitType", InMemoryPersistenceServiceImpl.RefreshPolicy.WAIT_FOR);
            // falseType uses default FALSE policy
            
            // and - save items with different policies
            TestMetadataItem immediateItem = new TestMetadataItem();
            immediateItem.setItemId("immediate1");
            immediateItem.setItemType("immediateType");
            serviceWithDelay.save(immediateItem);
            
            TestMetadataItem waitItem = new TestMetadataItem();
            waitItem.setItemId("wait1");
            waitItem.setItemType("waitType");
            serviceWithDelay.save(waitItem);
            
            TestMetadataItem falseItem = new TestMetadataItem();
            falseItem.setItemId("false1");
            falseItem.setItemType("falseType");
            serviceWithDelay.save(falseItem);
            
            // then - only items with TRUE or WAIT_FOR should be immediately available (retry until available)
            // Use retryQueryUntilAvailable with expected count, then verify specific items
            List<TestMetadataItem> queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                2
            );
            assertEquals(2, queryResults.size(), "Only items with TRUE or WAIT_FOR policies should be immediately available");
            assertTrue(queryResults.stream().anyMatch(i -> "immediate1".equals(i.getItemId())), 
                    "Item with TRUE policy should be available");
            assertTrue(queryResults.stream().anyMatch(i -> "wait1".equals(i.getItemId())), 
                    "Item with WAIT_FOR policy should be available");
            assertFalse(queryResults.stream().anyMatch(i -> "false1".equals(i.getItemId())), 
                    "Item with FALSE policy should not be available");
        }
        
        @Test
        void shouldReturnCorrectIsConsistentValue() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set different refresh policies
            serviceWithDelay.setRefreshPolicy("consistentType", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            serviceWithDelay.setRefreshPolicy("inconsistentType", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            
            // and - create items
            TestMetadataItem consistentItem = new TestMetadataItem();
            consistentItem.setItemId("consistent1");
            consistentItem.setItemType("consistentType");
            serviceWithDelay.save(consistentItem);
            
            TestMetadataItem inconsistentItem = new TestMetadataItem();
            inconsistentItem.setItemId("inconsistent1");
            inconsistentItem.setItemType("inconsistentType");
            serviceWithDelay.save(inconsistentItem);
            
            // then - isConsistent should return true for TRUE policy, false for FALSE policy
            assertTrue(serviceWithDelay.isConsistent(consistentItem), 
                    "Item with TRUE refresh policy should be consistent (immediately visible)");
            assertFalse(serviceWithDelay.isConsistent(inconsistentItem), 
                    "Item with FALSE refresh policy should not be consistent (not immediately visible)");
        }
        
        @Test
        void shouldHandleRefreshPolicyForCustomItems() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set refresh policy for custom item type
            serviceWithDelay.setRefreshPolicy("customType", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            
            // and - save custom item
            CustomItem customItem = new CustomItem();
            customItem.setItemId("custom1");
            customItem.setItemType("customType");
            customItem.setCustomItemType("customType");
            serviceWithDelay.save(customItem);
            
            // then - custom item should be immediately available
            PartialList<CustomItem> queryResults = serviceWithDelay.queryCustomItem(null, null, "customType", 0, 10, null);
            assertEquals(1, queryResults.getTotalSize(), "Custom item with TRUE refresh policy should be immediately available");
        }
        
        @Test
        void shouldUpdateRefreshPolicyDynamically() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - save item with default FALSE policy
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setItemType("testType");
            serviceWithDelay.save(item);
            
            // then - item should not be immediately available - retry to ensure it's not available
            List<TestMetadataItem> queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                0
            );
            assertTrue(queryResults.isEmpty(), "Item with FALSE policy should not be immediately available");
            
            // when - change refresh policy to TRUE
            serviceWithDelay.setRefreshPolicy("testType", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            
            // and - save another item
            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("test-item2");
            item2.setItemType("testType");
            serviceWithDelay.save(item2);
            
            // then - new item should be immediately available, but old item still needs refresh - retry until available
            // Use retryUntil to check for at least item2, allowing for item1 to potentially be refreshed by background thread
            queryResults = TestHelper.retryUntil(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                r -> r != null && r.size() >= 1 && r.stream().anyMatch(i -> "test-item2".equals(i.getItemId()))
            );
            assertTrue(queryResults.size() >= 1, "New item with TRUE policy should be immediately available");
            assertTrue(queryResults.stream().anyMatch(i -> "test-item2".equals(i.getItemId())),
                    "Item2 should be in results");
        }
        
        @Test
        void shouldRespectRefreshPolicyOnUpdate() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set TRUE refresh policy
            serviceWithDelay.setRefreshPolicy("testType", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            
            // and - save and update an item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setItemType("testType");
            item.setName("Original");
            serviceWithDelay.save(item);
            
            // Update the item
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", "Updated");
            serviceWithDelay.update(item, null, TestMetadataItem.class, updates);
            
            // then - updated item should be immediately available with new value - retry until available
            List<TestMetadataItem> queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, queryResults.size(), "Updated item with TRUE refresh policy should be immediately available");
            assertEquals("Updated", queryResults.get(0).getName(), "Updated value should be visible");
        }
        
        @Test
        void shouldRespectRefreshPolicyOnUpdateWithFalsePolicy() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set FALSE refresh policy
            serviceWithDelay.setRefreshPolicy("testType", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            
            // and - save and update an item
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setItemType("testType");
            item.setName("Original");
            serviceWithDelay.save(item);
            serviceWithDelay.refresh(); // Make initial item available
            
            // Update the item
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", "Updated");
            serviceWithDelay.update(item, null, TestMetadataItem.class, updates);
            
            // then - updated item should not be immediately available (back in pendingRefreshItems)
            List<TestMetadataItem> queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                0
            );
            assertTrue(queryResults.isEmpty(), "Updated item should not be immediately available until refresh");
            
            // when - refresh
            serviceWithDelay.refresh();
            
            // then - updated value should be visible - retry until available
            queryResults = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, queryResults.size(), "Item should be available after refresh");
            assertEquals("Updated", queryResults.get(0).getName(), "Updated value should be visible after refresh");
        }
        
        @Test
        void shouldSupportRequestBasedRefreshPolicyOverride() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set FALSE refresh policy for item type (default behavior)
            serviceWithDelay.setRefreshPolicy("testType", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            
            // and - save item with request-based override to TRUE
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setItemType("testType");
            item.setSystemMetadata("refresh", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            serviceWithDelay.save(item);
            
            // then - item should be immediately available (request override takes precedence)
            List<TestMetadataItem> queryResults = serviceWithDelay.query(null, null, TestMetadataItem.class);
            assertEquals(1, queryResults.size(), "Item with request-based TRUE override should be immediately available");
        }
        
        @Test
        void shouldSupportRequestBasedRefreshPolicyOverrideAsString() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set FALSE refresh policy for item type
            serviceWithDelay.setRefreshPolicy("testType", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            
            // and - save item with request-based override as string (Elasticsearch format)
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setItemType("testType");
            item.setSystemMetadata("refresh", "IMMEDIATE"); // Elasticsearch format
            serviceWithDelay.save(item);
            
            // then - item should be immediately available
            List<TestMetadataItem> queryResults = serviceWithDelay.query(null, null, TestMetadataItem.class);
            assertEquals(1, queryResults.size(), "Item with request-based IMMEDIATE override should be immediately available");
        }
        
        @Test
        void shouldSupportRequestBasedRefreshPolicyOverrideAsBoolean() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set FALSE refresh policy for item type
            serviceWithDelay.setRefreshPolicy("testType", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            
            // and - save item with request-based override as boolean
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setItemType("testType");
            item.setSystemMetadata("refresh", true); // Boolean true
            serviceWithDelay.save(item);
            
            // then - item should be immediately available
            List<TestMetadataItem> queryResults = serviceWithDelay.query(null, null, TestMetadataItem.class);
            assertEquals(1, queryResults.size(), "Item with request-based boolean true override should be immediately available");
        }
        
        @Test
        void shouldSupportRequestBasedRefreshPolicyOverrideWaitFor() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set FALSE refresh policy for item type
            serviceWithDelay.setRefreshPolicy("testType", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            
            // and - save item with request-based override to WAIT_FOR
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setItemType("testType");
            item.setSystemMetadata("refresh", "wait_for"); // OpenSearch/Elasticsearch format
            serviceWithDelay.save(item);
            
            // then - item should be immediately available (WAIT_FOR behaves like TRUE in-memory)
            List<TestMetadataItem> queryResults = serviceWithDelay.query(null, null, TestMetadataItem.class);
            assertEquals(1, queryResults.size(), "Item with request-based wait_for override should be immediately available");
        }
        
        @Test
        void shouldOverridePerItemTypePolicyWithRequestBasedOverride() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set TRUE refresh policy for item type
            serviceWithDelay.setRefreshPolicy("testType", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            
            // and - save item with request-based override to FALSE
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setItemType("testType");
            item.setSystemMetadata("refresh", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            serviceWithDelay.save(item);
            
            // then - item should NOT be immediately available (request override takes precedence)
            List<TestMetadataItem> queryResults = serviceWithDelay.query(null, null, TestMetadataItem.class);
            assertTrue(queryResults.isEmpty(), "Item with request-based FALSE override should not be immediately available");
        }
        
        @Test
        void shouldSupportRequestBasedRefreshPolicyOverrideOnUpdate() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // when - set FALSE refresh policy for item type
            serviceWithDelay.setRefreshPolicy("testType", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            
            // and - save and update item with request-based override
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("test-item");
            item.setItemType("testType");
            item.setName("Original");
            serviceWithDelay.save(item);
            serviceWithDelay.refresh(); // Make initial item available
            
            // Update with request-based refresh override
            item.setSystemMetadata("refresh", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", "Updated");
            serviceWithDelay.update(item, null, TestMetadataItem.class, updates);
            
            // then - updated item should be immediately available
            List<TestMetadataItem> queryResults = serviceWithDelay.query(null, null, TestMetadataItem.class);
            assertEquals(1, queryResults.size(), "Updated item with request-based TRUE override should be immediately available");
            assertEquals("Updated", queryResults.get(0).getName(), "Updated value should be visible");
        }
        
        @Test
        void shouldSupportElasticsearchRefreshParameterValues() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // Test Elasticsearch refresh parameter values: true, false, wait_for
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setItemType("testType");
            item1.setSystemMetadata("refresh", "true");
            serviceWithDelay.save(item1);
            List<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, results1.size(), 
                    "refresh=true should make item immediately available");
            
            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setItemType("testType");
            item2.setSystemMetadata("refresh", "false");
            serviceWithDelay.save(item2);
            // After saving item2 with false, we should still only see item1 (item2 not immediately available)
            List<TestMetadataItem> results2 = TestHelper.retryUntil(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                r -> r != null && r.size() >= 1 && r.stream().anyMatch(i -> "item1".equals(i.getItemId())) &&
                     !r.stream().anyMatch(i -> "item2".equals(i.getItemId()))
            );
            assertTrue(results2.size() >= 1, 
                    "refresh=false should not make item immediately available (only item1 visible)");
            assertTrue(results2.stream().anyMatch(i -> "item1".equals(i.getItemId())),
                    "Item1 should still be visible");
            
            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setItemType("testType");
            item3.setSystemMetadata("refresh", "wait_for");
            serviceWithDelay.save(item3);
            // After saving item3, we should see at least item1 and item3 (item2 still not available)
            List<TestMetadataItem> results3 = TestHelper.retryUntil(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                r -> r != null && r.size() >= 2 && r.stream().anyMatch(i -> "item3".equals(i.getItemId()))
            );
            assertTrue(results3.size() >= 2, 
                    "refresh=wait_for should make item immediately available");
            assertTrue(results3.stream().anyMatch(i -> "item3".equals(i.getItemId())),
                    "Item3 should be visible");
        }
        
        @Test
        void shouldSupportOpenSearchRefreshParameterValues() {
            // given - create persistence service with refresh delay enabled
            InMemoryPersistenceServiceImpl serviceWithDelay = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // Test OpenSearch refresh parameter values: True, False, WaitFor
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setItemType("testType");
            item1.setSystemMetadata("refresh", "True");
            serviceWithDelay.save(item1);
            List<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, results1.size(), 
                    "refresh=True should make item immediately available");
            
            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setItemType("testType");
            item2.setSystemMetadata("refresh", "False");
            serviceWithDelay.save(item2);
            // After saving item2 with False, we should still only see item1 (item2 not immediately available)
            List<TestMetadataItem> results2 = TestHelper.retryUntil(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                r -> r != null && r.size() >= 1 && r.stream().anyMatch(i -> "item1".equals(i.getItemId())) &&
                     !r.stream().anyMatch(i -> "item2".equals(i.getItemId()))
            );
            assertTrue(results2.size() >= 1, 
                    "refresh=False should not make item immediately available");
            assertTrue(results2.stream().anyMatch(i -> "item1".equals(i.getItemId())),
                    "Item1 should still be visible");
            
            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setItemType("testType");
            item3.setSystemMetadata("refresh", "WaitFor");
            serviceWithDelay.save(item3);
            // After saving item3, we should see at least item1 and item3 (item2 still not available)
            List<TestMetadataItem> results3 = TestHelper.retryUntil(
                () -> serviceWithDelay.query(null, null, TestMetadataItem.class),
                r -> r != null && r.size() >= 2 && r.stream().anyMatch(i -> "item3".equals(i.getItemId()))
            );
            assertTrue(results3.size() >= 2, 
                    "refresh=WaitFor should make item immediately available");
            assertTrue(results3.stream().anyMatch(i -> "item3".equals(i.getItemId())),
                    "Item3 should be visible");
        }
        
        @Test
        void shouldHandleAllRefreshPolicyCombinationsWithDelayEnabled() {
            // Test all combinations: per-item-type policy × request override × operation type
            
            // Combination 1: FALSE policy, no override, save
            InMemoryPersistenceServiceImpl service1 = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            service1.setRefreshPolicy("type1", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setItemType("type1");
            service1.save(item1);
            List<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> service1.query(null, null, TestMetadataItem.class),
                0
            );
            assertTrue(results1.isEmpty(), 
                    "FALSE policy, no override, save: should not be immediately available");
            
            // Combination 2: FALSE policy, TRUE override, save
            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setItemType("type1");
            item2.setSystemMetadata("refresh", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            service1.save(item2);
            // After saving item2 with TRUE override, we should see at least item2 (item1 might be refreshed by now)
            List<TestMetadataItem> results2 = TestHelper.retryUntil(
                () -> service1.query(null, null, TestMetadataItem.class),
                r -> r != null && r.size() >= 1 && r.stream().anyMatch(i -> "item2".equals(i.getItemId()))
            );
            assertTrue(results2.size() >= 1, 
                    "FALSE policy, TRUE override, save: should be immediately available");
            assertTrue(results2.stream().anyMatch(i -> "item2".equals(i.getItemId())),
                    "Item2 should be visible");
            
            // Combination 3: TRUE policy, no override, save
            InMemoryPersistenceServiceImpl service2 = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            service2.setRefreshPolicy("type2", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setItemType("type2");
            service2.save(item3);
            List<TestMetadataItem> results3 = TestHelper.retryQueryUntilAvailable(
                () -> service2.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, results3.size(), 
                    "TRUE policy, no override, save: should be immediately available");
            
            // Combination 4: TRUE policy, FALSE override, save
            TestMetadataItem item4 = new TestMetadataItem();
            item4.setItemId("item4");
            item4.setItemType("type2");
            item4.setSystemMetadata("refresh", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            service2.save(item4);
            List<TestMetadataItem> results4 = TestHelper.retryQueryUntilAvailable(
                () -> service2.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, results4.size(), 
                    "TRUE policy, FALSE override, save: override should take precedence (not available)");
            
            // Combination 5: WAIT_FOR policy, no override, save
            InMemoryPersistenceServiceImpl service3 = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            service3.setRefreshPolicy("type3", InMemoryPersistenceServiceImpl.RefreshPolicy.WAIT_FOR);
            TestMetadataItem item5 = new TestMetadataItem();
            item5.setItemId("item5");
            item5.setItemType("type3");
            service3.save(item5);
            List<TestMetadataItem> results5 = TestHelper.retryQueryUntilAvailable(
                () -> service3.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, results5.size(), 
                    "WAIT_FOR policy, no override, save: should be immediately available");
            
            // Combination 6: WAIT_FOR policy, FALSE override, save
            TestMetadataItem item6 = new TestMetadataItem();
            item6.setItemId("item6");
            item6.setItemType("type3");
            item6.setSystemMetadata("refresh", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            service3.save(item6);
            List<TestMetadataItem> results6 = TestHelper.retryQueryUntilAvailable(
                () -> service3.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, results6.size(), 
                    "WAIT_FOR policy, FALSE override, save: override should take precedence (not available)");
        }
        
        @Test
        void shouldHandleAllRefreshPolicyCombinationsOnUpdate() {
            // Test all combinations for update operations
            
            // Combination 1: FALSE policy, no override, update
            InMemoryPersistenceServiceImpl service1 = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            service1.setRefreshPolicy("type1", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setItemType("type1");
            item1.setName("Original");
            service1.save(item1);
            service1.refresh(); // Make initial item available
            
            Map<String, Object> updates1 = new HashMap<>();
            updates1.put("name", "Updated1");
            service1.update(item1, null, TestMetadataItem.class, updates1);
            // After update with FALSE policy, item should not be immediately available
            // (it's back in pendingRefreshItems with future refresh time)
            List<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> service1.query(null, null, TestMetadataItem.class),
                0
            );
            assertTrue(results1.isEmpty(), 
                    "FALSE policy, no override, update: should not be immediately available");
            
            // Combination 2: FALSE policy, TRUE override, update
            // First refresh to make item available again
            service1.refresh();
            item1.setSystemMetadata("refresh", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            Map<String, Object> updates2 = new HashMap<>();
            updates2.put("name", "Updated2");
            service1.update(item1, null, TestMetadataItem.class, updates2);
            List<TestMetadataItem> results2 = TestHelper.retryQueryUntilAvailable(
                () -> service1.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals("Updated2", results2.get(0).getName(), 
                    "FALSE policy, TRUE override, update: should be immediately available");
            
            // Combination 3: TRUE policy, no override, update
            InMemoryPersistenceServiceImpl service2 = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            service2.setRefreshPolicy("type2", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setItemType("type2");
            item2.setName("Original");
            service2.save(item2);
            
            Map<String, Object> updates3 = new HashMap<>();
            updates3.put("name", "Updated3");
            service2.update(item2, null, TestMetadataItem.class, updates3);
            List<TestMetadataItem> results3 = TestHelper.retryQueryUntilAvailable(
                () -> service2.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals("Updated3", results3.get(0).getName(), 
                    "TRUE policy, no override, update: should be immediately available");
        }
        
        @Test
        void shouldHandleRefreshPolicyWithExplicitRefresh() {
            // Test that explicit refresh works regardless of policy
            
            // FALSE policy + explicit refresh
            InMemoryPersistenceServiceImpl service1 = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            service1.setRefreshPolicy("type1", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setItemType("type1");
            service1.save(item1);
            List<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> service1.query(null, null, TestMetadataItem.class),
                0
            );
            assertTrue(results1.isEmpty(), 
                    "FALSE policy: should not be immediately available");
            
            service1.refresh();
            List<TestMetadataItem> results2 = TestHelper.retryQueryUntilAvailable(
                () -> service1.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, results2.size(), 
                    "FALSE policy + explicit refresh: should be available after refresh");
            
            // TRUE policy + explicit refresh (should still work)
            InMemoryPersistenceServiceImpl service2 = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            service2.setRefreshPolicy("type2", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setItemType("type2");
            service2.save(item2);
            List<TestMetadataItem> results3 = TestHelper.retryQueryUntilAvailable(
                () -> service2.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, results3.size(), 
                    "TRUE policy: should be immediately available");
            
            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setItemType("type2");
            service2.save(item3);
            service2.refresh(); // Explicit refresh should work
            List<TestMetadataItem> results4 = TestHelper.retryQueryUntilAvailable(
                () -> service2.query(null, null, TestMetadataItem.class),
                2
            );
            assertEquals(2, results4.size(), 
                    "TRUE policy + explicit refresh: should still work");
        }
        
        @Test
        void shouldHandleRefreshPolicyWithRefreshIndex() {
            // Test that refreshIndex works regardless of policy
            
            // FALSE policy + refreshIndex
            InMemoryPersistenceServiceImpl service1 = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            service1.setRefreshPolicy("type1", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setItemType("type1");
            service1.save(item1);
            List<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> service1.query(null, null, TestMetadataItem.class),
                0
            );
            assertTrue(results1.isEmpty(), 
                    "FALSE policy: should not be immediately available");
            
            service1.refreshIndex(TestMetadataItem.class, null);
            List<TestMetadataItem> results2 = TestHelper.retryQueryUntilAvailable(
                () -> service1.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, results2.size(), 
                    "FALSE policy + refreshIndex: should be available after refreshIndex");
        }
        
        @Test
        void shouldHandleRefreshPolicyWithAutomaticRefresh() throws InterruptedException {
            // Test that automatic refresh works with different policies
            
            // FALSE policy + automatic refresh
            InMemoryPersistenceServiceImpl service1 = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher,
                    InMemoryPersistenceServiceImpl.DEFAULT_STORAGE_DIR, true, true, true, true, 100L);
            service1.setRefreshPolicy("type1", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setItemType("type1");
            service1.save(item1);
            List<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> service1.query(null, null, TestMetadataItem.class),
                0
            );
            assertTrue(results1.isEmpty(), 
                    "FALSE policy: should not be immediately available");
            
            Thread.sleep(150); // Wait for automatic refresh
            List<TestMetadataItem> results2 = TestHelper.retryQueryUntilAvailable(
                () -> service1.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, results2.size(), 
                    "FALSE policy + automatic refresh: should be available after interval");
        }
        
        @Test
        void shouldHandleRefreshPolicyWhenDelayDisabled() {
            // Test that refresh policies are ignored when delay simulation is disabled
            
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher,
                    InMemoryPersistenceServiceImpl.DEFAULT_STORAGE_DIR, true, true, true, false, 1000L);
            
            // Set FALSE policy, but delay is disabled
            service.setRefreshPolicy("type1", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setItemType("type1");
            service.save(item);
            
            // Should be immediately available regardless of policy when delay is disabled
            List<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> service.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, results.size(), 
                    "When delay disabled: should be immediately available regardless of policy");
        }
        
        @Test
        void shouldHandleRequestOverrideWhenDelayDisabled() {
            // Test that request overrides are ignored when delay simulation is disabled
            
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher,
                    InMemoryPersistenceServiceImpl.DEFAULT_STORAGE_DIR, true, true, true, false, 1000L);
            
            // Set request override, but delay is disabled
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setItemType("type1");
            item.setSystemMetadata("refresh", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            service.save(item);
            
            // Should be immediately available regardless of override when delay is disabled
            List<TestMetadataItem> results = TestHelper.retryQueryUntilAvailable(
                () -> service.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, results.size(), 
                    "When delay disabled: should be immediately available regardless of override");
        }
        
        @Test
        void shouldHandleAllRequestOverrideFormats() {
            // Test all possible request override formats
            
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            service.setRefreshPolicy("testType", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            
            // Format 1: Enum value
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setItemType("testType");
            item1.setSystemMetadata("refresh", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            service.save(item1);
            List<TestMetadataItem> results1 = TestHelper.retryQueryUntilAvailable(
                () -> service.query(null, null, TestMetadataItem.class),
                1
            );
            assertEquals(1, results1.size(), 
                    "Enum override: should work");
            
            // Format 2: String "true"
            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setItemType("testType");
            item2.setSystemMetadata("refresh", "true");
            service.save(item2);
            List<TestMetadataItem> results2 = TestHelper.retryQueryUntilAvailable(
                () -> service.query(null, null, TestMetadataItem.class),
                2
            );
            assertEquals(2, results2.size(), 
                    "String 'true' override: should work");
            
            // Format 3: String "IMMEDIATE"
            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setItemType("testType");
            item3.setSystemMetadata("refresh", "IMMEDIATE");
            service.save(item3);
            List<TestMetadataItem> results3 = TestHelper.retryQueryUntilAvailable(
                () -> service.query(null, null, TestMetadataItem.class),
                3
            );
            assertEquals(3, results3.size(), 
                    "String 'IMMEDIATE' override: should work");
            
            // Format 4: Boolean true
            TestMetadataItem item4 = new TestMetadataItem();
            item4.setItemId("item4");
            item4.setItemType("testType");
            item4.setSystemMetadata("refresh", true);
            service.save(item4);
            List<TestMetadataItem> results4 = TestHelper.retryQueryUntilAvailable(
                () -> service.query(null, null, TestMetadataItem.class),
                4
            );
            assertEquals(4, results4.size(), 
                    "Boolean true override: should work");
            
            // Format 5: String "wait_for"
            TestMetadataItem item5 = new TestMetadataItem();
            item5.setItemId("item5");
            item5.setItemType("testType");
            item5.setSystemMetadata("refresh", "wait_for");
            service.save(item5);
            List<TestMetadataItem> results5 = TestHelper.retryQueryUntilAvailable(
                () -> service.query(null, null, TestMetadataItem.class),
                5
            );
            assertEquals(5, results5.size(), 
                    "String 'wait_for' override: should work");
            
            // Format 6: String "WaitFor"
            TestMetadataItem item6 = new TestMetadataItem();
            item6.setItemId("item6");
            item6.setItemType("testType");
            item6.setSystemMetadata("refresh", "WaitFor");
            service.save(item6);
            List<TestMetadataItem> results6 = TestHelper.retryQueryUntilAvailable(
                () -> service.query(null, null, TestMetadataItem.class),
                6
            );
            assertEquals(6, results6.size(), 
                    "String 'WaitFor' override: should work");
        }
        
        @Test
        void shouldHandleIsConsistentWithAllCombinations() {
            // Test isConsistent() with all policy combinations
            
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // FALSE policy, no override
            service.setRefreshPolicy("type1", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setItemType("type1");
            assertFalse(service.isConsistent(item1), 
                    "FALSE policy, no override: should not be consistent");
            
            // FALSE policy, TRUE override
            item1.setSystemMetadata("refresh", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            assertTrue(service.isConsistent(item1), 
                    "FALSE policy, TRUE override: should be consistent");
            
            // TRUE policy, no override
            service.setRefreshPolicy("type2", InMemoryPersistenceServiceImpl.RefreshPolicy.TRUE);
            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setItemType("type2");
            assertTrue(service.isConsistent(item2), 
                    "TRUE policy, no override: should be consistent");
            
            // TRUE policy, FALSE override
            item2.setSystemMetadata("refresh", InMemoryPersistenceServiceImpl.RefreshPolicy.FALSE);
            assertFalse(service.isConsistent(item2), 
                    "TRUE policy, FALSE override: should not be consistent");
            
            // WAIT_FOR policy, no override
            service.setRefreshPolicy("type3", InMemoryPersistenceServiceImpl.RefreshPolicy.WAIT_FOR);
            TestMetadataItem item3 = new TestMetadataItem();
            item3.setItemId("item3");
            item3.setItemType("type3");
            assertTrue(service.isConsistent(item3), 
                    "WAIT_FOR policy, no override: should be consistent");
        }
    }
    
    @Nested
    class DefaultQueryLimitTests {
        @Test
        void shouldApplyDefaultLimitWhenSizeIsNegative() {
            // Create service with default limit of 10
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // Save 20 items
            for (int i = 0; i < 20; i++) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + i);
                service.save(item);
            }
            
            // Query with size = -1 should return default limit (10) - retry until items are available
            PartialList<TestMetadataItem> result = TestHelper.retryQueryUntilAvailable(
                () -> service.query(null, null, TestMetadataItem.class, 0, -1),
                10
            );
            assertEquals(10, result.getList().size(), "Query with size=-1 should return default limit of 10");
            assertEquals(20, result.getTotalSize(), "Total size should be 20");
        }
        
        @Test
        void shouldApplyDefaultLimitWhenSizeIsNotSpecified() {
            // Create service with default limit of 10
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // Save 20 items
            for (int i = 0; i < 20; i++) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + i);
                service.save(item);
            }
            
            // Wait for all 20 items to be available before checking totalSize
            // The totalSize is calculated from items that are currently queryable (refreshed)
            // So we need to ensure all items are refreshed before verifying totalSize
            PartialList<TestMetadataItem> result = TestHelper.retryUntil(
                () -> service.query(null, null, TestMetadataItem.class, 0, -1, null),
                r -> r != null && r.getTotalSize() == 20 && r.getList().size() == 10
            );
            assertEquals(10, result.getList().size(), "Query with size=-1 should return default limit of 10");
            assertEquals(20, result.getTotalSize(), "Total size should be 20");
        }
        
        @Test
        void shouldRespectExplicitSizeWhenProvided() {
            // Create service with default limit of 10
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // Save 20 items
            for (int i = 0; i < 20; i++) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + i);
                service.save(item);
            }
            
            // Query with explicit size should respect it - retry until items are available
            PartialList<TestMetadataItem> result = TestHelper.retryQueryUntilAvailable(
                () -> service.query(null, null, TestMetadataItem.class, 0, 5),
                5
            );
            assertEquals(5, result.getList().size(), "Query with explicit size=5 should return 5 items");
            assertEquals(20, result.getTotalSize(), "Total size should be 20");
        }
        
        @Test
        void shouldAllowCustomDefaultLimit() {
            // Create service with custom default limit of 5
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher,
                    InMemoryPersistenceServiceImpl.DEFAULT_STORAGE_DIR, true, true, true, true, 1000L, 5);
            
            // Save 20 items
            for (int i = 0; i < 20; i++) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + i);
                service.save(item);
            }
            
            // Query with size = -1 should return custom default limit (5) - retry until items are available
            PartialList<TestMetadataItem> result = TestHelper.retryQueryUntilAvailable(
                () -> service.query(null, null, TestMetadataItem.class, 0, -1),
                5
            );
            assertEquals(5, result.getList().size(), "Query with size=-1 should return custom default limit of 5");
            assertEquals(20, result.getTotalSize(), "Total size should be 20");
        }
        
        @Test
        void shouldApplyDefaultLimitInCreatePartialList() {
            // Create service with default limit of 10
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // Save 20 items with name property set
            for (int i = 0; i < 20; i++) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + i);
                item.setName("test"); // Set name so the query will match
                service.save(item);
            }
            
            // Query with field matching and size = -1 - retry until items are available
            PartialList<TestMetadataItem> result = TestHelper.retryQueryUntilAvailable(
                () -> service.query("name", "test", null, TestMetadataItem.class, 0, -1),
                10
            );
            assertEquals(10, result.getList().size(), "Query with size=-1 should return default limit of 10");
        }
        
        @Test
        void shouldApplyDefaultLimitInRangeQuery() {
            // Create service with default limit of 10
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // Save 20 items with numeric values
            for (int i = 0; i < 20; i++) {
                TestMetadataItem item = new TestMetadataItem();
                item.setItemId("item" + i);
                item.setNumericValue((double) i);
                service.save(item);
            }
            
            // Wait for all 20 items to be available before checking totalSize
            // The totalSize is calculated from items that are currently queryable (refreshed)
            // So we need to ensure all items are refreshed before verifying totalSize
            PartialList<TestMetadataItem> result = TestHelper.retryUntil(
                () -> service.rangeQuery("numericValue", "0", "20", null, TestMetadataItem.class, 0, -1),
                r -> r != null && r.getTotalSize() == 20 && r.getList().size() == 10
            );
            assertEquals(10, result.getList().size(), "Range query with size=-1 should return default limit of 10");
            assertEquals(20, result.getTotalSize(), "Total size should be 20");
        }
        
        @Test
        void shouldGetAndSetDefaultQueryLimit() {
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            assertEquals(10, service.getDefaultQueryLimit(), "Default limit should be 10");
            
            service.setDefaultQueryLimit(15);
            assertEquals(15, service.getDefaultQueryLimit(), "Default limit should be 15 after setting");
            
            // Setting invalid value should default to 10
            service.setDefaultQueryLimit(0);
            assertEquals(10, service.getDefaultQueryLimit(), "Invalid limit should default to 10");
            
            service.setDefaultQueryLimit(-5);
            assertEquals(10, service.getDefaultQueryLimit(), "Negative limit should default to 10");
        }
    }
    
    @Nested
    class TenantTransformationTests {
        // Mock transformation listener for testing
        static class TestTransformationListener implements org.apache.unomi.api.tenants.TenantTransformationListener {
            private final String transformationType;
            private final boolean enabled;
            private final int priority;
            private boolean transformCalled = false;
            private boolean reverseTransformCalled = false;
            
            TestTransformationListener(String transformationType, boolean enabled, int priority) {
                this.transformationType = transformationType;
                this.enabled = enabled;
                this.priority = priority;
            }
            
            @Override
            public Item transformItem(Item item, String tenantId) {
                transformCalled = true;
                if (item instanceof TestMetadataItem) {
                    TestMetadataItem testItem = (TestMetadataItem) item;
                    testItem.setName(testItem.getName() + "_transformed");
                    testItem.setSystemMetadata("transformed", true);
                }
                return item;
            }
            
            @Override
            public boolean isTransformationEnabled() {
                return enabled;
            }
            
            @Override
            public Item reverseTransformItem(Item item, String tenantId) {
                reverseTransformCalled = true;
                if (item instanceof TestMetadataItem) {
                    TestMetadataItem testItem = (TestMetadataItem) item;
                    String name = testItem.getName();
                    if (name != null && name.endsWith("_transformed")) {
                        testItem.setName(name.substring(0, name.length() - "_transformed".length()));
                        testItem.setSystemMetadata("transformed", false);
                    }
                }
                return item;
            }
            
            @Override
            public String getTransformationType() {
                return transformationType;
            }
            
            @Override
            public int getPriority() {
                return priority;
            }
            
            boolean wasTransformCalled() {
                return transformCalled;
            }
            
            boolean wasReverseTransformCalled() {
                return reverseTransformCalled;
            }
            
            void reset() {
                transformCalled = false;
                reverseTransformCalled = false;
            }
        }
        
        @Test
        void shouldTransformItemBeforeSave() {
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            TestTransformationListener listener = new TestTransformationListener("test", true, 0);
            service.addTransformationListener(listener);
            
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setName("original");
            
            service.save(item);
            
            assertTrue(listener.wasTransformCalled(), "Transform should be called before save");
            
            // Load the item - reverse transformation should undo the transformation
            TestMetadataItem loaded = service.load("item1", TestMetadataItem.class);
            assertNotNull(loaded, "Item should be loaded");
            assertTrue(listener.wasReverseTransformCalled(), "Reverse transform should be called after load");
            // After reverse transformation, the name should be back to original
            assertEquals("original", loaded.getName(), "Item should be reverse transformed after load");
            // But the item should have been transformed when stored (check system metadata)
            // Note: The transformation is applied before save, so the stored item has "_transformed" suffix
            // The reverse transformation on load removes it, which is the expected behavior
        }
        
        @Test
        void shouldReverseTransformItemAfterLoad() {
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            TestTransformationListener listener = new TestTransformationListener("test", true, 0);
            service.addTransformationListener(listener);
            
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setName("original");
            item.setSystemMetadata("transformed", true);
            
            // Save transformed item directly (simulating what would be stored)
            service.save(item);
            listener.reset();
            
            // Load the item and verify reverse transformation is called
            TestMetadataItem loaded = service.load("item1", TestMetadataItem.class);
            assertNotNull(loaded, "Item should be loaded");
            assertTrue(listener.wasReverseTransformCalled(), "Reverse transform should be called after load");
        }
        
        @Test
        void shouldNotTransformWhenListenerIsDisabled() {
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            TestTransformationListener listener = new TestTransformationListener("test", false, 0);
            service.addTransformationListener(listener);
            
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setName("original");
            
            service.save(item);
            
            assertFalse(listener.wasTransformCalled(), "Transform should not be called when listener is disabled");
            
            TestMetadataItem loaded = service.load("item1", TestMetadataItem.class);
            assertNotNull(loaded, "Item should be loaded");
            assertEquals("original", loaded.getName(), "Item should not be transformed");
        }
        
        @Test
        void shouldApplyMultipleTransformationsInPriorityOrder() {
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            TestTransformationListener listener1 = new TestTransformationListener("test1", true, 10);
            TestTransformationListener listener2 = new TestTransformationListener("test2", true, 5);
            TestTransformationListener listener3 = new TestTransformationListener("test3", true, 1);
            
            service.addTransformationListener(listener1);
            service.addTransformationListener(listener2);
            service.addTransformationListener(listener3);
            
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setName("original");
            
            service.save(item);
            
            // All listeners should be called
            assertTrue(listener1.wasTransformCalled(), "Listener 1 should be called");
            assertTrue(listener2.wasTransformCalled(), "Listener 2 should be called");
            assertTrue(listener3.wasTransformCalled(), "Listener 3 should be called");
            
            // Item should be transformed multiple times before save, then reverse transformed on load
            TestMetadataItem loaded = service.load("item1", TestMetadataItem.class);
            assertNotNull(loaded, "Item should be loaded");
            // Reverse transformation should undo all transformations, so we get back to original
            assertEquals("original", loaded.getName(), 
                    "Item should be reverse transformed back to original after load");
            // All reverse transforms should have been called
            assertTrue(listener1.wasReverseTransformCalled(), "Reverse transform 1 should be called");
            assertTrue(listener2.wasReverseTransformCalled(), "Reverse transform 2 should be called");
            assertTrue(listener3.wasReverseTransformCalled(), "Reverse transform 3 should be called");
        }
        
        @Test
        void shouldTransformItemBeforeUpdate() {
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            TestTransformationListener listener = new TestTransformationListener("test", true, 0);
            service.addTransformationListener(listener);
            
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setName("original");
            service.save(item);
            listener.reset();
            
            // Update the item
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", "updated");
            service.update(item, null, TestMetadataItem.class, updates);
            
            assertTrue(listener.wasTransformCalled(), "Transform should be called before update");
        }
        
        @Test
        void shouldHandleTransformationErrorsGracefully() {
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            // Create a listener that throws an exception
            org.apache.unomi.api.tenants.TenantTransformationListener errorListener = 
                    new org.apache.unomi.api.tenants.TenantTransformationListener() {
                        @Override
                        public Item transformItem(Item item, String tenantId) {
                            throw new RuntimeException("Transformation error");
                        }
                        
                        @Override
                        public boolean isTransformationEnabled() {
                            return true;
                        }
                        
                        @Override
                        public Item reverseTransformItem(Item item, String tenantId) {
                            throw new RuntimeException("Reverse transformation error");
                        }
                        
                        @Override
                        public String getTransformationType() {
                            return "error";
                        }
                    };
            
            service.addTransformationListener(errorListener);
            
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setName("original");
            
            // Should not throw exception, but log error
            assertDoesNotThrow(() -> service.save(item), "Should handle transformation errors gracefully");
            
            // Item should still be saved
            TestMetadataItem loaded = service.load("item1", TestMetadataItem.class);
            assertNotNull(loaded, "Item should still be saved despite transformation error");
        }
        
        @Test
        void shouldRemoveTransformationListener() {
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            TestTransformationListener listener = new TestTransformationListener("test", true, 0);
            service.addTransformationListener(listener);
            
            TestMetadataItem item = new TestMetadataItem();
            item.setItemId("item1");
            item.setName("original");
            service.save(item);
            assertTrue(listener.wasTransformCalled(), "Transform should be called");
            
            // Remove listener
            service.removeTransformationListener(listener);
            listener.reset();
            
            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setName("original2");
            service.save(item2);
            
            assertFalse(listener.wasTransformCalled(), "Transform should not be called after removal");
        }
        
        @Test
        void shouldTransformCustomItems() {
            InMemoryPersistenceServiceImpl service = new InMemoryPersistenceServiceImpl(
                    executionContextManager, conditionEvaluatorDispatcher);
            
            TestTransformationListener listener = new TestTransformationListener("test", true, 0);
            service.addTransformationListener(listener);
            
            CustomItem customItem = new CustomItem();
            customItem.setItemId("custom1");
            customItem.setItemType("customType");
            Map<String, Object> properties = new HashMap<>();
            properties.put("name", "original");
            customItem.setProperties(properties);
            
            service.save(customItem);
            
            assertTrue(listener.wasTransformCalled(), "Transform should be called for custom items");
        }
    }
}
