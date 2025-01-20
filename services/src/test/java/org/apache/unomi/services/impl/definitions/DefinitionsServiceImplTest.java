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
package org.apache.unomi.services.impl.definitions;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.PropertyMergeStrategyType;
import org.apache.unomi.api.ValueType;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl;
import org.apache.unomi.services.impl.TestConditionEvaluators;
import org.apache.unomi.services.impl.TestTenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.unomi.services.impl.TestTenantService.SYSTEM_TENANT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefinitionsServiceImplTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefinitionsServiceImplTest.class);

    @Mock
    private BundleContext bundleContext;
    @Mock
    private Bundle bundle;

    private TestTenantService tenantService;
    private TestDefinitionsServiceImpl definitionsService;
    private InMemoryPersistenceServiceImpl persistenceService;

    // Extend DefinitionsServiceImpl to make private methods visible for testing
    private static class TestDefinitionsServiceImpl extends DefinitionsServiceImpl {
        @Override
        public void processBundleStartup(BundleContext bundleContext) {
            super.processBundleStartup(bundleContext);
        }

        @Override
        public void processBundleStop(BundleContext bundleContext) {
            super.processBundleStop(bundleContext);
        }

        @Override
        public Map<Long, List<PluginType>> getTypesByPlugin() {
            return super.getTypesByPlugin();
        }

        @Override
        public void onTenantRemoved(String tenantId) {
            super.onTenantRemoved(tenantId);
        }
    }

    @BeforeEach
    void setUp() {
        tenantService = new TestTenantService();
        tenantService.setCurrentTenantId("test-tenant");
        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();
        persistenceService = new InMemoryPersistenceServiceImpl(tenantService, conditionEvaluatorDispatcher);
        definitionsService = new TestDefinitionsServiceImpl();
        definitionsService.setPersistenceService(persistenceService);
        definitionsService.setBundleContext(bundleContext);
        definitionsService.setTenantService(tenantService);
    }

    @Nested
    class BundleLifecycleTests {
        @Test
        void shouldHandleBundleStartup() {
            // given
            when(bundle.getBundleId()).thenReturn(1L);
            when(bundleContext.getBundle()).thenReturn(bundle);
            tenantService.setCurrentTenant(SYSTEM_TENANT);

            // Mock condition type JSON
            String conditionJson = "{\"metadata\":{\"id\":\"test-condition\",\"name\":\"Test Condition\",\"tags\":[\"tag1\"],\"systemTags\":[\"systemTag1\"]},\"parentCondition\":null}";
            InputStream conditionStream = null;
            InputStream actionStream = null;
            InputStream valueStream = null;

            try {
                conditionStream = new ByteArrayInputStream(conditionJson.getBytes());

                // Mock action type JSON
                String actionJson = "{\"metadata\":{\"id\":\"test-action\",\"name\":\"Test Action\",\"tags\":[\"tag1\"],\"systemTags\":[\"systemTag1\"]}}";
                actionStream = new ByteArrayInputStream(actionJson.getBytes());

                // Mock value type JSON
                String valueJson = "{\"id\":\"test-value\",\"tags\":[\"tag1\"]}";
                valueStream = new ByteArrayInputStream(valueJson.getBytes());

                when(bundle.findEntries(eq("META-INF/cxs/conditions"), eq("*.json"), eq(true)))
                    .thenReturn(Collections.enumeration(Collections.singletonList(createTestURL(conditionStream))));
                when(bundle.findEntries(eq("META-INF/cxs/actions"), eq("*.json"), eq(true)))
                    .thenReturn(Collections.enumeration(Collections.singletonList(createTestURL(actionStream))));
                when(bundle.findEntries(eq("META-INF/cxs/values"), eq("*.json"), eq(true)))
                    .thenReturn(Collections.enumeration(Collections.singletonList(createTestURL(valueStream))));

                // when
                definitionsService.processBundleStartup(bundleContext);

                // then
                // Verify condition type was saved
                ConditionType savedConditionType = persistenceService.load("test-condition", ConditionType.class);
                assertNotNull(savedConditionType, "Condition type should be saved");
                assertEquals(SYSTEM_TENANT, savedConditionType.getTenantId());
                assertTrue(savedConditionType.getMetadata().getTags().contains("tag1"));
                assertTrue(savedConditionType.getMetadata().getSystemTags().contains("systemTag1"));

                // Verify action type was saved
                ActionType savedActionType = persistenceService.load("test-action", ActionType.class);
                assertNotNull(savedActionType, "Action type should be saved");
                assertEquals(SYSTEM_TENANT, savedActionType.getTenantId());
                assertTrue(savedActionType.getMetadata().getTags().contains("tag1"));
                assertTrue(savedActionType.getMetadata().getSystemTags().contains("systemTag1"));

                // Verify value type was loaded (not persisted)
                ValueType valueType = definitionsService.getValueType("test-value");
                assertNotNull(valueType);
                assertTrue(valueType.getTags().contains("tag1"));

                // Verify plugin types list
                List<PluginType> pluginTypes = definitionsService.getTypesByPlugin().get(1L);
                assertNotNull(pluginTypes);
                assertEquals(3, pluginTypes.size());
                assertTrue(pluginTypes.stream().anyMatch(t -> t instanceof ConditionType));
                assertTrue(pluginTypes.stream().anyMatch(t -> t instanceof ActionType));
                assertTrue(pluginTypes.stream().anyMatch(t -> t instanceof ValueType));
            } finally {
                // Clean up resources
                closeQuietly(conditionStream);
                closeQuietly(actionStream);
                closeQuietly(valueStream);
            }
        }

        @Test
        void shouldHandleMalformedJson() {
            // given
            when(bundle.getBundleId()).thenReturn(1L);
            String malformedJson = "{malformed:json}";
            InputStream jsonStream = new ByteArrayInputStream(malformedJson.getBytes());
            when(bundle.findEntries(eq("META-INF/cxs/conditions"), eq("*.json"), eq(true)))
                .thenReturn(Collections.enumeration(Collections.singletonList(createTestURL(jsonStream))));

            // when
            definitionsService.processBundleStartup(bundleContext);

            // then
            // Should not throw exception, but log error and continue
            List<ConditionType> allConditions = persistenceService.getAllItems(ConditionType.class);
            assertTrue(allConditions.isEmpty(), "No condition types should be saved");
            assertTrue(definitionsService.getTypesByPlugin().isEmpty());
        }

        @Test
        void shouldHandleConcurrentBundleOperations() throws InterruptedException {
            int threadCount = 5;
            CountDownLatch startCompletionLatch = new CountDownLatch(threadCount);
            ConcurrentTestHelper startHelper = new ConcurrentTestHelper(threadCount);
            ConcurrentTestHelper stopHelper = new ConcurrentTestHelper(threadCount);

            // First start all bundles
            for (int i = 0; i < threadCount; i++) {
                final long bundleId = i;

                startHelper.startThread("BundleStart-" + i, () -> {
                    Bundle bundle = mock(Bundle.class);
                    BundleContext context = mock(BundleContext.class);
                    when(bundle.getBundleId()).thenReturn(bundleId);
                    when(bundle.getBundleContext()).thenReturn(context);
                    when(context.getBundle()).thenReturn(bundle);

                    String valueJson = "{\"id\":\"test-value-" + bundleId + "\",\"tags\":[\"tag1\"]}";
                    try (TestResource resource = new TestResource(valueJson)) {
                        when(bundle.findEntries(eq("META-INF/cxs/values"), eq("*.json"), eq(true)))
                            .thenReturn(Collections.enumeration(Collections.singletonList(
                                createTestURL(resource.getStream()))));

                        definitionsService.processBundleStartup(context);
                        startCompletionLatch.countDown();
                    }
                });
            }

            // Wait for all starts to complete
            assertTrue(startCompletionLatch.await(5, TimeUnit.SECONDS), "Timed out waiting for bundle starts");
            startHelper.executeAndVerify("Bundle start operations", 5);

            // Then stop all bundles
            for (int i = 0; i < threadCount; i++) {
                final long bundleId = i;
                stopHelper.startThread("BundleStop-" + i, () -> {
                    Bundle bundle = mock(Bundle.class);
                    BundleContext context = mock(BundleContext.class);
                    when(bundle.getBundleId()).thenReturn(bundleId);
                    when(bundle.getBundleContext()).thenReturn(context);
                    when(context.getBundle()).thenReturn(bundle);

                    definitionsService.processBundleStop(context);
                });
            }

            stopHelper.executeAndVerify("Bundle stop operations", 5);

            // Verify final state
            assertEquals(0, definitionsService.getAllValueTypes().size());
            assertTrue(definitionsService.getTypesByPlugin().isEmpty());
        }

        @Test
        void shouldHandleCrosstenantConcurrentAccess() throws InterruptedException {
            int threadCount = 5;
            CyclicBarrier barrier = new CyclicBarrier(threadCount * 2); // Two tenants
            CountDownLatch endLatch = new CountDownLatch(threadCount * 2);
            AtomicBoolean failed = new AtomicBoolean(false);

            // Use synchronized mock to avoid race conditions
            synchronized(tenantService) {
                for (int i = 0; i < threadCount; i++) {
                    final int index = i;

                    // System tenant thread
                    new Thread(() -> {
                        try {
                            barrier.await();
                            synchronized(tenantService) {
                                tenantService.setCurrentTenant(SYSTEM_TENANT);
                            }
                            ConditionType conditionType = createTestConditionType("test" + index, new HashSet<>(Arrays.asList("tag1")), null);
                            definitionsService.setConditionType(conditionType);
                        } catch (Exception e) {
                            failed.set(true);
                            LOGGER.error("Thread execution failed", e);
                        } finally {
                            endLatch.countDown();
                        }
                    }).start();

                    // Custom tenant thread
                    new Thread(() -> {
                        try {
                            barrier.await();
                            synchronized(tenantService) {
                                tenantService.setCurrentTenant("tenant1");
                            }
                            ConditionType conditionType = createTestConditionType("test" + index + "-tenant", new HashSet<>(Arrays.asList("tag1")), null);
                            definitionsService.setConditionType(conditionType);
                        } catch (Exception e) {
                            failed.set(true);
                            LOGGER.error("Thread execution failed", e);
                        } finally {
                            endLatch.countDown();
                        }
                    }).start();
                }
            }

            assertTrue(endLatch.await(5, TimeUnit.SECONDS), "Test timed out");
            assertFalse(failed.get(), "One or more threads failed execution");

            // Verify tenant isolation
            synchronized(tenantService) {
                tenantService.setCurrentTenant(SYSTEM_TENANT);
                assertEquals(threadCount, definitionsService.getConditionTypesByTag("tag1").size());
                tenantService.setCurrentTenant("tenant1");
                assertEquals(threadCount*2, definitionsService.getConditionTypesByTag("tag1").size());
            }
        }

        @Test
        void shouldHandleTenantRemoval() {
            // Add types for multiple tenants
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            ConditionType systemType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            definitionsService.setConditionType(systemType);

            tenantService.setCurrentTenant("tenant1");
            ConditionType tenantType = createTestConditionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
            definitionsService.setConditionType(tenantType);

            // Simulate tenant removal
            definitionsService.onTenantRemoved("tenant1");

            // Verify tenant caches are cleared
            tenantService.setCurrentTenant("tenant1");
            assertEquals(1, definitionsService.getConditionTypesByTag("tag1").size());
            assertNull(definitionsService.getConditionType("test2"));

            // Verify system tenant is unaffected
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            assertFalse(definitionsService.getConditionTypesByTag("tag1").isEmpty());
            assertNotNull(definitionsService.getConditionType("test1"));
        }

        @Test
        void shouldHandleConcurrentAccessDuringTenantRemoval() throws InterruptedException {
            int numThreads = 10;
            ConcurrentTestHelper testHelper = new ConcurrentTestHelper(numThreads);

            // Setup initial data
            setupTenantContext("tenant1");
            ConditionType tenantType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            definitionsService.setConditionType(tenantType);

            // Create threads that will access the service while tenant is being removed
            for (int i = 0; i < numThreads; i++) {
                testHelper.startThread("TenantAccess-" + i, () -> {
                    setupTenantContext("tenant1");
                    definitionsService.getConditionType("test1");
                    definitionsService.getConditionTypesByTag("tag1");
                    Thread.sleep(10); // Simulate some work
                });
            }

            definitionsService.onTenantRemoved("tenant1");
            testHelper.executeAndVerify("Tenant removal test", 5);

            // Verify tenant was properly removed
            setupTenantContext("tenant1");
            assertTrue(definitionsService.getConditionTypesByTag("tag1").isEmpty());
            assertNull(definitionsService.getConditionType("test1"));
        }

        @Test
        void shouldPreventSystemTenantRemoval() {
            // Try to remove system tenant
            definitionsService.onTenantRemoved(SYSTEM_TENANT);

            // Verify system tenant data is still accessible
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            ConditionType systemType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            definitionsService.setConditionType(systemType);

            assertNotNull(definitionsService.getConditionType("test1"));
            assertFalse(definitionsService.getConditionTypesByTag("tag1").isEmpty());
        }

        @Test
        void shouldHandleNullTagAccess() {
            // Test with null tag
            assertTrue(definitionsService.getValueTypeByTag(null).isEmpty());
            assertTrue(definitionsService.getConditionTypesByTag(null).isEmpty());
            assertTrue(definitionsService.getActionTypeByTag(null).isEmpty());

            // Test with null tags collection
            ValueType valueType = createTestValueType("test1", null, null);
            definitionsService.setValueType(valueType);
            assertNotNull(definitionsService.getValueType("test1"));
        }

        @Test
        void shouldHandleBundleStop() {
            // given
            Long bundleId = 1L;
            setupMockBundle(bundleId);
            tenantService.setCurrentTenant(SYSTEM_TENANT);

            // Add all types of definitions
            ValueType valueType = createTestValueType("test1", new HashSet<>(Arrays.asList("tag1")), bundleId);
            ConditionType conditionType = createTestConditionType("test2", new HashSet<>(Arrays.asList("tag1")), bundleId);
            ActionType actionType = createTestActionType("test3", new HashSet<>(Arrays.asList("tag1")), bundleId);

            registerPluginType(valueType, bundleId);
            registerPluginType(conditionType, bundleId);
            registerPluginType(actionType, bundleId);

            // when
            definitionsService.processBundleStop(bundleContext);

            // then
            assertNull(definitionsService.getValueType("test1"));
            assertNull(definitionsService.getConditionType("test2"));
            assertNull(definitionsService.getActionType("test3"));
            assertTrue(definitionsService.getValueTypeByTag("tag1").isEmpty());
            assertTrue(definitionsService.getConditionTypesByTag("tag1").isEmpty());
            assertTrue(definitionsService.getActionTypeByTag("tag1").isEmpty());

            // Verify plugin types are removed
            assertNull(definitionsService.getTypesByPlugin().get(bundleId));
        }

        @Test
        void shouldInvalidateCachesOnBundleStop() {
            // Add types from multiple bundles
            Long bundleId1 = 1L;
            Long bundleId2 = 2L;
            tenantService.setCurrentTenant(SYSTEM_TENANT);

            ValueType valueType1 = createTestValueType("test1", new HashSet<>(Arrays.asList("tag1")), bundleId1);
            registerPluginType(valueType1, bundleId1);

            ValueType valueType2 = createTestValueType("test2", new HashSet<>(Arrays.asList("tag1")), bundleId2);
            registerPluginType(valueType2, bundleId2);

            // Stop one bundle
            setupMockBundle(bundleId1);
            definitionsService.processBundleStop(bundleContext);

            // Verify only its types are removed
            assertNull(definitionsService.getValueType("test1"));
            assertNotNull(definitionsService.getValueType("test2"));

            // Verify tag cache consistency
            Set<ValueType> taggedTypes = definitionsService.getValueTypeByTag("tag1");
            assertEquals(1, taggedTypes.size());
            assertTrue(taggedTypes.contains(valueType2));
        }

        @Test
        void shouldHandleValueTypeInheritance() {
            // Create system tenant value type
            ValueType valueType = new ValueType();
            valueType.setId("test");
            valueType.setTags(Collections.singleton("testTag"));

            // Add to system tenant cache
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            definitionsService.setValueType(valueType);

            // Switch to different tenant and verify inheritance
            tenantService.setCurrentTenant("tenant1");
            ValueType result = definitionsService.getValueType("test");
            assertNotNull(result);
            assertEquals("test", result.getId());

            // Second lookup should use cache
            result = definitionsService.getValueType("test");
            assertNotNull(result);
            assertEquals("test", result.getId());
        }

        @Test
        void shouldHandleConditionTypeMetadata() {
            // Create condition type with metadata
            ConditionType conditionType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            Metadata metadata = new Metadata();
            metadata.setId("test1");
            metadata.setName("Test Condition");
            metadata.setDescription("Test Description");
            metadata.setSystemTags(new HashSet<>(Arrays.asList("systemTag1")));
            conditionType.setMetadata(metadata);

            // Save and retrieve
            definitionsService.setConditionType(conditionType);
            ConditionType retrieved = definitionsService.getConditionType("test1");

            // Verify metadata
            assertNotNull(retrieved);
            assertEquals("Test Condition", retrieved.getMetadata().getName());
            assertEquals("Test Description", retrieved.getMetadata().getDescription());
            assertTrue(retrieved.getMetadata().getSystemTags().contains("systemTag1"));

            // Verify system tag lookup
            assertTrue(definitionsService.getConditionTypesBySystemTag("systemTag1").contains(retrieved));
        }

        @Test
        void shouldHandleActionTypeConcurrentAccess() throws InterruptedException {
            int threadCount = 10;
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            Set<String> expectedTags = new HashSet<>();
            Set<String> expectedIds = new HashSet<>();

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                String tag = "tag" + index;
                String id = "test" + index;
                expectedTags.add(tag);
                expectedIds.add(id);

                new Thread(() -> {
                    try {
                        barrier.await();
                        tenantService.setCurrentTenant("test-tenant");
                        ActionType actionType = createTestActionType(id, new HashSet<>(Arrays.asList(tag)), null);
                        definitionsService.setActionType(actionType);
                    } catch (Exception e) {
                        fail("Thread execution failed: " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                }).start();
            }

            assertTrue(endLatch.await(5, TimeUnit.SECONDS));

            // Verify all types were saved
            Collection<ActionType> allTypes = definitionsService.getAllActionTypes();
            assertEquals(threadCount, allTypes.size());

            // Verify all tags are present
            for (String tag : expectedTags) {
                Set<ActionType> taggedTypes = definitionsService.getActionTypeByTag(tag);
                assertEquals(1, taggedTypes.size());
                ActionType taggedType = taggedTypes.iterator().next();
                assertTrue(taggedType.getMetadata().getTags().contains(tag));
            }
        }

        @Test
        void shouldMaintainTagCacheConsistency() {
            // given
            tenantService.setCurrentTenant("tenant1");

            // Add type with tags
            ConditionType type1 = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1", "tag2")), null);
            definitionsService.setConditionType(type1);

            // Verify initial state
            assertEquals(1, definitionsService.getConditionTypesByTag("tag1").size());
            assertEquals(1, definitionsService.getConditionTypesByTag("tag2").size());

            // Update tags
            type1.getMetadata().setTags(new HashSet<>(Arrays.asList("tag2", "tag3")));
            definitionsService.setConditionType(type1);

            // Verify tag cache updates
            assertTrue(definitionsService.getConditionTypesByTag("tag1").isEmpty());
            assertEquals(1, definitionsService.getConditionTypesByTag("tag2").size());
            assertEquals(1, definitionsService.getConditionTypesByTag("tag3").size());

            // Remove type
            definitionsService.removeConditionType("test1");

            // Verify all tag caches are clean
            assertTrue(definitionsService.getConditionTypesByTag("tag2").isEmpty());
            assertTrue(definitionsService.getConditionTypesByTag("tag3").isEmpty());
        }

        @Test
        void shouldHandleErrorsInJsonLoading() {
            // given
            when(bundle.getBundleId()).thenReturn(1L);
            when(bundleContext.getBundle()).thenReturn(bundle);

            // Malformed condition JSON
            String malformedConditionJson = "{bad json}";
            InputStream conditionStream = new ByteArrayInputStream(malformedConditionJson.getBytes());
            when(bundle.findEntries(eq("META-INF/cxs/conditions"), eq("*.json"), eq(true)))
                .thenReturn(Collections.enumeration(Collections.singletonList(createTestURL(conditionStream))));

            // Malformed action JSON
            String malformedActionJson = "{also bad}";
            InputStream actionStream = new ByteArrayInputStream(malformedActionJson.getBytes());
            when(bundle.findEntries(eq("META-INF/cxs/actions"), eq("*.json"), eq(true)))
                .thenReturn(Collections.enumeration(Collections.singletonList(createTestURL(actionStream))));

            // Valid value type JSON
            String valueJson = "{\"id\":\"test-value\",\"tags\":[\"tag1\"]}";
            InputStream valueStream = new ByteArrayInputStream(valueJson.getBytes());
            when(bundle.findEntries(eq("META-INF/cxs/values"), eq("*.json"), eq(true)))
                .thenReturn(Collections.enumeration(Collections.singletonList(createTestURL(valueStream))));

            // when
            definitionsService.processBundleStartup(bundleContext);

            // then

            // Should still load valid value type
            ValueType valueType = definitionsService.getValueType("test-value");
            assertNotNull(valueType);
            assertTrue(valueType.getTags().contains("tag1"));
        }

        @Test
        void shouldPreserveSystemTenantOnTenantRemoval() {
            // Add system tenant types
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            ConditionType systemType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            definitionsService.setConditionType(systemType);

            // Add custom tenant types
            tenantService.setCurrentTenant("tenant1");
            ConditionType tenantType = createTestConditionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
            definitionsService.setConditionType(tenantType);

            // Remove tenant
            definitionsService.onTenantRemoved("tenant1");

            // Verify tenant caches are cleared
            tenantService.setCurrentTenant("tenant1");
            assertEquals(1, definitionsService.getConditionTypesByTag("tag1").size());
            assertNull(definitionsService.getConditionType("test2"));

            // Verify system tenant is preserved
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            Set<ConditionType> systemTypes = definitionsService.getConditionTypesByTag("tag1");
            assertEquals(1, systemTypes.size());
            assertTrue(systemTypes.contains(systemType));
            assertEquals(systemType, definitionsService.getConditionType("test1"));
        }

        @Test
        void shouldHandleResourceCleanup() {
            // given
            when(bundle.getBundleId()).thenReturn(1L);
            when(bundleContext.getBundle()).thenReturn(bundle);

            // Create a stream that tracks if it was closed
            final AtomicBoolean streamClosed = new AtomicBoolean(false);
            InputStream jsonStream = new ByteArrayInputStream("{\"id\":\"test\"}".getBytes()) {
                @Override
                public void close() throws IOException {
                    super.close();
                    streamClosed.set(true);
                }
            };

            try {
                when(bundle.findEntries(eq("META-INF/cxs/values"), eq("*.json"), eq(true)))
                    .thenReturn(Collections.enumeration(Collections.singletonList(createTestURL(jsonStream))));

                // when
                definitionsService.processBundleStartup(bundleContext);

                // then
                assertTrue(streamClosed.get(), "Stream should be closed after reading");
            } finally {
                // Ensure stream is closed even if test fails
                try {
                    jsonStream.close();
                } catch (IOException e) {
                    LOGGER.error("Error closing test stream", e);
                }
            }
        }

        @Test
        void shouldHandleInheritanceForAllTypes() {
            // Create system tenant types
            tenantService.setCurrentTenant(SYSTEM_TENANT);

            ConditionType conditionType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            definitionsService.setConditionType(conditionType);

            ActionType actionType = createTestActionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
            definitionsService.setActionType(actionType);

            ValueType valueType = createTestValueType("test3", new HashSet<>(Arrays.asList("tag1")), null);
            definitionsService.setValueType(valueType);

            // Switch to custom tenant
            tenantService.setCurrentTenant("tenant1");

            // Verify inheritance for condition types
            assertEquals(conditionType, definitionsService.getConditionType("test1"));
            assertTrue(definitionsService.getConditionTypesByTag("tag1").contains(conditionType));

            // Verify inheritance for action types
            assertEquals(actionType, definitionsService.getActionType("test2"));
            assertTrue(definitionsService.getActionTypeByTag("tag1").contains(actionType));

            // Verify inheritance for value types
            assertEquals(valueType, definitionsService.getValueType("test3"));
            assertTrue(definitionsService.getValueTypeByTag("tag1").contains(valueType));

            // Verify items were saved in persistence service
            ConditionType savedCondition = persistenceService.load("test1", ConditionType.class);
            ActionType savedAction = persistenceService.load("test2", ActionType.class);
            assertNotNull(savedCondition, "Condition type should be saved");
            assertNotNull(savedAction, "Action type should be saved");
            assertEquals(SYSTEM_TENANT, savedCondition.getTenantId());
            assertEquals(SYSTEM_TENANT, savedAction.getTenantId());
        }

        @Test
        void shouldHandleConcurrentTenantAccess() throws InterruptedException {
            int threadCount = 5;
            CyclicBarrier barrier = new CyclicBarrier(threadCount * 2); // Two tenants
            CountDownLatch endLatch = new CountDownLatch(threadCount * 2);
            AtomicBoolean failed = new AtomicBoolean(false);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;

                // System tenant thread
                new Thread(() -> {
                    try {
                        barrier.await();
                        tenantService.setCurrentTenantId(SYSTEM_TENANT);
                        ConditionType conditionType = createTestConditionType("test" + index, new HashSet<>(Arrays.asList("tag1")), null);
                        definitionsService.setConditionType(conditionType);
                    } catch (Exception e) {
                        failed.set(true);
                        LOGGER.error("Thread execution failed", e);
                    } finally {
                        endLatch.countDown();
                    }
                }).start();

                // Custom tenant thread
                new Thread(() -> {
                    try {
                        barrier.await();
                        tenantService.setCurrentTenantId("tenant1");
                        ConditionType conditionType = createTestConditionType("test" + index + "-tenant", new HashSet<>(Arrays.asList("tag1")), null);
                        definitionsService.setConditionType(conditionType);
                    } catch (Exception e) {
                        failed.set(true);
                        LOGGER.error("Thread execution failed", e);
                    } finally {
                        endLatch.countDown();
                    }
                }).start();
            }

            assertTrue(endLatch.await(5, TimeUnit.SECONDS), "Test timed out");
            assertFalse(failed.get(), "One or more threads failed execution");

            // Verify tenant isolation
            tenantService.setCurrentTenantId(SYSTEM_TENANT);
            assertEquals(threadCount, definitionsService.getConditionTypesByTag("tag1").size());
            tenantService.setCurrentTenantId("tenant1");
            assertEquals(threadCount*2, definitionsService.getConditionTypesByTag("tag1").size());
        }

        @Test
        void shouldHandleTenantLifecycle() {
            // Setup system tenant data
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            ConditionType systemConditionType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            systemConditionType.setTenantId(SYSTEM_TENANT);
            definitionsService.setConditionType(systemConditionType);

            ActionType systemActionType = createTestActionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
            systemActionType.setTenantId(SYSTEM_TENANT);
            definitionsService.setActionType(systemActionType);

            // Setup tenant data
            tenantService.setCurrentTenant("tenant1");
            ConditionType tenantConditionType = createTestConditionType("test4", new HashSet<>(Arrays.asList("tag2")), null);
            tenantConditionType.setTenantId("tenant1");
            definitionsService.setConditionType(tenantConditionType);

            ActionType tenantActionType = createTestActionType("test5", new HashSet<>(Arrays.asList("tag2")), null);
            tenantActionType.setTenantId("tenant1");
            definitionsService.setActionType(tenantActionType);

            // Verify tenant data
            assertEquals(tenantConditionType, definitionsService.getConditionType("test4"));
            assertEquals(tenantActionType, definitionsService.getActionType("test5"));
            assertTrue(definitionsService.getConditionTypesByTag("tag2").contains(tenantConditionType));
            assertTrue(definitionsService.getActionTypeByTag("tag2").contains(tenantActionType));

            // Verify system tenant data is accessible
            assertNotNull(definitionsService.getConditionType("test1"));
            assertNotNull(definitionsService.getActionType("test2"));

            // Test tenant removal
            definitionsService.onTenantRemoved("tenant1");

            // Verify tenant data is removed
            assertNull(definitionsService.getConditionType("test4"));
            assertNull(definitionsService.getActionType("test5"));
            assertTrue(definitionsService.getConditionTypesByTag("tag2").isEmpty());
            assertTrue(definitionsService.getActionTypeByTag("tag2").isEmpty());

            // Verify system tenant data is preserved
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            assertEquals(systemConditionType, definitionsService.getConditionType("test1"));
            assertEquals(systemActionType, definitionsService.getActionType("test2"));
        }

        @Test
        void shouldHandleConcurrentAccess() throws InterruptedException {
            int numThreads = 10;
            ConcurrentTestHelper testHelper = new ConcurrentTestHelper(numThreads);

            // Setup initial data
            setupTenantContext("tenant1");
            ConditionType tenantType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            definitionsService.setConditionType(tenantType);

            // Create threads that will access the service while tenant is being removed
            for (int i = 0; i < numThreads; i++) {
                testHelper.startThread("TenantAccess-" + i, () -> {
                    setupTenantContext("tenant1");
                    definitionsService.getConditionType("test1");
                    definitionsService.getConditionTypesByTag("tag1");
                    Thread.sleep(10); // Simulate some work
                });
            }

            definitionsService.onTenantRemoved("tenant1");
            testHelper.executeAndVerify("Tenant removal test", 5);

            // Verify tenant was properly removed
            setupTenantContext("tenant1");
            assertTrue(definitionsService.getConditionTypesByTag("tag1").isEmpty());
            assertNull(definitionsService.getConditionType("test1"));
        }

        @Test
        void shouldPreferTenantSpecificTypes() {
            // Create system tenant types
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            ConditionType systemConditionType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            systemConditionType.setTenantId(SYSTEM_TENANT);
            definitionsService.setConditionType(systemConditionType);

            ActionType systemActionType = createTestActionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
            systemActionType.setTenantId(SYSTEM_TENANT);
            definitionsService.setActionType(systemActionType);

            ValueType systemValueType = createTestValueType("test3", new HashSet<>(Arrays.asList("tag1")), null);
            definitionsService.setValueType(systemValueType);

            // Create tenant-specific types with same IDs but different tags
            tenantService.setCurrentTenant("tenant1");
            ConditionType tenantConditionType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag2")), null);
            tenantConditionType.setTenantId("tenant1");
            definitionsService.setConditionType(tenantConditionType);

            ActionType tenantActionType = createTestActionType("test2", new HashSet<>(Arrays.asList("tag2")), null);
            tenantActionType.setTenantId("tenant1");
            definitionsService.setActionType(tenantActionType);

            ValueType tenantValueType = createTestValueType("test3", new HashSet<>(Arrays.asList("tag2")), null);
            definitionsService.setValueType(tenantValueType);

            // Verify that tenant-specific types are returned
            assertEquals(tenantConditionType, definitionsService.getConditionType("test1"));
            assertEquals(tenantActionType, definitionsService.getActionType("test2"));
            assertEquals(tenantValueType, definitionsService.getValueType("test3"));

            // Verify that only tenant-specific tags are returned
            assertTrue(definitionsService.getConditionTypesByTag("tag2").contains(tenantConditionType));
            assertTrue(definitionsService.getConditionTypesByTag("tag1").contains(systemConditionType));

            assertTrue(definitionsService.getActionTypeByTag("tag2").contains(tenantActionType));
            assertTrue(definitionsService.getActionTypeByTag("tag1").contains(systemActionType));

            assertTrue(definitionsService.getValueTypeByTag("tag2").contains(tenantValueType));
            assertTrue(definitionsService.getValueTypeByTag("tag1").contains(systemValueType));
        }

        @Test
        void shouldHandleTypeRemovalWithInheritance() {
            // Create system tenant types
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            ConditionType systemConditionType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            systemConditionType.setTenantId(SYSTEM_TENANT);
            definitionsService.setConditionType(systemConditionType);
            // Verify system tenant type was saved
            assertEquals(systemConditionType, definitionsService.getConditionType("test1"));

            ActionType systemActionType = createTestActionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
            systemActionType.setTenantId(SYSTEM_TENANT);
            definitionsService.setActionType(systemActionType);
            // Verify system tenant type was saved
            assertEquals(systemActionType, definitionsService.getActionType("test2"));

            ValueType systemValueType = createTestValueType("test3", new HashSet<>(Arrays.asList("tag1")), null);
            definitionsService.setValueType(systemValueType);
            // Verify system tenant type was saved
            assertEquals(systemValueType, definitionsService.getValueType("test3"));

            // Create tenant-specific types with same IDs
            tenantService.setCurrentTenant("tenant1");
            ConditionType tenantConditionType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag2")), null);
            tenantConditionType.setTenantId("tenant1");
            definitionsService.setConditionType(tenantConditionType);

            ActionType tenantActionType = createTestActionType("test2", new HashSet<>(Arrays.asList("tag2")), null);
            tenantActionType.setTenantId("tenant1");
            definitionsService.setActionType(tenantActionType);

            ValueType tenantValueType = createTestValueType("test3", new HashSet<>(Arrays.asList("tag2")), null);
            definitionsService.setValueType(tenantValueType);

            // Verify initial state
            assertEquals(tenantConditionType, definitionsService.getConditionType("test1"));
            assertEquals(tenantActionType, definitionsService.getActionType("test2"));
            assertEquals(tenantValueType, definitionsService.getValueType("test3"));

            // Remove tenant-specific types
            definitionsService.removeConditionType("test1");
            definitionsService.removeActionType("test2");
            definitionsService.removeValueType("test3");

            // Verify tenant-specific types are removed but system types are still accessible
            assertEquals(systemConditionType, definitionsService.getConditionType("test1"));
            assertEquals(systemActionType, definitionsService.getActionType("test2"));
            assertEquals(systemValueType, definitionsService.getValueType("test3"));

            // Verify tag-based queries
            assertFalse(definitionsService.getConditionTypesByTag("tag2").contains(tenantConditionType));
            assertFalse(definitionsService.getActionTypeByTag("tag2").contains(tenantActionType));
            assertFalse(definitionsService.getValueTypeByTag("tag2").contains(tenantValueType));

            assertTrue(definitionsService.getConditionTypesByTag("tag1").contains(systemConditionType));
            assertTrue(definitionsService.getActionTypeByTag("tag1").contains(systemActionType));
            assertTrue(definitionsService.getValueTypeByTag("tag1").contains(systemValueType));

            // Switch to system tenant and verify types still exist
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            assertEquals(systemConditionType, definitionsService.getConditionType("test1"));
            assertEquals(systemActionType, definitionsService.getActionType("test2"));
            assertEquals(systemValueType, definitionsService.getValueType("test3"));
        }

        @Test
        void shouldHandleBundleTypeRemovalWithInheritance() {
            // Create system tenant types from a bundle
            Long bundleId = 1L;
            setupMockBundle(bundleId);

            tenantService.setCurrentTenant(SYSTEM_TENANT);
            ConditionType systemConditionType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), bundleId);
            systemConditionType.setTenantId(SYSTEM_TENANT);
            registerPluginType(systemConditionType, bundleId);

            ActionType systemActionType = createTestActionType("test2", new HashSet<>(Arrays.asList("tag1")), bundleId);
            systemActionType.setTenantId(SYSTEM_TENANT);
            registerPluginType(systemActionType, bundleId);

            ValueType systemValueType = createTestValueType("test3", new HashSet<>(Arrays.asList("tag1")), bundleId);
            registerPluginType(systemValueType, bundleId);

            // Create tenant-specific overrides
            tenantService.setCurrentTenant("tenant1");
            ConditionType tenantConditionType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag2")), null);
            tenantConditionType.setTenantId("tenant1");
            definitionsService.setConditionType(tenantConditionType);

            ActionType tenantActionType = createTestActionType("test2", new HashSet<>(Arrays.asList("tag2")), null);
            tenantActionType.setTenantId("tenant1");
            definitionsService.setActionType(tenantActionType);

            ValueType tenantValueType = createTestValueType("test3", new HashSet<>(Arrays.asList("tag2")), null);
            definitionsService.setValueType(tenantValueType);

            // Stop the bundle, this should remove the system types
            definitionsService.processBundleStop(bundleContext);

            // Verify tenant-specific types are still available
            assertEquals(tenantConditionType, definitionsService.getConditionType("test1"));
            assertEquals(tenantActionType, definitionsService.getActionType("test2"));
            assertEquals(tenantValueType, definitionsService.getValueType("test3"));

            // Remove tenant-specific types
            definitionsService.removeConditionType("test1");
            definitionsService.removeActionType("test2");
            definitionsService.removeValueType("test3");

            // Verify all types are now gone (system types were removed by bundle stop)
            assertNull(definitionsService.getConditionType("test1"));
            assertNull(definitionsService.getActionType("test2"));
            assertNull(definitionsService.getValueType("test3"));

            assertTrue(definitionsService.getConditionTypesByTag("tag1").isEmpty());
            assertTrue(definitionsService.getActionTypeByTag("tag1").isEmpty());
            assertTrue(definitionsService.getValueTypeByTag("tag1").isEmpty());
            assertTrue(definitionsService.getConditionTypesByTag("tag2").isEmpty());
            assertTrue(definitionsService.getActionTypeByTag("tag2").isEmpty());
            assertTrue(definitionsService.getValueTypeByTag("tag2").isEmpty());
        }
    }

    @Nested
    class ValueTypeTests {
        @Test
        void shouldManageValueTypes() {
            // given
            ValueType valueType = createTestValueType("test1", new HashSet<>(Arrays.asList("tag1", "tag2")), null);

            // when
            definitionsService.setValueType(valueType);

            // then
            assertEquals(valueType, definitionsService.getValueType("test1"));
            assertTrue(definitionsService.getValueTypeByTag("tag1").contains(valueType));
            assertTrue(definitionsService.getValueTypeByTag("tag2").contains(valueType));

            // when removing
            definitionsService.removeValueType("test1");

            // then
            assertNull(definitionsService.getValueType("test1"));
            assertTrue(definitionsService.getValueTypeByTag("tag1").isEmpty());
            assertTrue(definitionsService.getValueTypeByTag("tag2").isEmpty());

        }

        @Test
        void shouldGetAllValueTypes() {
            // given
            ValueType valueType1 = createTestValueType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            ValueType valueType2 = createTestValueType("test2", new HashSet<>(Arrays.asList("tag2")), null);
            definitionsService.setValueType(valueType1);
            definitionsService.setValueType(valueType2);

            // when
            Collection<ValueType> allTypes = definitionsService.getAllValueTypes();

            // then
            assertEquals(2, allTypes.size());
            assertTrue(allTypes.contains(valueType1));
            assertTrue(allTypes.contains(valueType2));

        }

        @Test
        void shouldHandleNullTags() {
            ValueType valueType = createTestValueType("test1", null, null);
            definitionsService.setValueType(valueType);
            assertNotNull(definitionsService.getValueType("test1"));
        }

        @Test
        void shouldHandleEmptyTags() {
            ValueType valueType = createTestValueType("test1", Collections.emptySet(), null);
            definitionsService.setValueType(valueType);
            assertNotNull(definitionsService.getValueType("test1"));
        }

        @Test
        void shouldHandleConcurrentAccess() throws InterruptedException {
            int threadCount = 10;
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            Set<String> expectedTags = new HashSet<>();
            Set<String> expectedIds = new HashSet<>();

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                String tag = "tag" + index;
                String id = "test" + index;
                expectedTags.add(tag);
                expectedIds.add(id);

                new Thread(() -> {
                    try {
                        barrier.await();
                        tenantService.setCurrentTenant("test-tenant");
                        ValueType valueType = createTestValueType(id, new HashSet<>(Arrays.asList(tag)), null);
                        definitionsService.setValueType(valueType);
                    } catch (Exception e) {
                        fail("Thread execution failed: " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                }).start();
            }

            assertTrue(endLatch.await(5, TimeUnit.SECONDS));

            // Verify all types were saved in memory
            Collection<ValueType> allTypes = definitionsService.getAllValueTypes();
            assertEquals(threadCount, allTypes.size());

            // Verify all tags are present
            Set<String> actualTags = new HashSet<>();
            Set<String> actualIds = new HashSet<>();
            for (ValueType type : allTypes) {
                actualIds.add(type.getId());
                actualTags.addAll(type.getTags());
            }
            assertEquals(expectedTags, actualTags);
            assertEquals(expectedIds, actualIds);

            // Verify tag cache consistency
            for (String tag : expectedTags) {
                Set<ValueType> taggedTypes = definitionsService.getValueTypeByTag(tag);
                assertEquals(1, taggedTypes.size());
                ValueType taggedType = taggedTypes.iterator().next();
                assertTrue(taggedType.getTags().contains(tag));
            }
        }
    }

    @Nested
    class ConditionTypeTests {
        @Test
        void shouldManageConditionTypes() {
            // given
            ConditionType conditionType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1", "tag2")), null);

            // when
            definitionsService.setConditionType(conditionType);

            // then
            assertEquals(conditionType, definitionsService.getConditionType("test1"));
            assertTrue(definitionsService.getConditionTypesByTag("tag1").contains(conditionType));
            assertTrue(definitionsService.getConditionTypesByTag("tag2").contains(conditionType));

            // when removing
            definitionsService.removeConditionType("test1");

            // then
            assertNull(definitionsService.getConditionType("test1"));
            assertTrue(definitionsService.getConditionTypesByTag("tag1").isEmpty());
            assertTrue(definitionsService.getConditionTypesByTag("tag2").isEmpty());
        }

        @Test
        void shouldGetAllConditionTypes() {
            // given
            ConditionType type1 = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            ConditionType type2 = createTestConditionType("test2", new HashSet<>(Arrays.asList("tag2")), null);
            definitionsService.setConditionType(type1);
            definitionsService.setConditionType(type2);

            // when
            Collection<ConditionType> allTypes = definitionsService.getAllConditionTypes();

            // then
            assertEquals(2, allTypes.size());
            assertTrue(allTypes.contains(type1));
            assertTrue(allTypes.contains(type2));
        }

        @Test
        void shouldHandleConcurrentAccess() throws InterruptedException {
            int threadCount = 10;
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            Set<String> expectedTags = new HashSet<>();
            Set<String> expectedIds = new HashSet<>();

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                String tag = "tag" + index;
                String id = "test" + index;
                expectedTags.add(tag);
                expectedIds.add(id);

                new Thread(() -> {
                    try {
                        barrier.await();
                        tenantService.setCurrentTenant("test-tenant");
                        ConditionType conditionType = createTestConditionType(id, new HashSet<>(Arrays.asList(tag)), null);
                        definitionsService.setConditionType(conditionType);
                    } catch (Exception e) {
                        fail("Thread execution failed: " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                }).start();
            }

            assertTrue(endLatch.await(5, TimeUnit.SECONDS));

            // Verify all types were saved
            Collection<ConditionType> allTypes = definitionsService.getAllConditionTypes();
            assertEquals(threadCount, allTypes.size());

            // Verify all tags are present
            for (String tag : expectedTags) {
                Set<ConditionType> taggedTypes = definitionsService.getConditionTypesByTag(tag);
                assertEquals(1, taggedTypes.size());
                ConditionType taggedType = taggedTypes.iterator().next();
                assertTrue(taggedType.getMetadata().getTags().contains(tag));
            }
        }
    }

    @Nested
    class ActionTypeTests {
        @Test
        void shouldManageActionTypes() {
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            // given
            ActionType actionType = createTestActionType("test1", new HashSet<>(Arrays.asList("tag1")), null);

            // when
            definitionsService.setActionType(actionType);

            // then
            assertEquals(actionType, definitionsService.getActionType("test1"));
            assertTrue(definitionsService.getActionTypeByTag("tag1").contains(actionType));

            // when removing
            definitionsService.removeActionType("test1");

            // then
            assertNull(persistenceService.load("test1", ActionType.class), "Action type should be removed");
            assertTrue(definitionsService.getActionTypeByTag("tag1").isEmpty());
        }

        @Test
        void shouldGetAllActionTypes() {
            // given
            ActionType type1 = createTestActionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            ActionType type2 = createTestActionType("test2", new HashSet<>(Arrays.asList("tag2")), null);
            definitionsService.setActionType(type1);
            definitionsService.setActionType(type2);

            // when
            Collection<ActionType> allTypes = definitionsService.getAllActionTypes();

            // then
            assertEquals(2, allTypes.size());
            assertTrue(allTypes.contains(type1));
            assertTrue(allTypes.contains(type2));
        }
    }

    @Nested
    class PropertyMergeStrategyTypeTests {
        @Test
        void shouldManagePropertyMergeStrategyTypes() {
            // given
            PropertyMergeStrategyType type = createTestPropertyMergeStrategyType("test1", null);

            // when
            definitionsService.setPropertyMergeStrategyType(type);

            // then
            assertEquals(type, definitionsService.getPropertyMergeStrategyType("test1"));

            // when removing
            definitionsService.removePropertyMergeStrategyType("test1");

            // then
            assertNull(definitionsService.getPropertyMergeStrategyType("test1"));
        }
    }

    @Nested
    class TenantAwarenessTests {
        @Test
        void shouldHandleTenantLifecycle() {
            // Setup system tenant data
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            ConditionType systemConditionType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
            systemConditionType.setTenantId(SYSTEM_TENANT);
            definitionsService.setConditionType(systemConditionType);

            ActionType systemActionType = createTestActionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
            systemActionType.setTenantId(SYSTEM_TENANT);
            definitionsService.setActionType(systemActionType);

            // Setup tenant data
            tenantService.setCurrentTenant("tenant1");
            ConditionType tenantConditionType = createTestConditionType("test4", new HashSet<>(Arrays.asList("tag2")), null);
            tenantConditionType.setTenantId("tenant1");
            definitionsService.setConditionType(tenantConditionType);

            ActionType tenantActionType = createTestActionType("test5", new HashSet<>(Arrays.asList("tag2")), null);
            tenantActionType.setTenantId("tenant1");
            definitionsService.setActionType(tenantActionType);

            // Verify tenant data
            assertEquals(tenantConditionType, definitionsService.getConditionType("test4"));
            assertEquals(tenantActionType, definitionsService.getActionType("test5"));
            assertTrue(definitionsService.getConditionTypesByTag("tag2").contains(tenantConditionType));
            assertTrue(definitionsService.getActionTypeByTag("tag2").contains(tenantActionType));

            // Verify system tenant data is accessible
            assertNotNull(definitionsService.getConditionType("test1"));
            assertNotNull(definitionsService.getActionType("test2"));

            // Test tenant removal
            definitionsService.onTenantRemoved("tenant1");

            // Verify tenant data is removed
            assertNull(definitionsService.getConditionType("test4"));
            assertNull(definitionsService.getActionType("test5"));
            assertTrue(definitionsService.getConditionTypesByTag("tag2").isEmpty());
            assertTrue(definitionsService.getActionTypeByTag("tag2").isEmpty());

            // Verify system tenant data is preserved
            tenantService.setCurrentTenant(SYSTEM_TENANT);
            assertEquals(systemConditionType, definitionsService.getConditionType("test1"));
            assertEquals(systemActionType, definitionsService.getActionType("test2"));
        }

        @Test
        void shouldHandleConcurrentTenantAccess() throws InterruptedException {
            int threadCount = 5;
            CyclicBarrier barrier = new CyclicBarrier(threadCount * 2); // Two tenants
            CountDownLatch endLatch = new CountDownLatch(threadCount * 2);
            AtomicBoolean failed = new AtomicBoolean(false);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;

                // System tenant thread
                new Thread(() -> {
                    try {
                        barrier.await();
                        tenantService.setCurrentTenantId(SYSTEM_TENANT);
                        ConditionType conditionType = createTestConditionType("test" + index, new HashSet<>(Arrays.asList("tag1")), null);
                        definitionsService.setConditionType(conditionType);
                    } catch (Exception e) {
                        failed.set(true);
                        LOGGER.error("Thread execution failed", e);
                    } finally {
                        endLatch.countDown();
                    }
                }).start();

                // Custom tenant thread
                new Thread(() -> {
                    try {
                        barrier.await();
                        tenantService.setCurrentTenantId("tenant1");
                        ConditionType conditionType = createTestConditionType("test" + index + "-tenant", new HashSet<>(Arrays.asList("tag1")), null);
                        definitionsService.setConditionType(conditionType);
                    } catch (Exception e) {
                        failed.set(true);
                        LOGGER.error("Thread execution failed", e);
                    } finally {
                        endLatch.countDown();
                    }
                }).start();
            }

            assertTrue(endLatch.await(5, TimeUnit.SECONDS), "Test timed out");
            assertFalse(failed.get(), "One or more threads failed execution");

            // Verify tenant isolation
            tenantService.setCurrentTenantId(SYSTEM_TENANT);
            assertEquals(threadCount, definitionsService.getConditionTypesByTag("tag1").size());
            tenantService.setCurrentTenantId("tenant1");
            assertEquals(threadCount*2 /* Because of inheritance */, definitionsService.getConditionTypesByTag("tag1").size());
        }
    }

    // Helper methods to create test objects
    private ValueType createTestValueType(String id, Set<String> tags, Long pluginId) {
        ValueType valueType = new ValueType();
        valueType.setId(id);
        valueType.setTags(tags);
        if (pluginId != null) {
            valueType.setPluginId(pluginId);
        }
        return valueType;
    }

    private ConditionType createTestConditionType(String id, Set<String> tags, Long pluginId) {
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId(id);
        if (pluginId != null) {
            conditionType.setPluginId(pluginId);
        }
        Metadata metadata = new Metadata();
        metadata.setId(id);
        metadata.setTags(tags);
        conditionType.setMetadata(metadata);
        return conditionType;
    }

    private ActionType createTestActionType(String id, Set<String> tags, Long pluginId) {
        ActionType actionType = new ActionType();
        actionType.setItemId(id);
        if (pluginId != null) {
            actionType.setPluginId(pluginId);
        }
        Metadata metadata = new Metadata();
        metadata.setId(id);
        metadata.setTags(tags);
        actionType.setMetadata(metadata);
        return actionType;
    }

    private PropertyMergeStrategyType createTestPropertyMergeStrategyType(String id, Long pluginId) {
        PropertyMergeStrategyType type = new PropertyMergeStrategyType();
        if (pluginId != null) {
            type.setPluginId(pluginId);
        }
        type.setId(id);
        return type;
    }

    // Helper method to register plugin types
    private void registerPluginType(PluginType type, Long bundleId) {
        if (type == null || bundleId == null) {
            return;
        }

        // Set up the bundle context if needed
        if (bundle == null) {
            bundle = mock(Bundle.class);
            when(bundle.getBundleId()).thenReturn(bundleId);
            when(bundleContext.getBundle()).thenReturn(bundle);
        }

        // Register the type with the definitions service
        if (type instanceof ConditionType) {
            definitionsService.setConditionType((ConditionType) type);
        } else if (type instanceof ActionType) {
            definitionsService.setActionType((ActionType) type);
        } else if (type instanceof ValueType) {
            definitionsService.setValueType((ValueType) type);
        } else if (type instanceof PropertyMergeStrategyType) {
            definitionsService.setPropertyMergeStrategyType((PropertyMergeStrategyType) type);
        }

        // Add to plugin types tracking
        synchronized(definitionsService.getTypesByPlugin()) {
            List<PluginType> bundlePluginTypes = definitionsService.getTypesByPlugin()
                .computeIfAbsent(bundleId, k -> new CopyOnWriteArrayList<>());
            bundlePluginTypes.add(type);
        }
    }

    private void closeQuietly(InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                LOGGER.error("Error closing stream", e);
            }
        }
    }

    // Test utilities for concurrent testing
    private static class ConcurrentTestHelper {
        private final CyclicBarrier barrier;
        private final CountDownLatch endLatch;
        private final AtomicBoolean failed;
        private final List<Thread> threads;

        public ConcurrentTestHelper(int threadCount) {
            this.barrier = new CyclicBarrier(threadCount);
            this.endLatch = new CountDownLatch(threadCount);
            this.failed = new AtomicBoolean(false);
            this.threads = Collections.synchronizedList(new ArrayList<>());
        }

        public void executeAndVerify(String testName, int timeoutSeconds) throws InterruptedException {
            try {
                assertTrue(endLatch.await(timeoutSeconds, TimeUnit.SECONDS),
                    testName + " timed out waiting for threads");
                assertFalse(failed.get(), testName + " had thread failures");
            } finally {
                cleanupThreads();
            }
        }

        public void startThread(String name, ThrowingRunnable task) {
            Thread thread = new Thread(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    task.run();
                } catch (Exception e) {
                    failed.set(true);
                    LOGGER.error("Thread execution failed in {}", name, e);
                } finally {
                    endLatch.countDown();
                }
            }, name);
            threads.add(thread);
            thread.start();
        }

        private void cleanupThreads() {
            for (Thread thread : threads) {
                if (thread.isAlive()) {
                    thread.interrupt();
                    try {
                        thread.join(1000);
                        if (thread.isAlive()) {
                            LOGGER.warn("Thread {} could not be stopped gracefully", thread.getName());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOGGER.error("Interrupted while waiting for thread cleanup", e);
                        break;
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    // Test utilities for resource management
    private static class TestResource implements AutoCloseable {
        private final InputStream stream;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        public TestResource(String content) {
            this.stream = new ByteArrayInputStream(content.getBytes());
        }

        public InputStream getStream() {
            return stream;
        }

        @Override
        public void close() {
            if (!closed.getAndSet(true)) {
                try {
                    stream.close();
                } catch (IOException e) {
                    LOGGER.error("Error closing test resource", e);
                }
            }
        }

        public boolean wasClosed() {
            return closed.get();
        }
    }

    // Simplified test setup methods
    private void setupMockBundle(long bundleId) {
        when(bundle.getBundleId()).thenReturn(bundleId);
        when(bundle.getBundleContext()).thenReturn(bundleContext);
        when(bundleContext.getBundle()).thenReturn(bundle);
    }

    private void setupTenantContext(String tenantId) {
        tenantService.setCurrentTenantId(tenantId);
    }

    private URL createTestURL(final InputStream content) {
        try {
            return new URL("memory", "", -1, "/test-condition.json", new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL u) {
                    return new URLConnection(u) {
                        private boolean connected = false;

                        @Override
                        public void connect() {
                            if (!connected) {
                                connected = true;
                            }
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new BufferedInputStream(content) {
                                private volatile boolean closed = false;

                                @Override
                                public void close() throws IOException {
                                    if (!closed) {
                                        try {
                                            super.close();
                                        } finally {
                                            content.close();
                                            closed = true;
                                        }
                                    }
                                }
                            };
                        }
                    };
                }
            });
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
