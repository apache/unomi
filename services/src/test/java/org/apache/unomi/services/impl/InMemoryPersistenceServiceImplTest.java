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

import org.apache.commons.io.FileUtils;
import org.apache.unomi.api.*;
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
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.impl.tenants.AuditServiceImpl;
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

import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;
import static org.junit.jupiter.api.Assertions.*;

public class InMemoryPersistenceServiceImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryPersistenceServiceImplTest.class);
    private InMemoryPersistenceServiceImpl persistenceService;
    private ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private ExecutionContextManagerImpl executionContextManager;
    private KarafSecurityService securityService;
    private AuditServiceImpl auditService;

    // Test helper class
    public static class TestMetadataItem extends MetadataItem {
        public static final String ITEM_TYPE = "testMetadataItem";
        private Metadata metadata;
        private Map<String, Object> properties = new HashMap<>();
        private String name;
        private Set<String> tags;
        private Double numericValue;

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
        Path defaultStorageDir = Paths.get(InMemoryPersistenceServiceImpl.DEFAULT_STORAGE_DIR).toAbsolutePath().normalize();
        FileUtils.deleteDirectory(defaultStorageDir.toFile());
        conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();
        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);
        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);
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

            // when
            List<TestMetadataItem> results = persistenceService.query("name", "test-name", null, TestMetadataItem.class);

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

            // when
            List<TestMetadataItem> results = persistenceService.query("properties.nested\\.field", "test-value", null, TestMetadataItem.class);

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

            // when
            List<TestMetadataItem> results = persistenceService.query("metadata.name", "test-metadata-name", null, TestMetadataItem.class);

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

            // when
            List<TestMetadataItem> results = persistenceService.query("tags", "tag1", null, TestMetadataItem.class);

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

            // when
            List<TestMetadataItem> results1 = persistenceService.query("nonexistent", "any-value", null, TestMetadataItem.class);
            List<TestMetadataItem> results2 = persistenceService.query("properties.map.nested.field", "any-value", null, TestMetadataItem.class);
            List<TestMetadataItem> results3 = persistenceService.query("nonexistent.nested.field", "any-value", null, TestMetadataItem.class);

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

            // Test different notation styles
            List<TestMetadataItem> results1 = persistenceService.query("properties.direct\\.key\\.with\\.dots", "direct-value", null, TestMetadataItem.class);
            List<TestMetadataItem> results2 = persistenceService.query("properties.map[nested.field][key.with.dots]", "test-value", null, TestMetadataItem.class);
            List<TestMetadataItem> results3 = persistenceService.query("properties.map.nested\\.field.regular\\.key", "another-value", null, TestMetadataItem.class);

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

            // Verify all special field types
            List<TestMetadataItem> results1 = persistenceService.query("properties.active", "true", null, TestMetadataItem.class);
            List<TestMetadataItem> results2 = persistenceService.query("tags", "tag1", null, TestMetadataItem.class);
            List<TestMetadataItem> results3 = persistenceService.query("properties.nested.list\\.of\\.items", "item1", null, TestMetadataItem.class);

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

            // when - initial query with scroll
            PartialList<Profile> firstPage = persistenceService.query(null, null, Profile.class, 0, 10, "1000");

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

            // when - initial query with very short scroll validity
            PartialList<Profile> firstPage = persistenceService.query(null, null, Profile.class, 0, 1, "1");

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

            // when - initial query with scroll
            PartialList<Profile> firstPage = persistenceService.query(condition, null, Profile.class, 0, 3, "1000");

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

            // when - initial query with scroll and page size equal to total items
            PartialList<Profile> firstPage = persistenceService.query(null, null, Profile.class, 0, 10, "1000");

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

            // when - start two scroll queries
            PartialList<Profile> firstScroll = persistenceService.query(null, null, Profile.class, 0, 4, "1000");
            PartialList<Profile> secondScroll = persistenceService.query(null, null, Profile.class, 0, 3, "1000");

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

            // when
            TermsAggregate termsAggregate = new TermsAggregate("properties.category");
            Map<String, Long> results = persistenceService.aggregateWithOptimizedQuery(null, termsAggregate, Profile.ITEM_TYPE);

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

            // when
            DateRangeAggregate dateRangeAggregate = new DateRangeAggregate("properties.lastVisit", "yyyy-MM-dd", ranges);
            Map<String, Long> results = persistenceService.aggregateWithOptimizedQuery(null, dateRangeAggregate, Profile.ITEM_TYPE);

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

            // when
            NumericRangeAggregate numericRangeAggregate = new NumericRangeAggregate("properties.age", ranges);
            Map<String, Long> results = persistenceService.aggregateWithOptimizedQuery(null, numericRangeAggregate, Profile.ITEM_TYPE);

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

            // when
            IpRangeAggregate ipRangeAggregate = new IpRangeAggregate("properties.ipAddress", ranges);
            Map<String, Long> results = persistenceService.aggregateWithOptimizedQuery(null, ipRangeAggregate, Profile.ITEM_TYPE);

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

            // when
            TermsAggregate termsAggregate = new TermsAggregate("properties.category");
            Map<String, Long> results = persistenceService.aggregateWithOptimizedQuery(null, termsAggregate, Profile.ITEM_TYPE, 2);

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

            // when
            TermsAggregate termsAggregate = new TermsAggregate("properties.category");
            Map<String, Long> results = persistenceService.aggregateWithOptimizedQuery(condition, termsAggregate, Profile.ITEM_TYPE);

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

            // when
            long count = persistenceService.queryCount(null, Profile.ITEM_TYPE);

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

            // when
            long count = persistenceService.queryCount(condition, Profile.ITEM_TYPE);

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

            // when
            PartialList<TestMetadataItem> results = persistenceService.queryFullText("match", null, TestMetadataItem.class, 0, 10);

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

            // when
            PartialList<TestMetadataItem> results = persistenceService.queryFullText(
                "properties.category", "electronics", "matching", null, TestMetadataItem.class, 0, 10);

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

            // when
            PartialList<TestMetadataItem> results = persistenceService.queryFullText(
                "test", condition, null, TestMetadataItem.class, 0, 10);

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

            // when
            PartialList<TestMetadataItem> results = persistenceService.queryFullText(
                "nested value", null, TestMetadataItem.class, 0, 10);

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

            // when
            PartialList<TestMetadataItem> page1 = persistenceService.queryFullText(
                "test", null, TestMetadataItem.class, 0, 2);
            PartialList<TestMetadataItem> page2 = persistenceService.queryFullText(
                "test", null, TestMetadataItem.class, 2, 2);
            PartialList<TestMetadataItem> page3 = persistenceService.queryFullText(
                "test", null, TestMetadataItem.class, 4, 2);

            // then
            assertEquals(2, page1.getList().size());
            assertEquals(2, page2.getList().size());
            assertEquals(1, page3.getList().size());
            assertEquals(5, page1.getTotalSize());
            assertEquals(5, page2.getTotalSize());
            assertEquals(5, page3.getTotalSize());
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

            // when
            PartialList<TestMetadataItem> results = persistenceService.queryFullText(
                "nonexistent", null, TestMetadataItem.class, 0, 10);

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

            // when
            PartialList<TestMetadataItem> results1 = persistenceService.queryFullText(
                "test", null, TestMetadataItem.class, 0, 10);
            PartialList<TestMetadataItem> results2 = persistenceService.queryFullText(
                "TEST", null, TestMetadataItem.class, 0, 10);
            PartialList<TestMetadataItem> results3 = persistenceService.queryFullText(
                "tEsT", null, TestMetadataItem.class, 0, 10);

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

                // when - search in string property
                PartialList<SimpleItem> results1 = persistenceService.queryFullText(
                    "searchable", null, SimpleItem.class, 0, 10);

                // when - search in numeric property
                PartialList<SimpleItem> results2 = persistenceService.queryFullText(
                    "42", null, SimpleItem.class, 0, 10);

                // when - search in boolean property
                PartialList<SimpleItem> results3 = persistenceService.queryFullText(
                    "true", null, SimpleItem.class, 0, 10);

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

                // when - search in first level
                PartialList<NestedItem> results1 = persistenceService.queryFullText(
                    "searchable", null, NestedItem.class, 0, 10);

                // when - search in nested level
                PartialList<NestedItem> results2 = persistenceService.queryFullText(
                    "nested searchable", null, NestedItem.class, 0, 10);

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

                // when - search in string list
                PartialList<NestedItem> results1 = persistenceService.queryFullText(
                    "second", null, NestedItem.class, 0, 10);

                // when - search in complex set
                PartialList<NestedItem> results2 = persistenceService.queryFullText(
                    "searchable value", null, NestedItem.class, 0, 10);

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

                // when - search in property names
                PartialList<NestedItem> results = persistenceService.queryFullText(
                    "searchable_key", null, NestedItem.class, 0, 10);

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

                // when
                PartialList<NestedItem> results = persistenceService.queryFullText(
                    "searchable", null, NestedItem.class, 0, 10);

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

                // when
                PartialList<SimpleItem> results = persistenceService.queryFullText(
                    "!@#$%", null, SimpleItem.class, 0, 10);

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

                // when
                PartialList<NestedItem> results = persistenceService.queryFullText(
                    "nonexistent", null, NestedItem.class, 0, 10);

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

                // when - search across different item types
                PartialList<SimpleItem> results1 = persistenceService.queryFullText(
                    "common", null, SimpleItem.class, 0, 10);
                PartialList<NestedItem> results2 = persistenceService.queryFullText(
                    "common", null, NestedItem.class, 0, 10);

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

                // when
                PartialList<NestedItem> results = persistenceService.queryFullText(
                    "searchable", null, NestedItem.class, 0, 10);

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

            // when
            Map<String, Double> results = persistenceService.getSingleValuesMetrics(
                null, metrics, "numericValue", TestMetadataItem.ITEM_TYPE);

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

            // when
            Map<String, Double> results = persistenceService.getSingleValuesMetrics(
                null, metrics, "numericValue", TestMetadataItem.ITEM_TYPE);

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

            // when
            Map<String, Double> results = persistenceService.getSingleValuesMetrics(
                condition, metrics, "numericValue", TestMetadataItem.ITEM_TYPE);

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

            // when
            Map<String, Double> results = persistenceService.getSingleValuesMetrics(
                null, metrics, "properties.stringValue", TestMetadataItem.ITEM_TYPE);

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

            // when
            Map<String, Double> results = persistenceService.getSingleValuesMetrics(
                null, metrics, "numericValue", TestMetadataItem.ITEM_TYPE);

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

            // when
            Map<String, Double> results = persistenceService.getSingleValuesMetrics(
                null, metrics, "numericValue", TestMetadataItem.ITEM_TYPE);

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

            // when
            Map<String, Double> results = persistenceService.getSingleValuesMetrics(
                null, metrics, "properties.nested.value", TestMetadataItem.ITEM_TYPE);

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

            // when - query with size = -1
            PartialList<Profile> result = persistenceService.query(null, null, Profile.class, 0, -1);

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

            // when - query with size = -1 and offset = 5
            PartialList<Profile> result = persistenceService.query(null, null, Profile.class, 5, -1);

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

            // when - initial query with scroll and size = -1
            PartialList<Profile> result = persistenceService.query(null, null, Profile.class, 0, -1, "1000");

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

            // when - getAllItems with size = -1
            PartialList<Profile> result = persistenceService.getAllItems(Profile.class, 0, -1, null);

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
            Path expectedPath = tempDir.resolve("org_apache_unomi_api_Profile")
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
            Path expectedPath = tempDir.resolve("org.apache.unomi.api.Profile")
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
            Path expectedPath = tempDir.resolve("org_apache_unomi_api_Profile")
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

            Path expectedPath = tempDir.resolve("org_apache_unomi_api_Profile")
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

            Path tenantDir = tempDir.resolve("org_apache_unomi_api_Profile")
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
            assertTrue(Files.exists(tempDir.resolve("org_apache_unomi_api_Profile")
                .resolve(SYSTEM_TENANT)
                .resolve("profile1.json")));
            assertTrue(Files.exists(tempDir.resolve("org_apache_unomi_api_Profile")
                .resolve("tenant2")
                .resolve("profile2.json")));
            assertTrue(Files.exists(tempDir.resolve(TestMetadataItem.class.getName().replaceAll("\\$", "_").replaceAll("\\.", "_"))
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
                .resolve(TestMetadataItem.class.getName().replace('.', '_').replaceAll("\\$", "_"))
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
            conditionType.setQueryBuilder("matchAllConditionESQueryBuilder");
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
            TestMetadataItem item1 = new TestMetadataItem();
            item1.setItemId("item1");
            item1.setNumericValue(10.0);

            TestMetadataItem item2 = new TestMetadataItem();
            item2.setItemId("item2");
            item2.setNumericValue(20.0);

            persistenceService.save(item1);
            persistenceService.save(item2);

            PartialList<TestMetadataItem> results = persistenceService.rangeQuery(
                "numericValue", "15", "25", null, TestMetadataItem.class, 0, 10);

            assertNotNull(results);
            assertEquals(0, results.size()); // In-memory implementation returns empty list
        }
    }

    @Nested
    class FileStorageConcurrencyTests {
        @Test
        void shouldHandleConcurrentFileOperations() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                new Thread(() -> {
                    try {
                        TestMetadataItem item = new TestMetadataItem();
                        item.setItemId("concurrent-item-" + index);
                        persistenceService.save(item);
                        persistenceService.load(item.getItemId(), TestMetadataItem.class);
                        persistenceService.remove(item.getItemId(), TestMetadataItem.class);
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await(5, TimeUnit.SECONDS);
            assertTrue(exceptions.isEmpty(), "Concurrent operations should not throw exceptions");
        }

    }
}
