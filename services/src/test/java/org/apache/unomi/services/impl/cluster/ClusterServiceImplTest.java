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
package org.apache.unomi.services.impl.cluster;

import org.apache.unomi.api.ClusterNode;
import org.apache.unomi.api.services.ConditionValidationService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.impl.ExecutionContextManagerImpl;
import org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl;
import org.apache.unomi.services.impl.KarafSecurityService;
import org.apache.unomi.services.impl.TestConditionEvaluators;
import org.apache.unomi.services.impl.TestTenantService;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl;
import org.apache.unomi.services.impl.tenants.AuditServiceImpl;
import org.apache.unomi.tracing.api.TracerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.osgi.framework.BundleContext;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ClusterServiceImplTest {

    private ClusterServiceImpl clusterService;
    private TestTenantService tenantService;
    private PersistenceService persistenceService;
    private ExecutionContextManagerImpl executionContextManager;
    private KarafSecurityService securityService;
    private SchedulerServiceImpl schedulerService;

    @Mock
    private BundleContext bundleContext;

    // Add mock for BundleWatcher
    @Mock
    private org.apache.unomi.lifecycle.BundleWatcher bundleWatcher;

    private static final String TEST_NODE_ID = "test-node-1";
    private static final String PUBLIC_ADDRESS = "http://localhost:8181";
    private static final String INTERNAL_ADDRESS = "https://localhost:9443";
    private static final long NODE_STATISTICS_UPDATE_FREQUENCY = 10000;

    @BeforeEach
    public void setUp() {
        // Initialize tenant service
        tenantService = new TestTenantService();

        // Create tenants using TestHelper
        TestHelper.setupCommonTestData(tenantService);

        // Set up condition evaluator dispatcher
        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();

        // Set up bundle context using TestHelper
        bundleContext = TestHelper.createMockBundleContext();

        // Set up security service and context manager
        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);

        // Set up persistence service
        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);

        // Create cluster service using TestHelper
        clusterService = TestHelper.createClusterService(persistenceService, TEST_NODE_ID, PUBLIC_ADDRESS, INTERNAL_ADDRESS, bundleContext);
        
        // Configure cluster service (additional configurations not covered by helper method)
        clusterService.setNodeStatisticsUpdateFrequency(NODE_STATISTICS_UPDATE_FREQUENCY);
        
        // Create scheduler service using TestHelper
        schedulerService = TestHelper.createSchedulerService(
            "cluster-scheduler-node",
            persistenceService, executionContextManager, bundleContext, clusterService, -1, true, true);

        // Set scheduler in cluster service - this would normally be done by OSGi but we need to do it manually in tests
        clusterService.setSchedulerService(schedulerService);
        
        // Explicitly initialize scheduled tasks to handle the circular dependency properly
        clusterService.initializeScheduledTasks();
    }

    @Test
    public void testInitRegistersNodeInPersistence() {
        // Setup mock BundleWatcher to return a ServerInfo
        org.apache.unomi.api.ServerInfo mockServerInfo = new org.apache.unomi.api.ServerInfo();
        mockServerInfo.setServerIdentifier("test-server");
        mockServerInfo.setServerVersion("1.0.0");
        mockServerInfo.setServerBuildNumber("123");
        mockServerInfo.setServerBuildDate(new Date());
        mockServerInfo.setServerTimestamp("20250314120000");
        mockServerInfo.setServerScmBranch("main");
        
        when(bundleWatcher.getServerInfos()).thenReturn(java.util.Collections.singletonList(mockServerInfo));
        
        // Set the BundleWatcher in the ClusterService
        clusterService.setBundleWatcher(bundleWatcher);
        
        // Execute
        clusterService.init();

        // Verify node was saved in persistence
        ClusterNode savedNode = persistenceService.load(TEST_NODE_ID, ClusterNode.class);
        assertNotNull(savedNode);
        assertEquals(TEST_NODE_ID, savedNode.getItemId());
        assertEquals(PUBLIC_ADDRESS, savedNode.getPublicHostAddress());
        assertEquals(INTERNAL_ADDRESS, savedNode.getInternalHostAddress());
        assertNotNull(savedNode.getStartTime());
        assertNotNull(savedNode.getLastHeartbeat());
        assertNotNull(savedNode.getCpuLoad());
        assertNotNull(savedNode.getUptime());
        assertNotNull(savedNode.getLoadAverage());
        
        // Verify ServerInfo was set correctly
        assertNotNull(savedNode.getServerInfo(), "ServerInfo should be set from BundleWatcher");
        assertEquals(mockServerInfo.getServerIdentifier(), savedNode.getServerInfo().getServerIdentifier());
        assertEquals(mockServerInfo.getServerVersion(), savedNode.getServerInfo().getServerVersion());
        assertEquals(mockServerInfo.getServerBuildNumber(), savedNode.getServerInfo().getServerBuildNumber());
        assertEquals(mockServerInfo.getServerBuildDate(), savedNode.getServerInfo().getServerBuildDate());
        assertEquals(mockServerInfo.getServerTimestamp(), savedNode.getServerInfo().getServerTimestamp());
        assertEquals(mockServerInfo.getServerScmBranch(), savedNode.getServerInfo().getServerScmBranch());
    }

    // Add a new test to verify behavior when BundleWatcher is not available
    @Test
    public void testInitRegistersNodeInPersistenceWithoutBundleWatcher() {
        // Execute without setting a BundleWatcher
        clusterService.init();

        // Verify node was saved in persistence
        ClusterNode savedNode = persistenceService.load(TEST_NODE_ID, ClusterNode.class);
        assertNotNull(savedNode);
        assertEquals(TEST_NODE_ID, savedNode.getItemId());
        
        // Server info should be null since we don't have a BundleWatcher set
        assertNull(savedNode.getServerInfo(), "ServerInfo should be null when BundleWatcher is not available");
    }

    @Test
    public void testInitSchedulesStatisticsUpdateTask() {
        // We need to use a mock scheduler service for this test to verify the task creation
        SchedulerServiceImpl mockSchedulerService = mock(SchedulerServiceImpl.class);
        clusterService.setSchedulerService(mockSchedulerService);

        // Execute
        clusterService.init();

        // Verify
        verify(mockSchedulerService).createRecurringTask(
            eq("clusterNodeStatisticsUpdate"),
            eq(NODE_STATISTICS_UPDATE_FREQUENCY),
            eq(TimeUnit.MILLISECONDS),
            any(TimerTask.class),
            eq(false)
        );
    }

    @Test
    public void testInitSchedulesStaleNodesCleanupTask() {
        // We need to use a mock scheduler service for this test to verify the task creation
        SchedulerServiceImpl mockSchedulerService = mock(SchedulerServiceImpl.class);
        clusterService.setSchedulerService(mockSchedulerService);

        // Execute
        clusterService.init();

        // Verify
        verify(mockSchedulerService).createRecurringTask(
            eq("clusterStaleNodesCleanup"),
            eq(60000L),
            eq(TimeUnit.MILLISECONDS),
            any(TimerTask.class),
            eq(false)
        );
    }

    @Test
    public void testInitWithoutNodeIdThrowsException() {
        // Setup
        ClusterServiceImpl serviceWithoutNodeId = new ClusterServiceImpl();
        serviceWithoutNodeId.setPersistenceService(persistenceService);
        serviceWithoutNodeId.setPublicAddress(PUBLIC_ADDRESS);
        serviceWithoutNodeId.setInternalAddress(INTERNAL_ADDRESS);
        serviceWithoutNodeId.setSchedulerService(schedulerService);

        // Execute and verify
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            serviceWithoutNodeId::init);
        assertTrue(exception.getMessage().contains("nodeId is not set"));
    }

    @Test
    public void testDestroyRemovesNodeFromPersistence() {
        // Setup - first initialize to create the node
        clusterService.init();

        // Verify node exists
        assertNotNull(persistenceService.load(TEST_NODE_ID, ClusterNode.class));

        // Execute
        clusterService.destroy();

        // Verify node was removed
        assertNull(persistenceService.load(TEST_NODE_ID, ClusterNode.class));
    }

    @Test
    public void testGetClusterNodesReturnsAllNodes() {
        // Setup - create multiple nodes
        ClusterNode node1 = new ClusterNode();
        node1.setItemId(TEST_NODE_ID);
        node1.setPublicHostAddress(PUBLIC_ADDRESS);
        node1.setInternalHostAddress(INTERNAL_ADDRESS);
        node1.setStartTime(System.currentTimeMillis());
        node1.setLastHeartbeat(System.currentTimeMillis());
        persistenceService.save(node1);

        ClusterNode node2 = new ClusterNode();
        node2.setItemId("test-node-2");
        node2.setPublicHostAddress("http://localhost:8182");
        node2.setInternalHostAddress("https://localhost:9444");
        node2.setStartTime(System.currentTimeMillis());
        node2.setLastHeartbeat(System.currentTimeMillis());
        persistenceService.save(node2);

        // Execute
        List<ClusterNode> result = clusterService.getClusterNodes();

        // Verify
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(node -> node.getItemId().equals(TEST_NODE_ID)));
        assertTrue(result.stream().anyMatch(node -> node.getItemId().equals("test-node-2")));
    }

    @Test
    public void testUpdateSystemStats() {
        // Setup - initialize the service to create the node
        clusterService.init();

        // Get the initial node state
        ClusterNode initialNode = persistenceService.load(TEST_NODE_ID, ClusterNode.class);
        long initialHeartbeat = initialNode.getLastHeartbeat();

        // Wait a bit to ensure timestamps will be different
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // Ignore
        }

        // Simulate the statistics update task running
        // We need to access the private method via reflection
        try {
            java.lang.reflect.Method updateStatsMethod = ClusterServiceImpl.class.getDeclaredMethod("updateSystemStats");
            updateStatsMethod.setAccessible(true);
            updateStatsMethod.invoke(clusterService);
        } catch (Exception e) {
            fail("Failed to invoke updateSystemStats method: " + e.getMessage());
        }

        // Verify node was updated
        ClusterNode updatedNode = persistenceService.load(TEST_NODE_ID, ClusterNode.class);
        assertNotNull(updatedNode);
        assertTrue(updatedNode.getLastHeartbeat() > initialHeartbeat,
            "Heartbeat should be updated: initial=" + initialHeartbeat + ", updated=" + updatedNode.getLastHeartbeat());

        // Verify node statistics are updated in memory
        Map<String, Map<String, Serializable>> nodeStats = clusterService.getNodeSystemStatistics();
        assertNotNull(nodeStats);
        assertTrue(nodeStats.containsKey(TEST_NODE_ID));

        Map<String, Serializable> stats = nodeStats.get(TEST_NODE_ID);
        assertNotNull(stats.get("systemCpuLoad"));
        assertNotNull(stats.get("uptime"));
        assertNotNull(stats.get("systemLoadAverage"));
    }

    @Test
    public void testCleanupStaleNodes() {
        // Setup - create a stale node
        long cutoffTime = System.currentTimeMillis() - (NODE_STATISTICS_UPDATE_FREQUENCY * 3);

        ClusterNode staleNode = new ClusterNode();
        staleNode.setItemId("stale-node");
        staleNode.setPublicHostAddress("http://stale:8181");
        staleNode.setInternalHostAddress("https://stale:9443");
        staleNode.setStartTime(cutoffTime - 60000);
        staleNode.setLastHeartbeat(cutoffTime - 10000); // Older than cutoff
        persistenceService.save(staleNode);

        // Create a fresh node
        ClusterNode freshNode = new ClusterNode();
        freshNode.setItemId("fresh-node");
        freshNode.setPublicHostAddress("http://fresh:8181");
        freshNode.setInternalHostAddress("https://fresh:9443");
        freshNode.setStartTime(System.currentTimeMillis() - 60000);
        freshNode.setLastHeartbeat(System.currentTimeMillis()); // Recent heartbeat
        persistenceService.save(freshNode);

        // Verify both nodes exist
        assertNotNull(persistenceService.load("stale-node", ClusterNode.class));
        assertNotNull(persistenceService.load("fresh-node", ClusterNode.class));

        // Simulate the cleanup task running
        // We need to access the private method via reflection
        try {
            java.lang.reflect.Method cleanupMethod = ClusterServiceImpl.class.getDeclaredMethod("cleanupStaleNodes");
            cleanupMethod.setAccessible(true);
            cleanupMethod.invoke(clusterService);
        } catch (Exception e) {
            fail("Failed to invoke cleanupStaleNodes method: " + e.getMessage());
        }

        // Verify stale node was removed but fresh node remains
        assertNull(persistenceService.load("stale-node", ClusterNode.class), "Stale node should be removed");
        assertNotNull(persistenceService.load("fresh-node", ClusterNode.class), "Fresh node should remain");
    }

    @Test
    public void testPurgeByDateDelegatesToPersistenceService() {
        // Setup - use a mock persistence service to verify the call
        PersistenceService mockPersistenceService = mock(PersistenceService.class);
        clusterService.setPersistenceService(mockPersistenceService);

        // Setup
        Date purgeDate = new Date();

        // Execute
        clusterService.purge(purgeDate);

        // Verify
        verify(mockPersistenceService).purge(purgeDate);
    }

    @Test
    public void testPurgeByScopeDelegatesToPersistenceService() {
        // Setup - use a mock persistence service to verify the call
        PersistenceService mockPersistenceService = mock(PersistenceService.class);
        clusterService.setPersistenceService(mockPersistenceService);

        // Setup
        String scope = "testScope";

        // Execute
        clusterService.purge(scope);

        // Verify
        verify(mockPersistenceService).purge(scope);
    }
}
