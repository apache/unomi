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
package org.apache.unomi.services.impl.cache;

import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.api.services.cache.MultiTypeCacheService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class MultiTypeCacheServiceImplTest {

    private static final String SYSTEM_TENANT = "system";
    private static final String TEST_TENANT = "test-tenant";
    private static final String TEST_TYPE = "test-type";
    private static final String TEST_ID = "test-id";

    private MultiTypeCacheServiceImpl cacheService;

    // Test plugin type implementation
    private static class TestSerializable implements Serializable {
        private String id;

        public TestSerializable(String id) {
            this.id = id;
        }


        public String getId() {
            return id;
        }
    }

    @Before
    public void setUp() {
        cacheService = new MultiTypeCacheServiceImpl();
    }

    @Test
    public void testRegisterType() {
        // Create test configuration
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();

        // Register type
        cacheService.registerType(config);

        // Put a value and verify it's cached
        TestSerializable value = new TestSerializable(TEST_ID);
        cacheService.put(TEST_TYPE, TEST_ID, TEST_TENANT, value);

        // Verify value can be retrieved
        TestSerializable retrieved = cacheService.getWithInheritance(TEST_ID, TEST_TENANT, TestSerializable.class);
        assertNotNull("Retrieved value should not be null", retrieved);
        assertEquals("Retrieved value should match original", value.getId(), retrieved.getId());
    }

    @Test
    public void testInheritanceFromSystemTenant() {
        // Create test configuration with inheritance enabled
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config);

        // Put a value in system tenant
        TestSerializable systemValue = new TestSerializable("system-value");
        cacheService.put(TEST_TYPE, TEST_ID, SYSTEM_TENANT, systemValue);

        // Verify value can be retrieved from another tenant
        TestSerializable retrieved = cacheService.getWithInheritance(TEST_ID, TEST_TENANT, TestSerializable.class);
        assertNotNull("Retrieved value should not be null", retrieved);
        assertEquals("Retrieved value should match system tenant value", systemValue.getId(), retrieved.getId());

        // Put a tenant-specific value
        TestSerializable tenantValue = new TestSerializable("tenant-value");
        cacheService.put(TEST_TYPE, TEST_ID, TEST_TENANT, tenantValue);

        // Verify tenant-specific value overrides system tenant
        retrieved = cacheService.getWithInheritance(TEST_ID, TEST_TENANT, TestSerializable.class);
        assertNotNull("Retrieved value should not be null", retrieved);
        assertEquals("Retrieved value should match tenant value", tenantValue.getId(), retrieved.getId());
    }

    @Test
    public void testGetValuesByPredicateWithInheritance() {
        // Create test configuration with inheritance enabled
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config);

        // Put values in system tenant
        TestSerializable systemValue1 = new TestSerializable("system1");
        TestSerializable systemValue2 = new TestSerializable("system2");
        cacheService.put(TEST_TYPE, "system1", SYSTEM_TENANT, systemValue1);
        cacheService.put(TEST_TYPE, "system2", SYSTEM_TENANT, systemValue2);

        // Put values in test tenant
        TestSerializable tenantValue1 = new TestSerializable("tenant1");
        TestSerializable tenantValue2 = new TestSerializable("tenant2");
        cacheService.put(TEST_TYPE, "tenant1", TEST_TENANT, tenantValue1);
        cacheService.put(TEST_TYPE, "tenant2", TEST_TENANT, tenantValue2);

        // Get values by predicate
        Set<TestSerializable> values = cacheService.getValuesByPredicateWithInheritance(
            TEST_TENANT,
            TestSerializable.class,
            value -> value.getId().startsWith("system")
        );

        assertEquals("Should find 2 values matching predicate", 2, values.size());
        assertTrue("Should contain system values", values.contains(systemValue1));
        assertTrue("Should contain system values", values.contains(systemValue2));
    }

    @Test
    public void testStatistics() {
        // Create test configuration
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config);

        // Put a value
        TestSerializable value = new TestSerializable(TEST_ID);
        cacheService.put(TEST_TYPE, TEST_ID, TEST_TENANT, value);

        // Get the value (hit)
        cacheService.getWithInheritance(TEST_ID, TEST_TENANT, TestSerializable.class);

        // Try to get non-existent value (miss)
        cacheService.getWithInheritance("non-existent", TEST_TENANT, TestSerializable.class);

        // Verify statistics
        MultiTypeCacheService.CacheStatistics stats = cacheService.getStatistics();
        MultiTypeCacheService.CacheStatistics.TypeStatistics typeStats = stats.getAllStats().get(TEST_TYPE);

        assertNotNull("Type statistics should exist", typeStats);
        assertEquals("Should have 1 hit", 1, typeStats.getHits());
        assertEquals("Should have 1 miss", 1, typeStats.getMisses());
        assertEquals("Should have 1 update", 1, typeStats.getUpdates());

        // Reset statistics
        stats.reset();
        assertTrue("Statistics should be empty after reset", stats.getAllStats().isEmpty());
    }

    @Test
    public void testClearTenantCache() {
        // Create test configuration
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config);

        // Put values in different tenants
        TestSerializable value1 = new TestSerializable("value1");
        TestSerializable value2 = new TestSerializable("value2");
        cacheService.put(TEST_TYPE, "value1", TEST_TENANT, value1);
        cacheService.put(TEST_TYPE, "value2", SYSTEM_TENANT, value2);

        // Clear test tenant cache
        cacheService.clear(TEST_TENANT);

        // Verify test tenant value is gone but system tenant value remains
        assertNull("Test tenant value should be cleared",
            cacheService.getWithInheritance("value1", TEST_TENANT, TestSerializable.class));
        assertNotNull("System tenant value should remain",
            cacheService.getWithInheritance("value2", SYSTEM_TENANT, TestSerializable.class));
    }

    @Test
    public void testRemoveValue() {
        // Create test configuration
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config);

        // Put a value
        TestSerializable value = new TestSerializable(TEST_ID);
        cacheService.put(TEST_TYPE, TEST_ID, TEST_TENANT, value);

        // Remove the value
        cacheService.remove(TEST_TYPE, TEST_ID, TEST_TENANT, TestSerializable.class);

        // Verify value is removed
        assertNull("Value should be removed",
            cacheService.getWithInheritance(TEST_ID, TEST_TENANT, TestSerializable.class));
    }

    @Test
    public void testNullParameters() {
        // Create test configuration
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config);

        // Test null parameters for put
        cacheService.put(null, TEST_ID, TEST_TENANT, new TestSerializable(TEST_ID));
        cacheService.put(TEST_TYPE, null, TEST_TENANT, new TestSerializable(TEST_ID));
        cacheService.put(TEST_TYPE, TEST_ID, null, new TestSerializable(TEST_ID));
        cacheService.put(TEST_TYPE, TEST_ID, TEST_TENANT, null);

        // Verify no values were cached
        assertNull("No value should be cached with null parameters",
            cacheService.getWithInheritance(TEST_ID, TEST_TENANT, TestSerializable.class));

        // Test null parameters for get
        assertNull("Get with null ID should return null",
            cacheService.getWithInheritance(null, TEST_TENANT, TestSerializable.class));
        assertNull("Get with null tenant should return null",
            cacheService.getWithInheritance(TEST_ID, null, TestSerializable.class));
        assertNull("Get with null type should return null",
            cacheService.getWithInheritance(TEST_ID, TEST_TENANT, null));
    }

    @Test
    public void testGetTenantCache() {
        // Create test configuration
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config);

        // Put multiple values
        TestSerializable value1 = new TestSerializable("value1");
        TestSerializable value2 = new TestSerializable("value2");
        cacheService.put(TEST_TYPE, "value1", TEST_TENANT, value1);
        cacheService.put(TEST_TYPE, "value2", TEST_TENANT, value2);

        // Get tenant cache
        Map<String, TestSerializable> tenantCache = cacheService.getTenantCache(TEST_TENANT, TestSerializable.class);
        assertNotNull("Tenant cache should not be null", tenantCache);
        assertEquals("Tenant cache should contain 2 values", 2, tenantCache.size());
        assertEquals("Value1 should match", value1.getId(), tenantCache.get("value1").getId());
        assertEquals("Value2 should match", value2.getId(), tenantCache.get("value2").getId());

        // Verify cache is unmodifiable
        try {
            tenantCache.put("value3", new TestSerializable("value3"));
            fail("Tenant cache should be unmodifiable");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    @Test
    public void testUnregisteredType() {
        // Try to get value for unregistered type
        assertNull("Get with unregistered type should return null",
            cacheService.getWithInheritance(TEST_ID, TEST_TENANT, TestSerializable.class));

        // Try to put value for unregistered type
        TestSerializable value = new TestSerializable(TEST_ID);
        cacheService.put(TEST_TYPE, TEST_ID, TEST_TENANT, value);

        // Verify value was not cached
        assertNull("Value should not be cached for unregistered type",
            cacheService.getWithInheritance(TEST_ID, TEST_TENANT, TestSerializable.class));
    }

    @Test
    public void testRefreshTypeCache() {
        // Create test configuration with refresh required
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config);

        // Put a value
        TestSerializable value = new TestSerializable(TEST_ID);
        cacheService.put(TEST_TYPE, TEST_ID, TEST_TENANT, value);

        // Refresh cache
        cacheService.refreshTypeCache(config);

        // Verify statistics
        MultiTypeCacheService.CacheStatistics.TypeStatistics typeStats =
            cacheService.getStatistics().getAllStats().get(TEST_TYPE);
        assertEquals("Should have no indexing errors", 0, typeStats.getIndexingErrors());
    }

    @Test
    public void testInvalidRefreshTypeCache() {
        // Create test configuration with refresh disabled
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(false)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();

        // Try to refresh with disabled config
        cacheService.refreshTypeCache(config);

        // Verify no statistics were created
        assertTrue("No statistics should be created",
            cacheService.getStatistics().getAllStats().isEmpty());
    }

    @Test
    public void testInheritanceDisabled() {
        // Create test configuration with inheritance disabled
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(false)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config);

        // Put a value in system tenant
        TestSerializable systemValue = new TestSerializable("system-value");
        cacheService.put(TEST_TYPE, TEST_ID, SYSTEM_TENANT, systemValue);

        // Verify value cannot be retrieved from another tenant when inheritance is disabled
        TestSerializable retrieved = cacheService.getWithInheritance(TEST_ID, TEST_TENANT, TestSerializable.class);
        assertNull("Should not inherit from system tenant when inheritance is disabled", retrieved);

        // Verify statistics
        MultiTypeCacheService.CacheStatistics.TypeStatistics typeStats =
            cacheService.getStatistics().getAllStats().get(TEST_TYPE);
        assertEquals("Should have 1 miss", 1, typeStats.getMisses());
        assertEquals("Should have no hits", 0, typeStats.getHits());
    }

    @Test
    public void testMultipleTypeConfigurations() {
        // First configuration
        CacheableTypeConfig<TestSerializable> config1 = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        
        // Second configuration with different type
        CacheableTypeConfig<TestSerializable> config2 = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            "other-type",
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config1);
        cacheService.registerType(config2);

        // Put values of different types
        TestSerializable value1 = new TestSerializable("value1");
        TestSerializable value2 = new TestSerializable("value2");
        cacheService.put("type1", "id1", TEST_TENANT, value1);
        cacheService.put("type2", "id2", TEST_TENANT, value2);

        // Verify values are cached separately
        Map<String, TestSerializable> cache1 = cacheService.getTenantCache(TEST_TENANT, TestSerializable.class);
        Map<String, TestSerializable> cache2 = cacheService.getTenantCache(TEST_TENANT, TestSerializable.class);

        assertNotNull("Cache for type1 should exist", cache1);
        assertNotNull("Cache for type2 should exist", cache2);
    }

    @Test
    public void testPredicateWithNullValues() {
        // Create test configuration
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config);

        // Put some values including null IDs
        TestSerializable value1 = new TestSerializable(null);  // null ID
        TestSerializable value2 = new TestSerializable("value2");
        cacheService.put(TEST_TYPE, "id1", TEST_TENANT, value1);
        cacheService.put(TEST_TYPE, "id2", TEST_TENANT, value2);

        // Get values with predicate that handles null
        Set<TestSerializable> values = cacheService.getValuesByPredicateWithInheritance(
            TEST_TENANT,
            TestSerializable.class,
            value -> value.getId() == null || value.getId().equals("value2")
        );

        assertEquals("Should find 2 values matching predicate", 2, values.size());
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        // Create test configuration
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config);

        // Create multiple threads to access cache concurrently
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                // Each thread puts and gets its own values
                TestSerializable value = new TestSerializable("value" + threadId);
                cacheService.put(TEST_TYPE, "id" + threadId, TEST_TENANT, value);
                cacheService.getWithInheritance("id" + threadId, TEST_TENANT, TestSerializable.class);
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify statistics
        MultiTypeCacheService.CacheStatistics.TypeStatistics typeStats =
            cacheService.getStatistics().getAllStats().get(TEST_TYPE);
        assertEquals("Should have correct number of updates", threadCount, typeStats.getUpdates());
        assertEquals("Should have correct number of hits", threadCount, typeStats.getHits());
    }

    @Test
    public void testOverrideTypeConfiguration() {
        // Original configuration
        CacheableTypeConfig<TestSerializable> config1 = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        
        // Override configuration
        CacheableTypeConfig<TestSerializable> config2 = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/other/path")
            .withInheritFromSystemTenant(false)
            .withRequiresRefresh(false)
            .withRefreshInterval(2000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config1);
        cacheService.registerType(config2);

        // Put a value in system tenant
        TestSerializable systemValue = new TestSerializable("system-value");
        cacheService.put(TEST_TYPE, TEST_ID, SYSTEM_TENANT, systemValue);

        // Verify inheritance is disabled as per new configuration
        TestSerializable retrieved = cacheService.getWithInheritance(TEST_ID, TEST_TENANT, TestSerializable.class);
        assertNull("Should not inherit from system tenant with new configuration", retrieved);
    }

    @Test
    public void testEmptyPredicateResults() {
        // Create test configuration
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config);

        // Put some values
        TestSerializable value1 = new TestSerializable("value1");
        TestSerializable value2 = new TestSerializable("value2");
        cacheService.put(TEST_TYPE, "id1", TEST_TENANT, value1);
        cacheService.put(TEST_TYPE, "id2", TEST_TENANT, value2);

        // Get values with predicate that matches nothing
        Set<TestSerializable> values = cacheService.getValuesByPredicateWithInheritance(
            TEST_TENANT,
            TestSerializable.class,
            value -> false
        );

        assertTrue("Should return empty set when no values match predicate", values.isEmpty());
    }

    @Test
    public void testStatisticsThreadSafety() throws InterruptedException {
        // Create test configuration
        CacheableTypeConfig<TestSerializable> config = CacheableTypeConfig.<TestSerializable>builder(
            TestSerializable.class,
            TEST_TYPE,
            "/test/path")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000L)
            .withIdExtractor(TestSerializable::getId)
            .build();
        cacheService.registerType(config);

        // Create threads to update statistics concurrently
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    TestSerializable value = new TestSerializable("value");
                    cacheService.put(TEST_TYPE, "id", TEST_TENANT, value);
                    cacheService.getWithInheritance("id", TEST_TENANT, TestSerializable.class);
                    cacheService.getWithInheritance("nonexistent", TEST_TENANT, TestSerializable.class);
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify statistics
        MultiTypeCacheService.CacheStatistics.TypeStatistics typeStats =
            cacheService.getStatistics().getAllStats().get(TEST_TYPE);
        assertEquals("Should have correct number of updates", threadCount * 100, typeStats.getUpdates());
        assertEquals("Should have correct number of hits", threadCount * 100, typeStats.getHits());
        assertEquals("Should have correct number of misses", threadCount * 100, typeStats.getMisses());
    }
}
