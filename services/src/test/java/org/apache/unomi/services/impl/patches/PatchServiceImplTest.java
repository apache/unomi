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
package org.apache.unomi.services.impl.patches;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Patch;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl;
import org.apache.unomi.services.impl.TestConditionEvaluators;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the embedded json-patch library through {@link PatchServiceImpl}, with Unomi's
 * Jackson configuration.
 */
class PatchServiceImplTest {

    private static final String TENANT = "test-tenant";
    private static final String GOAL_ID = "patchedGoal";

    private ExecutionContextManagerImpl executionContextManager;
    private PersistenceService persistenceService;
    private MultiTypeCacheServiceImpl cacheService;
    private PatchServiceImpl patchService;

    @BeforeEach
    void setUp() {
        executionContextManager = TestHelper.createExecutionContextManager(TestHelper.createSecurityService());
        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, TestConditionEvaluators.createDispatcher());
        cacheService = new MultiTypeCacheServiceImpl();
        cacheService.registerType(CacheableTypeConfig.builder(Goal.class, Goal.ITEM_TYPE, "goals").withIdExtractor(Goal::getItemId).build());

        patchService = new PatchServiceImpl();
        patchService.setPersistenceService(persistenceService);
        patchService.setCacheService(cacheService);
        patchService.setContextManager(executionContextManager);

        Goal goal = new Goal(new Metadata(null, GOAL_ID, "Old name", "Old description"));
        executionContextManager.executeAsTenant(TENANT, () -> {
            persistenceService.save(goal);
            cacheService.put(Goal.ITEM_TYPE, GOAL_ID, TENANT, goal);
        });
    }

    @Test
    void appliesJsonPatchOperations() {
        Patch patch = patch("patch-ok",
                op("test", "/metadata/name", "Old name"),
                op("replace", "/metadata/name", "New name"),
                op("remove", "/metadata/description", null));

        Item result = executionContextManager.executeAsTenant(TENANT, () -> patchService.patch(patch));

        assertEquals("New name", ((Goal) result).getMetadata().getName());
        Goal stored = load(GOAL_ID, Goal.class);
        assertEquals("New name", stored.getMetadata().getName());
        assertEquals(null, stored.getMetadata().getDescription());
    }

    @Test
    void failedPatchLeavesItemUnchanged() {
        Patch patch = patch("patch-failing-test",
                op("test", "/metadata/name", "Not the current name"),
                op("replace", "/metadata/name", "New name"));

        executionContextManager.executeAsTenant(TENANT, () -> patchService.patch(patch));

        assertEquals("Old name", load(GOAL_ID, Goal.class).getMetadata().getName());
        assertNotNull(load("patch-failing-test", Patch.class).getLastApplication());
    }

    private <T extends Item> T load(String id, Class<T> type) {
        return executionContextManager.executeAsTenant(TENANT, () -> persistenceService.load(id, type));
    }

    @SafeVarargs
    private static Patch patch(String id, Map<String, Object>... operations) {
        Patch patch = new Patch();
        patch.setItemId(id);
        patch.setPatchedItemId(GOAL_ID);
        patch.setPatchedItemType("goal");
        patch.setOperation("patch");
        patch.setData(Arrays.asList(operations));
        return patch;
    }

    private static Map<String, Object> op(String op, String path, Object value) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("op", op);
        operation.put("path", path);
        if (value != null) {
            operation.put("value", value);
        }
        return operation;
    }
}
