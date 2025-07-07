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
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.apache.unomi.services.common.security.KarafSecurityService;
import org.apache.unomi.services.impl.*;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.apache.unomi.services.impl.validation.ConditionValidationServiceImpl;
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
    private org.apache.unomi.api.services.SchedulerService schedulerService;

    private TestTenantService tenantService;
    private DefinitionsServiceImpl definitionsService;
    private InMemoryPersistenceServiceImpl persistenceService;
    private MultiTypeCacheServiceImpl multiTypeCacheService;
    private KarafSecurityService securityService;
    private ExecutionContextManagerImpl executionContextManager;
    private ConditionValidationServiceImpl conditionValidationService;

    @BeforeEach
    void setUp() {
        tenantService = new TestTenantService();
        tenantService.setCurrentTenantId("test-tenant");
        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();
        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);
        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);
        conditionValidationService = new ConditionValidationServiceImpl();
        // Mock bundle context
        bundleContext = TestHelper.createMockBundleContext();
        schedulerService = TestHelper.createSchedulerService("definitions-service-scheduler-node", persistenceService, executionContextManager, bundleContext, null, -1, true, true);
        // Create scheduler service using TestHelper
        multiTypeCacheService = new MultiTypeCacheServiceImpl();
        definitionsService = TestHelper.createDefinitionService(persistenceService, bundleContext, schedulerService, multiTypeCacheService, executionContextManager, tenantService, conditionValidationService);
    }

    @Nested
    class BundleLifecycleTests {
        @Test
        void shouldHandleBundleStartup() {
            // given
            when(bundle.getBundleId()).thenReturn(1L);
            when(bundleContext.getBundle()).thenReturn(bundle);

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
                            executionContextManager.executeAsSystem(() -> {
                                ConditionType conditionType = createTestConditionType("test" + index, new HashSet<>(Arrays.asList("tag1")), null);
                                definitionsService.setConditionType(conditionType);

                            });
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
                            executionContextManager.executeAsTenant("tenant1", () -> {
                                ConditionType conditionType = createTestConditionType("test" + index + "-tenant", new HashSet<>(Arrays.asList("tag1")), null);
                                definitionsService.setConditionType(conditionType);
                            });
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
            executionContextManager.executeAsSystem(() -> {
                assertEquals(threadCount, definitionsService.getConditionTypesByTag("tag1").size());
            });
            executionContextManager.executeAsTenant("tenant1", () -> {
                assertEquals(threadCount*2, definitionsService.getConditionTypesByTag("tag1").size());
            });
        }

        @Test
        void shouldHandleTenantRemoval() {
            // Add types for multiple tenants
            executionContextManager.executeAsSystem(() -> {
                ConditionType systemType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
                definitionsService.setConditionType(systemType);
            });
            executionContextManager.executeAsTenant("tenant1", () -> {
                ConditionType tenantType = createTestConditionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
                definitionsService.setConditionType(tenantType);
            });

            // Simulate tenant removal
            definitionsService.onTenantRemoved("tenant1");

            // Verify tenant caches are cleared
            executionContextManager.executeAsTenant("tenant1", () -> {
                assertEquals(1, definitionsService.getConditionTypesByTag("tag1").size());
                assertNull(definitionsService.getConditionType("test2"));
            });

            // Verify system tenant is unaffected
            executionContextManager.executeAsSystem(() -> {
                assertFalse(definitionsService.getConditionTypesByTag("tag1").isEmpty());
                assertNotNull(definitionsService.getConditionType("test1"));
            });
        }

        @Test
        void shouldHandleConcurrentAccessDuringTenantRemoval() throws InterruptedException {
            int numThreads = 10;
            ConcurrentTestHelper testHelper = new ConcurrentTestHelper(numThreads);

            executionContextManager.executeAsTenant("tenant1", () -> {
                ConditionType tenantType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
                definitionsService.setConditionType(tenantType);
            });

            // Create threads that will access the service while tenant is being removed
            for (int i = 0; i < numThreads; i++) {
                testHelper.startThread("TenantAccess-" + i, () -> {
                    try {
                        executionContextManager.executeAsTenant("tenant1", () -> {
                            try {
                                definitionsService.getConditionType("test1");
                                definitionsService.getConditionTypesByTag("tag1");
                                Thread.sleep(10); // Simulate some work
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Thread interrupted during test", e);
                            }
                        });
                    } catch (Exception e) {
                        throw new RuntimeException("Error executing in tenant context", e);
                    }
                });
            }

            executionContextManager.executeAsTenant("tenant1", () -> {
                definitionsService.onTenantRemoved("tenant1");
                try {
                    testHelper.executeAndVerify("Tenant removal test", 5);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                assertTrue(definitionsService.getConditionTypesByTag("tag1").isEmpty());
                assertNull(definitionsService.getConditionType("test1"));
            });
        }

        @Test
        void shouldPreventSystemTenantRemoval() {
            // Try to remove system tenant
            definitionsService.onTenantRemoved(SYSTEM_TENANT);

            // Verify system tenant data is still accessible
            executionContextManager.executeAsSystem(() -> {
                ConditionType systemType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
                definitionsService.setConditionType(systemType);
            });

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
            executionContextManager.executeAsSystem(() -> {
                // Add all types of definitions
                ValueType valueType = createTestValueType("test1", new HashSet<>(Arrays.asList("tag1")), bundleId);
                ConditionType conditionType = createTestConditionType("test2", new HashSet<>(Arrays.asList("tag1")), bundleId);
                ActionType actionType = createTestActionType("test3", new HashSet<>(Arrays.asList("tag1")), bundleId);

                registerPluginType(valueType, bundleId);
                registerPluginType(conditionType, bundleId);
                registerPluginType(actionType, bundleId);
            });

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
            executionContextManager.executeAsSystem(() -> {
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
            });
        }

        @Test
        void shouldHandleValueTypeInheritance() {
            // Create system tenant value type
            ValueType valueType = new ValueType();
            valueType.setId("test");
            valueType.setTags(Collections.singleton("testTag"));

            // Add to system tenant cache
            executionContextManager.executeAsSystem(() -> {
                definitionsService.setValueType(valueType);
            });

            // Switch to different tenant and verify inheritance
            executionContextManager.executeAsTenant("tenant1", () -> {
                ValueType result = definitionsService.getValueType("test");
                assertNotNull(result);
                assertEquals("test", result.getId());

                // Second lookup should use cache
                result = definitionsService.getValueType("test");
                assertNotNull(result);
                assertEquals("test", result.getId());
            });
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
                        executionContextManager.executeAsTenant("test-tenant", () -> {
                            ActionType actionType = createTestActionType(id, new HashSet<>(Arrays.asList(tag)), null);
                            definitionsService.setActionType(actionType);
                        });
                    } catch (Exception e) {
                        fail("Thread execution failed: " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                }).start();
            }

            assertTrue(endLatch.await(5, TimeUnit.SECONDS));

            executionContextManager.executeAsTenant("test-tenant", () -> {
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
            });
        }

        @Test
        void shouldMaintainTagCacheConsistency() {
            // given
            executionContextManager.executeAsTenant("tenant1", () -> {

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
            });
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
            final ConditionType[] systemType = new ConditionType[1];
            executionContextManager.executeAsSystem(() -> {
                systemType[0] = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
                definitionsService.setConditionType(systemType[0]);
            });

            // Add custom tenant types
            executionContextManager.executeAsTenant("tenant1", () -> {
                ConditionType tenantType = createTestConditionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
                definitionsService.setConditionType(tenantType);
            });

            // Remove tenant
            definitionsService.onTenantRemoved("tenant1");

            // Verify tenant caches are cleared
            executionContextManager.executeAsTenant("tenant1", () -> {
                assertEquals(1, definitionsService.getConditionTypesByTag("tag1").size());
                assertNull(definitionsService.getConditionType("test2"));
            });

            // Verify system tenant is unaffected
            executionContextManager.executeAsSystem(() -> {
                Set<ConditionType> systemTypes = definitionsService.getConditionTypesByTag("tag1");
                assertEquals(1, systemTypes.size());
                assertTrue(systemTypes.contains(systemType[0]));
                assertEquals(systemType[0], definitionsService.getConditionType("test1"));
            });
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
            final ConditionType[] conditionType = new ConditionType[1];
            final ActionType[] actionType = new ActionType[1];
            final ValueType[] valueType = new ValueType[1];

            executionContextManager.executeAsSystem(() -> {
                conditionType[0] = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
                definitionsService.setConditionType(conditionType[0]);
            });
            executionContextManager.executeAsSystem(() -> {
                actionType[0] = createTestActionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
                definitionsService.setActionType(actionType[0]);
            });
            executionContextManager.executeAsSystem(() -> {
                valueType[0] = createTestValueType("test3", new HashSet<>(Arrays.asList("tag1")), null);
                definitionsService.setValueType(valueType[0]);
            });

            // Switch to custom tenant
            executionContextManager.executeAsTenant("tenant1", () -> {
                // Verify inheritance for condition types
                assertEquals(conditionType[0], definitionsService.getConditionType("test1"));
                assertTrue(definitionsService.getConditionTypesByTag("tag1").contains(conditionType[0]));

                // Verify inheritance for action types
                assertEquals(actionType[0], definitionsService.getActionType("test2"));
                assertTrue(definitionsService.getActionTypeByTag("tag1").contains(actionType[0]));

                // Verify inheritance for value types
                assertEquals(valueType[0], definitionsService.getValueType("test3"));
                assertTrue(definitionsService.getValueTypeByTag("tag1").contains(valueType[0]));

                // Verify items were saved in persistence service
                ConditionType savedCondition = definitionsService.getConditionType("test1");
                ActionType savedAction = definitionsService.getActionType("test2");
                assertNotNull(savedCondition, "Condition type should be saved");
                assertNotNull(savedAction, "Action type should be saved");
                assertEquals(SYSTEM_TENANT, savedCondition.getTenantId());
                assertEquals(SYSTEM_TENANT, savedAction.getTenantId());
            });
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
                        executionContextManager.executeAsTenant(SYSTEM_TENANT, () -> {
                            ConditionType conditionType = createTestConditionType("test" + index, new HashSet<>(Arrays.asList("tag1")), null);
                            definitionsService.setConditionType(conditionType);
                        });
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
                        executionContextManager.executeAsTenant("tenant1", () -> {
                            ConditionType conditionType = createTestConditionType("test" + index + "-tenant", new HashSet<>(Arrays.asList("tag1")), null);
                            definitionsService.setConditionType(conditionType);
                        });
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
            executionContextManager.executeAsTenant(SYSTEM_TENANT, () -> {
                assertEquals(threadCount, definitionsService.getConditionTypesByTag("tag1").size());
            });
            executionContextManager.executeAsTenant("tenant1", () -> {
                assertEquals(threadCount*2 /* Because of inheritance */, definitionsService.getConditionTypesByTag("tag1").size());
            });
        }

        @Test
        void shouldHandleTenantLifecycle() {
            // Setup system tenant data
            final ConditionType[] systemConditionType = new ConditionType[1];
            final ActionType[] systemActionType = new ActionType[1];

            executionContextManager.executeAsTenant(SYSTEM_TENANT, () -> {
                systemConditionType[0] = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
                systemConditionType[0].setTenantId(SYSTEM_TENANT);
                definitionsService.setConditionType(systemConditionType[0]);

                systemActionType[0] = createTestActionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
                systemActionType[0].setTenantId(SYSTEM_TENANT);
                definitionsService.setActionType(systemActionType[0]);
            });

            // Setup tenant data
            executionContextManager.executeAsTenant("tenant1", () -> {
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
            });

            // Verify system tenant data is preserved
            executionContextManager.executeAsTenant(SYSTEM_TENANT, () -> {
                assertEquals(systemConditionType[0], definitionsService.getConditionType("test1"));
                assertEquals(systemActionType[0], definitionsService.getActionType("test2"));
            });
        }

        @Test
        void shouldHandleConcurrentAccess() throws InterruptedException {
            int numThreads = 10;
            ConcurrentTestHelper testHelper = new ConcurrentTestHelper(numThreads);

            // Setup initial data
            executionContextManager.executeAsTenant("tenant1", () -> {
                ConditionType tenantType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
                definitionsService.setConditionType(tenantType);
            });

            // Create threads that will access the service while tenant is being removed
            for (int i = 0; i < numThreads; i++) {
                testHelper.startThread("TenantAccess-" + i, () -> {
                    try {
                        executionContextManager.executeAsTenant("tenant1", () -> {
                            try {
                                definitionsService.getConditionType("test1");
                                definitionsService.getConditionTypesByTag("tag1");
                                Thread.sleep(10); // Simulate some work
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Thread interrupted during test", e);
                            }
                        });
                    } catch (Exception e) {
                        throw new RuntimeException("Error executing in tenant context", e);
                    }
                });
            }

            try {
                definitionsService.onTenantRemoved("tenant1");
                testHelper.executeAndVerify("Tenant removal test", 5);

                // Verify tenant was properly removed
                executionContextManager.executeAsTenant("tenant1", () -> {
                    assertTrue(definitionsService.getConditionTypesByTag("tag1").isEmpty());
                    assertNull(definitionsService.getConditionType("test1"));
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }

        @Test
        void shouldPreferTenantSpecificTypes() {
            // Create system tenant types
            final ConditionType[] systemConditionType = new ConditionType[1];
            final ActionType[] systemActionType = new ActionType[1];
            final ValueType[] systemValueType = new ValueType[1];

            executionContextManager.executeAsTenant(SYSTEM_TENANT, () -> {
                systemConditionType[0] = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
                systemConditionType[0].setTenantId(SYSTEM_TENANT);
                definitionsService.setConditionType(systemConditionType[0]);

                systemActionType[0] = createTestActionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
                systemActionType[0].setTenantId(SYSTEM_TENANT);
                definitionsService.setActionType(systemActionType[0]);

                systemValueType[0] = createTestValueType("test3", new HashSet<>(Arrays.asList("tag1")), null);
                definitionsService.setValueType(systemValueType[0]);
            });

            // Create tenant-specific types with same IDs but different tags
            executionContextManager.executeAsTenant("tenant1", () -> {
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
                assertTrue(definitionsService.getConditionTypesByTag("tag1").contains(systemConditionType[0]));

                assertTrue(definitionsService.getActionTypeByTag("tag2").contains(tenantActionType));
                assertTrue(definitionsService.getActionTypeByTag("tag1").contains(systemActionType[0]));

                assertTrue(definitionsService.getValueTypeByTag("tag2").contains(tenantValueType));
                assertTrue(definitionsService.getValueTypeByTag("tag1").contains(systemValueType[0]));
            });
        }

        @Test
        void shouldHandleTypeRemovalWithInheritance() {
            // Create system tenant types
            final ConditionType[] systemConditionType = new ConditionType[1];
            final ActionType[] systemActionType = new ActionType[1];
            final ValueType[] systemValueType = new ValueType[1];

            executionContextManager.executeAsTenant(SYSTEM_TENANT, () -> {
                systemConditionType[0] = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
                systemConditionType[0].setTenantId(SYSTEM_TENANT);
                definitionsService.setConditionType(systemConditionType[0]);
                // Verify system tenant type was saved
                assertEquals(systemConditionType[0], definitionsService.getConditionType("test1"));

                systemActionType[0] = createTestActionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
                systemActionType[0].setTenantId(SYSTEM_TENANT);
                definitionsService.setActionType(systemActionType[0]);
                // Verify system tenant type was saved
                assertEquals(systemActionType[0], definitionsService.getActionType("test2"));

                systemValueType[0] = createTestValueType("test3", new HashSet<>(Arrays.asList("tag1")), null);
                definitionsService.setValueType(systemValueType[0]);
                // Verify system tenant type was saved
                assertEquals(systemValueType[0], definitionsService.getValueType("test3"));
            });

            // Create tenant-specific types with same IDs
            executionContextManager.executeAsTenant("tenant1", () -> {
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
                assertEquals(systemConditionType[0], definitionsService.getConditionType("test1"));
                assertEquals(systemActionType[0], definitionsService.getActionType("test2"));
                assertEquals(systemValueType[0], definitionsService.getValueType("test3"));

                // Verify tag-based queries
                assertFalse(definitionsService.getConditionTypesByTag("tag2").contains(tenantConditionType));
                assertFalse(definitionsService.getActionTypeByTag("tag2").contains(tenantActionType));
                assertFalse(definitionsService.getValueTypeByTag("tag2").contains(tenantValueType));

                assertTrue(definitionsService.getConditionTypesByTag("tag1").contains(systemConditionType[0]));
                assertTrue(definitionsService.getActionTypeByTag("tag1").contains(systemActionType[0]));
                assertTrue(definitionsService.getValueTypeByTag("tag1").contains(systemValueType[0]));
            });

            // Switch to system tenant and verify types still exist
            executionContextManager.executeAsTenant(SYSTEM_TENANT, () -> {
                assertEquals(systemConditionType[0], definitionsService.getConditionType("test1"));
                assertEquals(systemActionType[0], definitionsService.getActionType("test2"));
                assertEquals(systemValueType[0], definitionsService.getValueType("test3"));
            });
        }

        @Test
        void shouldHandleBundleTypeRemovalWithInheritance() {
            // Create system tenant types from a bundle
            Long bundleId = 1L;
            setupMockBundle(bundleId);

            executionContextManager.executeAsTenant(SYSTEM_TENANT, () -> {
                ConditionType systemConditionType = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), bundleId);
                systemConditionType.setTenantId(SYSTEM_TENANT);
                registerPluginType(systemConditionType, bundleId);

                ActionType systemActionType = createTestActionType("test2", new HashSet<>(Arrays.asList("tag1")), bundleId);
                systemActionType.setTenantId(SYSTEM_TENANT);
                registerPluginType(systemActionType, bundleId);

                ValueType systemValueType = createTestValueType("test3", new HashSet<>(Arrays.asList("tag1")), bundleId);
                registerPluginType(systemValueType, bundleId);
            });

            // Create tenant-specific overrides
            executionContextManager.executeAsTenant("tenant1", () -> {
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
            });
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
                        executionContextManager.executeAsTenant("test-tenant", () -> {
                            ValueType valueType = createTestValueType(id, new HashSet<>(Arrays.asList(tag)), null);
                            definitionsService.setValueType(valueType);
                        });
                    } catch (Exception e) {
                        fail("Thread execution failed: " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                }).start();
            }

            assertTrue(endLatch.await(5, TimeUnit.SECONDS));

            executionContextManager.executeAsTenant("test-tenant", () -> {
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
            });
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
                        executionContextManager.executeAsTenant("test-tenant", () -> {
                            ConditionType conditionType = createTestConditionType(id, new HashSet<>(Arrays.asList(tag)), null);
                            definitionsService.setConditionType(conditionType);
                        });
                    } catch (Exception e) {
                        fail("Thread execution failed: " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                }).start();
            }

            assertTrue(endLatch.await(10, TimeUnit.SECONDS));

            executionContextManager.executeAsTenant("test-tenant", () -> {
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
            });

        }
    }

    @Nested
    class ActionTypeTests {
        @Test
        void shouldManageActionTypes() {
            executionContextManager.executeAsTenant(SYSTEM_TENANT, () -> {
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
            });
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
            final ConditionType[] systemConditionType = new ConditionType[1];
            final ActionType[] systemActionType = new ActionType[1];

            executionContextManager.executeAsTenant(SYSTEM_TENANT, () -> {
                systemConditionType[0] = createTestConditionType("test1", new HashSet<>(Arrays.asList("tag1")), null);
                systemConditionType[0].setTenantId(SYSTEM_TENANT);
                definitionsService.setConditionType(systemConditionType[0]);

                systemActionType[0] = createTestActionType("test2", new HashSet<>(Arrays.asList("tag1")), null);
                systemActionType[0].setTenantId(SYSTEM_TENANT);
                definitionsService.setActionType(systemActionType[0]);
            });

            // Setup tenant data
            executionContextManager.executeAsTenant("tenant1", () -> {
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
            });

            // Verify system tenant data is preserved
            executionContextManager.executeAsTenant(SYSTEM_TENANT, () -> {
                assertEquals(systemConditionType[0], definitionsService.getConditionType("test1"));
                assertEquals(systemActionType[0], definitionsService.getActionType("test2"));
            });
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
                        executionContextManager.executeAsTenant(SYSTEM_TENANT, () -> {
                            ConditionType conditionType = createTestConditionType("test" + index, new HashSet<>(Arrays.asList("tag1")), null);
                            definitionsService.setConditionType(conditionType);
                        });
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
                        executionContextManager.executeAsTenant("tenant1", () -> {
                            ConditionType conditionType = createTestConditionType("test" + index + "-tenant", new HashSet<>(Arrays.asList("tag1")), null);
                            definitionsService.setConditionType(conditionType);
                        });
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
            executionContextManager.executeAsTenant(SYSTEM_TENANT, () -> {
                assertEquals(threadCount, definitionsService.getConditionTypesByTag("tag1").size());
            });
            executionContextManager.executeAsTenant("tenant1", () -> {
                assertEquals(threadCount*2 /* Because of inheritance */, definitionsService.getConditionTypesByTag("tag1").size());
            });
        }
    }

    @Nested
    class ExtractConditionTests {
        @Test
        void shouldExtractConditionsByType() {
            // Create a complex condition structure
            ConditionType propertyType = createTestConditionType("propertyCondition", new HashSet<>(), null);
            ConditionType booleanType = createTestConditionType("booleanCondition", new HashSet<>(), null);
            definitionsService.setConditionType(propertyType);
            definitionsService.setConditionType(booleanType);

            Condition property1 = new Condition(propertyType);
            property1.setParameter("propertyName", "prop1");

            Condition property2 = new Condition(propertyType);
            property2.setParameter("propertyName", "prop2");

            Condition andCondition = new Condition(booleanType);
            andCondition.setParameter("operator", "and");
            andCondition.setParameter("subConditions", Arrays.asList(property1, property2));

            // Test extraction
            List<Condition> extracted = definitionsService.extractConditionsByType(andCondition, "propertyCondition");
            assertEquals(2, extracted.size());
            assertTrue(extracted.contains(property1));
            assertTrue(extracted.contains(property2));

            // Test with non-existent type
            List<Condition> emptyResult = definitionsService.extractConditionsByType(andCondition, "nonExistentType");
            assertTrue(emptyResult.isEmpty());

            // Test with null inputs
            assertTrue(definitionsService.extractConditionsByType(null, "propertyCondition").isEmpty());
            assertTrue(definitionsService.extractConditionsByType(andCondition, null).isEmpty());
        }

        @Test
        void shouldHandleDeepNestedConditions() {
            // Create condition types
            ConditionType propertyType = createTestConditionType("propertyCondition", new HashSet<>(), null);
            ConditionType booleanType = createTestConditionType("booleanCondition", new HashSet<>(), null);
            definitionsService.setConditionType(propertyType);
            definitionsService.setConditionType(booleanType);

            // Create a deeply nested condition structure
            Condition property1 = new Condition(propertyType);
            Condition property2 = new Condition(propertyType);
            Condition property3 = new Condition(propertyType);

            Condition innerAnd = new Condition(booleanType);
            innerAnd.setParameter("operator", "and");
            innerAnd.setParameter("subConditions", Arrays.asList(property2, property3));

            Condition outerAnd = new Condition(booleanType);
            outerAnd.setParameter("operator", "and");
            outerAnd.setParameter("subConditions", Arrays.asList(property1, innerAnd));

            // Test extraction
            List<Condition> extracted = definitionsService.extractConditionsByType(outerAnd, "propertyCondition");
            assertEquals(3, extracted.size());
            assertTrue(extracted.contains(property1));
            assertTrue(extracted.contains(property2));
            assertTrue(extracted.contains(property3));
        }

        @Test
        void shouldHandleInvalidSubConditions() {
            ConditionType booleanType = createTestConditionType("booleanCondition", new HashSet<>(), null);
            definitionsService.setConditionType(booleanType);

            // Create condition with invalid subConditions parameter
            Condition invalidCondition = new Condition(booleanType);
            invalidCondition.setParameter("operator", "and");
            invalidCondition.setParameter("subConditions", "not a list"); // Invalid type

            List<Condition> result = definitionsService.extractConditionsByType(invalidCondition, "anyType");
            assertTrue(result.isEmpty());

            // Test with list containing non-Condition objects
            Condition invalidListCondition = new Condition(booleanType);
            invalidListCondition.setParameter("operator", "and");
            invalidListCondition.setParameter("subConditions", Arrays.asList("not a condition", 123)); // Invalid list contents

            result = definitionsService.extractConditionsByType(invalidListCondition, "anyType");
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldExtractConditionBySystemTag() {
            // Create condition types with system tags
            ConditionType taggedType = createTestConditionType("taggedCondition", new HashSet<>(), null);
            taggedType.getMetadata().setSystemTags(new HashSet<>(Arrays.asList("testTag")));
            ConditionType untaggedType = createTestConditionType("untaggedCondition", new HashSet<>(), null);
            definitionsService.setConditionType(taggedType);
            definitionsService.setConditionType(untaggedType);

            // Create test conditions
            Condition taggedCondition = new Condition(taggedType);
            Condition untaggedCondition = new Condition(untaggedType);

            // Create boolean condition containing both
            ConditionType booleanType = createTestConditionType("booleanCondition", new HashSet<>(), null);
            definitionsService.setConditionType(booleanType);
            Condition booleanCondition = new Condition(booleanType);
            booleanCondition.setParameter("operator", "and");
            booleanCondition.setParameter("subConditions", Arrays.asList(taggedCondition, untaggedCondition));

            // Test extraction
            Condition extracted = definitionsService.extractConditionBySystemTag(booleanCondition, "testTag");
            assertNotNull(extracted);
            assertEquals(taggedCondition, extracted);

            // Test with non-existent tag
            assertNull(definitionsService.extractConditionBySystemTag(booleanCondition, "nonExistentTag"));

            // Test with null inputs
            assertNull(definitionsService.extractConditionBySystemTag(null, "testTag"));
            assertNull(definitionsService.extractConditionBySystemTag(booleanCondition, null));
        }

        @Test
        void shouldHandleComplexSystemTagExtraction() {
            // Create condition types with system tags
            ConditionType taggedType = createTestConditionType("taggedCondition", new HashSet<>(), null);
            taggedType.getMetadata().setSystemTags(new HashSet<>(Arrays.asList("testTag")));
            ConditionType booleanType = createTestConditionType("booleanCondition", new HashSet<>(), null);
            definitionsService.setConditionType(taggedType);
            definitionsService.setConditionType(booleanType);

            // Create multiple tagged conditions
            Condition taggedCondition1 = new Condition(taggedType);
            Condition taggedCondition2 = new Condition(taggedType);

            // Create nested boolean structure
            Condition innerAnd = new Condition(booleanType);
            innerAnd.setParameter("operator", "and");
            innerAnd.setParameter("subConditions", Arrays.asList(taggedCondition1, taggedCondition2));

            // Test extraction returns combined boolean condition
            Condition extracted = definitionsService.extractConditionBySystemTag(innerAnd, "testTag");
            assertNotNull(extracted);
            assertEquals(innerAnd, extracted);

            // Test with single matching condition
            Condition singleAnd = new Condition(booleanType);
            singleAnd.setParameter("operator", "and");
            singleAnd.setParameter("subConditions", Collections.singletonList(taggedCondition1));

            extracted = definitionsService.extractConditionBySystemTag(singleAnd, "testTag");
            assertNotNull(extracted);
            assertEquals(singleAnd, extracted);
        }

        @Test
        void shouldHandleInvalidSystemTagExtraction() {
            // Create condition types
            ConditionType taggedType = createTestConditionType("taggedCondition", new HashSet<>(), null);
            taggedType.getMetadata().setSystemTags(new HashSet<>(Arrays.asList("testTag")));
            ConditionType booleanType = createTestConditionType("booleanCondition", new HashSet<>(), null);
            definitionsService.setConditionType(taggedType);
            definitionsService.setConditionType(booleanType);

            // Test with invalid subConditions parameter
            Condition invalidCondition = new Condition(booleanType);
            invalidCondition.setParameter("operator", "and");
            invalidCondition.setParameter("subConditions", "invalid");
            assertNull(definitionsService.extractConditionBySystemTag(invalidCondition, "testTag"));

            // Test with condition type having no metadata
            ConditionType noMetadataType = createTestConditionType("noMetadata", new HashSet<>(), null);
            noMetadataType.setMetadata(null);
            Condition noMetadataCondition = new Condition(noMetadataType);
            assertNull(definitionsService.extractConditionBySystemTag(noMetadataCondition, "testTag"));

            // Test with condition type having no system tags
            ConditionType noTagsType = createTestConditionType("noTags", new HashSet<>(), null);
            Condition noTagsCondition = new Condition(noTagsType);
            assertNull(definitionsService.extractConditionBySystemTag(noTagsCondition, "testTag"));
        }

        @SuppressWarnings("deprecation")
        @Test
        void shouldHandleDeprecatedExtractConditionByTag() {
            // Create condition types with tags
            ConditionType taggedType = createTestConditionType("taggedCondition", new HashSet<>(Arrays.asList("testTag")), null);
            ConditionType untaggedType = createTestConditionType("untaggedCondition", new HashSet<>(), null);
            definitionsService.setConditionType(taggedType);
            definitionsService.setConditionType(untaggedType);

            // Create test conditions
            Condition taggedCondition = new Condition(taggedType);
            Condition untaggedCondition = new Condition(untaggedType);

            // Test simple extraction
            Condition extracted = definitionsService.extractConditionByTag(taggedCondition, "testTag");
            assertEquals(taggedCondition, extracted);

            // Test with boolean AND condition
            ConditionType booleanType = createTestConditionType("booleanCondition", new HashSet<>(), null);
            definitionsService.setConditionType(booleanType);
            Condition booleanCondition = new Condition(booleanType);
            booleanCondition.setParameter("operator", "and");
            booleanCondition.setParameter("subConditions", Arrays.asList(taggedCondition, taggedCondition));

            extracted = definitionsService.extractConditionByTag(booleanCondition, "testTag");
            assertNotNull(extracted);
            assertEquals(booleanCondition, extracted);

            // Test with mixed conditions
            booleanCondition.setParameter("subConditions", Arrays.asList(taggedCondition, untaggedCondition));
            extracted = definitionsService.extractConditionByTag(booleanCondition, "testTag");
            assertNotNull(extracted);
            assertEquals(taggedCondition, extracted);
        }
    }

    @Nested
    class RefreshTimerTests {
        @Test
        void shouldScheduleAndExecuteRefreshTask() throws InterruptedException {
            // Create a test condition type
            ConditionType testType = createTestConditionType("testCondition", new HashSet<>(), null);
            definitionsService.setConditionType(testType);

            // Set a short refresh interval for testing
            definitionsService.setDefinitionsRefreshInterval(100);

            // Wait for at least one refresh cycle
            Thread.sleep(150);

            // Verify the condition type is still available
            ConditionType retrieved = definitionsService.getConditionType("testCondition");
            assertNotNull(retrieved);
            assertEquals(testType.getItemId(), retrieved.getItemId());
        }

        @Test
        void shouldHandleRefreshFailureGracefully() throws InterruptedException {
            // Save original persistence service
            org.apache.unomi.persistence.spi.PersistenceService originalPersistence = definitionsService.getPersistenceService();

            try {
                // Create a test condition type before breaking persistence
                ConditionType testType = createTestConditionType("testCondition", new HashSet<>(), null);
                definitionsService.setConditionType(testType);

                // Replace with failing persistence service
                definitionsService.setPersistenceService(null);

                // Set a short refresh interval
                definitionsService.setDefinitionsRefreshInterval(100);

                // Wait for at least one refresh cycle
                Thread.sleep(150);

                // Service should still be operational with in-memory data
                assertNotNull(definitionsService.getConditionType("testCondition"));
            } finally {
                // Restore original persistence service
                definitionsService.setPersistenceService(originalPersistence);
            }
        }

        @Test
        void shouldStopRefreshOnShutdown() throws Exception {
            // Set a short refresh interval
            definitionsService.setDefinitionsRefreshInterval(100);

            // Trigger shutdown
            definitionsService.preDestroy();

            try {
                // Verify the service is marked as shutdown
                java.lang.reflect.Field isShutdownField = DefinitionsServiceImpl.class.getDeclaredField("isShutdown");
                isShutdownField.setAccessible(true);
                assertTrue((Boolean) isShutdownField.get(definitionsService));

                // Wait for potential refresh cycle
                Thread.sleep(150);

                // Service should still respond to direct calls but not refresh
                ConditionType testType = createTestConditionType("testCondition", new HashSet<>(), null);
                definitionsService.setConditionType(testType);
                assertNotNull(definitionsService.getConditionType("testCondition"));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new Exception("Failed to access isShutdown field", e);
            }
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

    /**
     * Creates a test condition type with specified configuration.
     * Initializes a condition type with the provided ID, tags, and plugin ID.
     *
     * @param id The unique identifier for the condition type
     * @param tags The set of tags to associate with the condition type
     * @param pluginId The ID of the plugin that owns this condition type, or null if none
     * @return A configured ConditionType instance
     */
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

    /**
     * Creates a test action type with specified configuration.
     * Initializes an action type with the provided ID, tags, and plugin ID.
     *
     * @param id The unique identifier for the action type
     * @param tags The set of tags to associate with the action type
     * @param pluginId The ID of the plugin that owns this action type, or null if none
     * @return A configured ActionType instance
     */
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

    /**
     * Creates a test property merge strategy type with specified configuration.
     * Initializes a property merge strategy type with the provided ID and plugin ID.
     *
     * @param id The unique identifier for the merge strategy type
     * @param pluginId The ID of the plugin that owns this strategy type, or null if none
     * @return A configured PropertyMergeStrategyType instance
     */
    private PropertyMergeStrategyType createTestPropertyMergeStrategyType(String id, Long pluginId) {
        PropertyMergeStrategyType type = new PropertyMergeStrategyType();
        if (pluginId != null) {
            type.setPluginId(pluginId);
        }
        type.setId(id);
        return type;
    }

    /**
     * Registers a plugin type with the definitions service.
     * Sets up the bundle context if needed and adds the type to plugin tracking.
     *
     * @param type The plugin type to register (ConditionType, ActionType, ValueType, or PropertyMergeStrategyType)
     * @param bundleId The ID of the bundle that owns this plugin type
     */
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

    /**
     * Safely closes an input stream, logging any errors.
     * Helper method to ensure streams are properly closed in tests.
     *
     * @param stream The input stream to close
     */
    private void closeQuietly(InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                LOGGER.error("Error closing stream", e);
            }
        }
    }

    /**
     * Helper class for managing concurrent test execution.
     * Provides utilities for synchronizing threads and tracking test failures.
     */
    private static class ConcurrentTestHelper {
        private final CyclicBarrier barrier;
        private final CountDownLatch endLatch;
        private final AtomicBoolean failed;
        private final List<Thread> threads;

        /**
         * Creates a new concurrent test helper.
         * Initializes synchronization primitives for the specified number of threads.
         *
         * @param threadCount The number of threads that will participate in the test
         */
        public ConcurrentTestHelper(int threadCount) {
            this.barrier = new CyclicBarrier(threadCount);
            this.endLatch = new CountDownLatch(threadCount);
            this.failed = new AtomicBoolean(false);
            this.threads = Collections.synchronizedList(new ArrayList<>());
        }

        /**
         * Executes test threads and verifies their completion.
         * Waits for all threads to complete and checks for failures.
         *
         * @param testName The name of the test for error reporting
         * @param timeoutSeconds Maximum time to wait for thread completion
         * @throws InterruptedException if the wait is interrupted
         */
        public void executeAndVerify(String testName, int timeoutSeconds) throws InterruptedException {
            try {
                assertTrue(endLatch.await(timeoutSeconds, TimeUnit.SECONDS),
                    testName + " timed out waiting for threads");
                assertFalse(failed.get(), testName + " had thread failures");
            } finally {
                cleanupThreads();
            }
        }

        /**
         * Starts a new test thread with the specified task.
         * The thread will wait at the barrier before executing the task.
         *
         * @param name The name of the thread for identification
         * @param task The task to execute in the thread
         */
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

        /**
         * Cleans up any remaining test threads.
         * Interrupts and waits for threads to complete.
         */
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
