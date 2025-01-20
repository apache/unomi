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

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPersistenceServiceImplTest {

    private InMemoryPersistenceServiceImpl persistenceService;
    private TestTenantService testTenantService;
    private ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;

    // Test helper class
    public static class TestMetadataItem extends MetadataItem {
        public static final String ITEM_TYPE = "testMetadataItem";
        private Metadata metadata;
        private Map<String, Object> properties = new HashMap<>();
        private String name;
        private Set<String> tags;

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
    }

    @BeforeEach
    void setUp() {
        testTenantService = new TestTenantService();
        conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();
        persistenceService = new InMemoryPersistenceServiceImpl(testTenantService, conditionEvaluatorDispatcher);
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
            condition.setConditionType(TestConditionEvaluators.getConditionType("propertyCondition"));
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
        void shouldReturnFalseForNullCondition() {
            // given
            Profile profile = new Profile();
            profile.setItemId("test-profile");

            // when
            boolean result = persistenceService.testMatch(null, profile);

            // then
            assertFalse(result);
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
}
