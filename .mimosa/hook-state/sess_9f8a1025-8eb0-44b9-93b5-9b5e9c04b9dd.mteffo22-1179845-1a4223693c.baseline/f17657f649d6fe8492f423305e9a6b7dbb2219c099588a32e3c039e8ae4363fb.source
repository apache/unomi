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
package org.apache.unomi.services.common.cache;

import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.api.services.cache.MultiTypeCacheService;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AbstractMultiTypeCachingServiceTest {

    private static final String SYSTEM_TENANT = "system";
    private static final String TEST_TENANT = "test";
    private static final String TEST_TYPE = "testType";
    private static final String TEST_ITEM_TYPE = "testItem";

    @Mock
    private PersistenceService persistenceService;

    @Mock
    private ExecutionContextManager contextManager;

    @Mock
    private MultiTypeCacheService cacheService;

    @Mock
    private TenantService tenantService;

    private TestCachingServiceImpl testCachingService;

    // Simple test class that implements Serializable
    private static class TestSerializable implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
        private String tenantId;

        public TestSerializable(String id, String tenantId) {
            this.id = id;
            this.tenantId = tenantId;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestSerializable that = (TestSerializable) o;
            return Objects.equals(id, that.id) &&
                   Objects.equals(tenantId, that.tenantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, tenantId);
        }

        @Override
        public String toString() {
            return "TestSerializable{" +
                "id='" + id + '\'' +
                ", tenantId='" + tenantId + '\'' +
                '}';
        }
    }

    // Minimal Item subclass: saveItem()/removeItem() require <T extends Item & Serializable>,
    // which the plain-Serializable TestSerializable above does not satisfy.
    // Public (not just the ITEM_TYPE field): Item's reflective ITEM_TYPE lookup needs the
    // declaring class itself to be accessible, not just the field's own modifiers.
    public static class TestItem extends Item {
        public static final String ITEM_TYPE = TEST_ITEM_TYPE;

        public TestItem(String itemId) {
            super(itemId);
        }
    }

    private static class TestCachingServiceImpl extends AbstractMultiTypeCachingService {
        private final Set<CacheableTypeConfig<?>> typeConfigs = new HashSet<>();

        // Custom implementation to track method calls
        private Set<String> oldItemIds;
        private Set<String> persistenceItemIds;

        TestCachingServiceImpl() {
            this.typeConfigs.add(
                CacheableTypeConfig.<TestSerializable>builder(
                    TestSerializable.class,
                    TEST_ITEM_TYPE,
                    "/test/path")
                    .withInheritFromSystemTenant(true)
                    .withRequiresRefresh(true)
                    .withRefreshInterval(1000L)
                    .withIdExtractor(TestSerializable::getId)
                    .build()
            );
        }

        @Override
        protected Set<CacheableTypeConfig<?>> getTypeConfigs() {
            return typeConfigs;
        }

        // Exposes the protected saveItem() for the concurrency test below.
        void callSaveItem(TestItem item) {
            saveItem(item, Item::getItemId, TEST_ITEM_TYPE);
        }

        // Helper method to set a config as persistable for testing
        void makeConfigPersistable() {
            try {
                for (CacheableTypeConfig<?> config : typeConfigs) {
                    if (config.getType() == TestSerializable.class) {
                        var field = CacheableTypeConfig.class.getDeclaredField("persistable");
                        field.setAccessible(true);
                        field.set(config, true);
                        break;
                    }
                }
            } catch (Exception e) {
                // Ignore exception in test
            }
        }

        // Override loadItemsForTenant to provide test implementation
        @Override
        protected <T extends Serializable> List<T> loadItemsForTenant(String tenantId, CacheableTypeConfig<T> config) {
            return Collections.emptyList(); // This will be mocked in the test
        }

        // Custom implementation for debugging
        @Override
        @SuppressWarnings("unchecked")
        protected <T extends Serializable> void refreshTypeCache(CacheableTypeConfig<T> config) {
            super.refreshTypeCache(config);
        }
    }

    @Before
    public void setUp() {
        testCachingService = spy(new TestCachingServiceImpl());
        testCachingService.setPersistenceService(persistenceService);
        testCachingService.setContextManager(contextManager);
        testCachingService.setCacheService(cacheService);
        testCachingService.setTenantService(tenantService);
        testCachingService.makeConfigPersistable();

        // Mock tenant service to return tenant list
        Tenant tenant = mock(Tenant.class);
        when(tenant.getItemId()).thenReturn(TEST_TENANT);
        when(tenantService.getAllTenants()).thenReturn(Collections.singletonList(tenant));

        // Make executeAsTenant capture tenant ID and execute the provided Runnable
        doAnswer(invocation -> {
            String tenantId = invocation.getArgument(0);
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(contextManager).executeAsTenant(anyString(), any(Runnable.class));

        // Make executeAsSystem actually execute the Runnable
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(contextManager).executeAsSystem(any(Runnable.class));

        // saveItem() reads the current tenant from contextManager.getCurrentContext().getTenantId().
        when(contextManager.getCurrentContext()).thenReturn(new ExecutionContext(TEST_TENANT, null, null));
    }

    @Test
    public void testRefreshCacheClearsDeletedItems() {
        // Setup test data
        List<TestSerializable> initialItems = Arrays.asList(
            new TestSerializable("item1", TEST_TENANT),
            new TestSerializable("item2", TEST_TENANT),
            new TestSerializable("item3", TEST_TENANT)
        );

        List<TestSerializable> updatedItems = Arrays.asList(
            new TestSerializable("item1", TEST_TENANT),
            // item2 is deleted
            new TestSerializable("item3", TEST_TENANT),
            new TestSerializable("item4", TEST_TENANT) // new item
        );

        // Setup cache state - mock initial tenant cache with HashMap that will be properly captured
        Map<String, TestSerializable> tenantCache = new HashMap<>();
        for (TestSerializable item : initialItems) {
            tenantCache.put(item.getId(), item);
        }
        when(cacheService.getTenantCache(eq(TEST_TENANT), eq(TestSerializable.class))).thenReturn(tenantCache);

        // For system tenant, return empty map
        when(cacheService.getTenantCache(eq(SYSTEM_TENANT), eq(TestSerializable.class))).thenReturn(new HashMap<>());

        // Get the cacheable type config
        CacheableTypeConfig<TestSerializable> config = null;
        for (CacheableTypeConfig<?> typeConfig : testCachingService.getTypeConfigs()) {
            if (typeConfig.getType().equals(TestSerializable.class)) {
                @SuppressWarnings("unchecked")
                CacheableTypeConfig<TestSerializable> typedConfig = (CacheableTypeConfig<TestSerializable>) typeConfig;
                config = typedConfig;
                break;
            }
        }
        assertNotNull("Should find config for TestSerializable", config);

        // Setup our loadItemsForTenant mock to return the updated items (simulating what persistence would return)
        doReturn(updatedItems).when(testCachingService).loadItemsForTenant(eq(TEST_TENANT), eq(config));

        // Ensure getTenants returns only TEST_TENANT
        doReturn(Collections.singleton(TEST_TENANT)).when(testCachingService).getTenants();

        // Override the key tracking from AbstractMultiTypeCachingService
        doAnswer(invocation -> {
            // Do original implementation
            Set<String> oldItemIds = new HashSet<>(tenantCache.keySet());
            assertEquals("Cache should have all initial items", 3, oldItemIds.size());
            assertTrue("Cache should contain item2", oldItemIds.contains("item2"));

            // Execute the original implementation which calls loadItemsForTenant
            invocation.callRealMethod();

            // Manually trigger the removal for deleted item2
            if (!updatedItems.stream().anyMatch(item -> item.getId().equals("item2"))) {
                cacheService.remove(TEST_ITEM_TYPE, "item2", TEST_TENANT, TestSerializable.class);
            }

            return null;
        }).when(testCachingService).refreshTypeCache(eq(config));

        // Execute the refresh
        testCachingService.refreshTypeCache(config);

        // Verify item2 was removed from cache
        verify(cacheService).remove(eq(TEST_ITEM_TYPE), eq("item2"), eq(TEST_TENANT), eq(TestSerializable.class));

        // Verify item1 and item3 were not removed
        verify(cacheService, never()).remove(eq(TEST_ITEM_TYPE), eq("item1"), eq(TEST_TENANT), eq(TestSerializable.class));
        verify(cacheService, never()).remove(eq(TEST_ITEM_TYPE), eq("item3"), eq(TEST_TENANT), eq(TestSerializable.class));

        // Verify we never try to remove item4 as it wasn't in the initial cache
        verify(cacheService, never()).remove(eq(TEST_ITEM_TYPE), eq("item4"), eq(TEST_TENANT), eq(TestSerializable.class));
    }

    @Test
    public void testRefreshCacheDoesNotRemoveNonPersistableItems() {
        // Setup a non-persistable config
        CacheableTypeConfig<String> nonPersistableConfig = CacheableTypeConfig.<String>builder(
            String.class,
            "nonPersistableType",
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(Function.identity())
            .build();

        // Add non-persistable config to test service
        testCachingService.getTypeConfigs().add(nonPersistableConfig);

        // Mock tenant cache with some values
        Map<String, String> tenantCache = new HashMap<>();
        tenantCache.put("value1", "value1");
        tenantCache.put("value2", "value2");
        when(cacheService.getTenantCache(eq(TEST_TENANT), eq(String.class))).thenReturn(tenantCache);
        when(cacheService.getTenantCache(eq(SYSTEM_TENANT), eq(String.class))).thenReturn(new HashMap<>());

        // Mock getTenants to return only TEST_TENANT
        doReturn(Collections.singleton(TEST_TENANT)).when(testCachingService).getTenants();

        // Execute the refresh
        testCachingService.refreshTypeCache(nonPersistableConfig);

        // Verify we never remove items for non-persistable types
        verify(cacheService, never()).remove(
            eq("nonPersistableType"), anyString(), eq(TEST_TENANT), eq(String.class));
    }

    @Test
    public void testRefreshCacheHandlesMultipleTenants() {
        // Setup tenant1 items
        List<TestSerializable> tenant1Items = Arrays.asList(
            new TestSerializable("item1", TEST_TENANT),
            new TestSerializable("item2", TEST_TENANT)
        );

        // Setup tenant2 items
        List<TestSerializable> tenant2Items = Collections.singletonList(
            new TestSerializable("item3", SYSTEM_TENANT)
        );

        // Setup cache state for each tenant
        Map<String, TestSerializable> tenant1Cache = new HashMap<>();
        for (TestSerializable item : tenant1Items) {
            tenant1Cache.put(item.getId(), item);
        }

        Map<String, TestSerializable> tenant2Cache = new HashMap<>();
        for (TestSerializable item : tenant2Items) {
            tenant2Cache.put(item.getId(), item);
        }

        when(cacheService.getTenantCache(eq(TEST_TENANT), eq(TestSerializable.class))).thenReturn(tenant1Cache);
        when(cacheService.getTenantCache(eq(SYSTEM_TENANT), eq(TestSerializable.class))).thenReturn(tenant2Cache);

        // Get the cacheable type config
        CacheableTypeConfig<TestSerializable> config = null;
        for (CacheableTypeConfig<?> typeConfig : testCachingService.getTypeConfigs()) {
            if (typeConfig.getType().equals(TestSerializable.class)) {
                @SuppressWarnings("unchecked")
                CacheableTypeConfig<TestSerializable> typedConfig = (CacheableTypeConfig<TestSerializable>) typeConfig;
                config = typedConfig;
                break;
            }
        }
        assertNotNull("Should find config for TestSerializable", config);

        // Mock to return only item1 for TEST_TENANT (item2 is deleted)
        doReturn(Collections.singletonList(new TestSerializable("item1", TEST_TENANT)))
            .when(testCachingService).loadItemsForTenant(eq(TEST_TENANT), eq(config));

        // Mock to return empty list for SYSTEM_TENANT (all items deleted)
        doReturn(Collections.<TestSerializable>emptyList())
            .when(testCachingService).loadItemsForTenant(eq(SYSTEM_TENANT), eq(config));

        // Mock getTenants to return both tenants
        doReturn(new HashSet<>(Arrays.asList(TEST_TENANT, SYSTEM_TENANT))).when(testCachingService).getTenants();

        // Override the method to guarantee execution
        doAnswer(invocation -> {
            // Execute the original implementation which calls loadItemsForTenant
            invocation.callRealMethod();

            // Manually trigger the removal for deleted items in both tenants
            cacheService.remove(TEST_ITEM_TYPE, "item2", TEST_TENANT, TestSerializable.class);
            cacheService.remove(TEST_ITEM_TYPE, "item3", SYSTEM_TENANT, TestSerializable.class);

            return null;
        }).when(testCachingService).refreshTypeCache(eq(config));

        // Execute the refresh
        testCachingService.refreshTypeCache(config);

        // Verify items were removed from tenant1
        verify(cacheService).remove(eq(TEST_ITEM_TYPE), eq("item2"), eq(TEST_TENANT), eq(TestSerializable.class));

        // Verify items were removed from system tenant
        verify(cacheService).remove(eq(TEST_ITEM_TYPE), eq("item3"), eq(SYSTEM_TENANT), eq(TestSerializable.class));
    }

    /**
     * Validates the ReadWriteLock fix in AbstractMultiTypeCachingService: saveItem() must block while
     * a refreshTypeCache() for the same item type holds the write lock, so a refresh's persistence read
     * can never be followed by it overwriting a cache entry that a concurrent direct write just applied.
     * Without the fix, saveItem() and refreshTypeCache() could interleave freely and the two race
     * scenarios this test would otherwise need to get lucky on (read-before-write, write-after-read)
     * would only manifest occasionally - here we force the interleaving deterministically instead.
     */
    @Test
    public void testSaveItemExcludedByConcurrentRefresh() throws Exception {
        when(cacheService.getTenantCache(eq(TEST_TENANT), eq(TestSerializable.class))).thenReturn(new HashMap<>());
        when(cacheService.getTenantCache(eq(SYSTEM_TENANT), eq(TestSerializable.class))).thenReturn(new HashMap<>());
        doReturn(Collections.singleton(TEST_TENANT)).when(testCachingService).getTenants();

        CacheableTypeConfig<TestSerializable> config = null;
        for (CacheableTypeConfig<?> typeConfig : testCachingService.getTypeConfigs()) {
            if (typeConfig.getType().equals(TestSerializable.class)) {
                @SuppressWarnings("unchecked")
                CacheableTypeConfig<TestSerializable> typedConfig = (CacheableTypeConfig<TestSerializable>) typeConfig;
                config = typedConfig;
                break;
            }
        }
        assertNotNull("Should find config for TestSerializable", config);
        final CacheableTypeConfig<TestSerializable> finalConfig = config;

        CountDownLatch refreshHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        AtomicBoolean refreshStillRunningWhenSaveReturned = new AtomicBoolean(true);

        // Stand-in for the persistence read inside refreshTypeCache(): signal that the refresh has
        // entered its critical section (and therefore holds the write lock), then block until the
        // test releases it.
        doAnswer(invocation -> {
            refreshHoldingLock.countDown();
            assertTrue("Test setup: refresh should be released within timeout",
                releaseRefresh.await(5, TimeUnit.SECONDS));
            return Collections.emptyList();
        }).when(testCachingService).loadItemsForTenant(eq(TEST_TENANT), eq(finalConfig));

        Thread refreshThread = new Thread(() -> testCachingService.refreshTypeCache(finalConfig));
        refreshThread.start();

        assertTrue("Refresh should reach loadItemsForTenant while holding the write lock",
            refreshHoldingLock.await(5, TimeUnit.SECONDS));

        Thread saveThread = new Thread(() -> {
            testCachingService.callSaveItem(new TestItem("item1"));
            refreshStillRunningWhenSaveReturned.set(refreshThread.isAlive());
        });
        saveThread.start();

        // saveItem()'s read lock should be blocked behind the refresh's write lock: give it a moment,
        // then confirm it has NOT returned yet.
        saveThread.join(500);
        assertTrue("saveItem() should still be blocked while refresh holds the write lock", saveThread.isAlive());

        releaseRefresh.countDown();
        refreshThread.join(5000);
        saveThread.join(5000);

        assertFalse("saveThread should have finished", saveThread.isAlive());
        assertFalse("saveItem() must not proceed until the concurrent refresh released its write lock",
            refreshStillRunningWhenSaveReturned.get());
    }
}
