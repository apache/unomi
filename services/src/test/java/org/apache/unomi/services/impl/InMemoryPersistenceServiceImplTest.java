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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPersistenceServiceImplTest {

    private InMemoryPersistenceServiceImpl persistenceService;
    private TestTenantService testTenantService;

    @BeforeEach
    void setUp() {
        testTenantService = new TestTenantService();
        persistenceService = new InMemoryPersistenceServiceImpl(testTenantService);
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

    // Test helper class
    public static class TestMetadataItem extends MetadataItem {

        public static final String ITEM_TYPE = "testMetadataItem";

        private Metadata metadata;

        @Override
        public Metadata getMetadata() {
            return metadata;
        }

        public void setMetadata(Metadata metadata) {
            this.metadata = metadata;
        }
    }
}
