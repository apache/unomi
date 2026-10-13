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
import org.apache.unomi.api.ServerInfo;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.lifecycle.BundleWatcher;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.apache.unomi.services.common.security.KarafSecurityService;
import org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl;
import org.apache.unomi.services.impl.TestConditionEvaluators;
import org.apache.unomi.services.impl.TestTenantService;
import org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.osgi.framework.BundleContext;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private BundleWatcher bundleWatcher;

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

        // Set up persistence service — no refresh-delay simulation: cluster tests verify
        // cluster logic, not persistence refresh semantics (tested in InMemoryPersistenceServiceImplTest).
        persistenceService = new InMemoryPersistenceServiceImpl(
                executionContextManager, conditionEvaluatorDispatcher,
                System.getProperty("java.io.tmpdir"), false, false, false, false, 0L);

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

        // Note: scheduled tasks are intentionally NOT started here. clusterStaleNodesCleanup and
        // clusterNodeStatisticsUpdate both have initialDelay=0, so starting them eagerly in every
        // test's setUp() raced the test body on a real background thread pool and could delete
        // freshly-saved fixtures before assertions ran (flaky only under CI-like scheduling/timing).
        // Tests that actually exercise scheduled-task behavior call clusterService.init() themselves,
        // which starts them at the right time.
    }

    @Test
    public void testInitRegistersNodeInPersistence() {
        // Setup mock BundleWatcher to return a ServerInfo
        ServerInfo mockServerInfo = new ServerInfo();
        mockServerInfo.setServerIdentifier("test-server");
        mockServerInfo.setServerVersion("1.0.0");
        mockServerInfo.setServerBuildNumber("123");
        mockServerInfo.setServerBuildDate(new Date());
        mockServerInfo.setServerTimestamp("20250314120000");
        mockServerInfo.setServerScmBranch("main");

        when(bundleWatcher.getServerInfos()).thenReturn(Collections.singletonList(mockServerInfo));

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
    public void testInitRegistersNodeNameFromBundleWatcher() {
        when(bundleWatcher.getNodeName()).thenReturn("web-1");
        clusterService.setBundleWatcher(bundleWatcher);

        clusterService.init();

        assertEquals("web-1", persistenceService.load(TEST_NODE_ID, ClusterNode.class).getNodeName());
    }

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
        SchedulerService mockSchedulerService = mock(SchedulerService.class);
        SchedulerService.TaskBuilder heartbeatBuilder = mock(SchedulerService.TaskBuilder.class, RETURNS_SELF);
        when(mockSchedulerService.newTask("clusterNodeStatisticsUpdate")).thenReturn(heartbeatBuilder);
        clusterService.setSchedulerService(mockSchedulerService);

        // Execute
        clusterService.init();

        // Verify: a local, recurring heartbeat that runs on every node, executor or not
        verify(heartbeatBuilder).withPeriod(NODE_STATISTICS_UPDATE_FREQUENCY, TimeUnit.MILLISECONDS);
        verify(heartbeatBuilder).nonPersistent();
        verify(heartbeatBuilder).runOnAllNodes();
        verify(heartbeatBuilder).schedule();
    }

    @Test
    public void testInitSchedulesStaleNodesCleanupTask() {
        // We need to use a mock scheduler service for this test to verify the task creation
        SchedulerService mockSchedulerService = mock(SchedulerService.class);
        when(mockSchedulerService.newTask(any())).thenReturn(mock(SchedulerService.TaskBuilder.class, RETURNS_SELF));
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

        // Verify node was removed - retry to handle race condition with scheduled tasks
        // The updateSystemStats scheduled task might re-register the node if it's running
        // concurrently with destroy(), so we retry until the node is actually removed
        ClusterNode node = TestHelper.retryUntil(
            () -> persistenceService.load(TEST_NODE_ID, ClusterNode.class),
            n -> n == null
        );

        assertNull(node, "Node should be removed from persistence after destroy(), nodeId=" + TEST_NODE_ID);
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

        // Refresh persistence to ensure nodes are available for querying (handles refresh delay)
        persistenceService.refresh();

        // Force a cache refresh: updateSystemStats() queries persistence and populates
        // cachedClusterNodes. The scheduled task runs every 10 s, so we invoke it directly.
        clusterService.updateSystemStats();

        List<ClusterNode> result = clusterService.getClusterNodes();

        // Verify
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(node -> node.getItemId().equals(TEST_NODE_ID)));
        assertTrue(result.stream().anyMatch(node -> node.getItemId().equals("test-node-2")));
    }

    @Test
    public void testUpdateSystemStats() {
        // Setup - initialize the service to create the node (this also records an initial heartbeat)
        clusterService.init();

        // Get the initial heartbeat timestamp recorded at init time
        ClusterNode initialNode = persistenceService.load(TEST_NODE_ID, ClusterNode.class);
        assertNotNull(initialNode, "Node should be registered after init");
        long initialHeartbeat = initialNode.getLastHeartbeat();

        // Wait for the scheduler's statistics update task to run and write a newer heartbeat.
        // The task runs on the configured nodeStatisticsUpdateFrequency; retryUntil polls until
        // the persisted heartbeat is strictly greater than the one captured at init.
        ClusterNode updatedNode = TestHelper.retryUntil(
            () -> persistenceService.load(TEST_NODE_ID, ClusterNode.class),
            node -> node != null && node.getLastHeartbeat() > initialHeartbeat
        );

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
        // Cancel the background cluster tasks to prevent a race: cleanupStaleNodes() has
        // initialDelay=0, so on a loaded CI machine the thread pool may defer its first
        // execution until after we save the stale node — at which point it would delete the
        // node before our precondition assertions run.
        clusterService.cancelScheduledTasks();

        // Setup - create a stale node
        long cutoffTime = System.currentTimeMillis() - (NODE_STATISTICS_UPDATE_FREQUENCY * 3);

        // Save nodes in system context to ensure proper tenant handling
        executionContextManager.executeAsSystem(() -> {
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
            return null;
        });

        // Verify both nodes exist — in-memory persistence with simulateRefreshDelay=false is
        // synchronous, so no retry loop is needed here.
        assertNotNull(persistenceService.load("stale-node", ClusterNode.class),
                "Stale node should exist before cleanup, nodeId=stale-node");
        assertNotNull(persistenceService.load("fresh-node", ClusterNode.class),
                "Fresh node should exist before cleanup, nodeId=fresh-node");

        // Trigger the cleanup synchronously. The scheduled task runs every 60 s which is
        // impractical in a unit test; calling the method directly exercises the same end-to-end
        // persistence behaviour (query by lastHeartbeat, remove stale, keep fresh).
        assertDoesNotThrow(() -> clusterService.cleanupStaleNodes(),
                "cleanupStaleNodes should run without throwing");

        // Verify stale node was removed but fresh node remains
        assertNull(persistenceService.load("stale-node", ClusterNode.class), "Stale node should be removed");
        assertNotNull(persistenceService.load("fresh-node", ClusterNode.class), "Fresh node should remain");
    }

    @Test
    public void testPurgeByDateDelegatesToPersistenceService() {
        // Setup: create items with different creation dates in real persistence
        executionContextManager.executeAsSystem(() -> {
            ClusterNode oldNode = new ClusterNode();
            oldNode.setItemId("old-node");
            oldNode.setPublicHostAddress("http://old:8181");
            oldNode.setInternalHostAddress("https://old:9443");
            oldNode.setCreationDate(new Date(System.currentTimeMillis() - 7L * 24 * 3600 * 1000)); // 7 days ago
            persistenceService.save(oldNode);

            ClusterNode recentNode = new ClusterNode();
            recentNode.setItemId("recent-node");
            recentNode.setPublicHostAddress("http://recent:8181");
            recentNode.setInternalHostAddress("https://recent:9443");
            recentNode.setCreationDate(new Date());
            persistenceService.save(recentNode);
        });

        // Purge and verify in system tenant — items were saved there; load/purge are tenant-scoped.
        executionContextManager.executeAsSystem(() -> {
            Date cutoff = new Date(System.currentTimeMillis() - 3L * 24 * 3600 * 1000); // 3 days ago
            clusterService.purge(cutoff);

            assertNull(persistenceService.load("old-node", ClusterNode.class));
            assertNotNull(persistenceService.load("recent-node", ClusterNode.class));
        });
    }

    @Test
    public void testPurgeByScopeDelegatesToPersistenceService() {
        // Setup: create two nodes with different scopes in real persistence
        executionContextManager.executeAsSystem(() -> {
            ClusterNode scopedNode = new ClusterNode();
            scopedNode.setItemId("scoped-node");
            scopedNode.setPublicHostAddress("http://scoped:8181");
            scopedNode.setInternalHostAddress("https://scoped:9443");
            scopedNode.setScope("testScope");
            persistenceService.save(scopedNode);

            ClusterNode otherNode = new ClusterNode();
            otherNode.setItemId("other-node");
            otherNode.setPublicHostAddress("http://other:8181");
            otherNode.setInternalHostAddress("https://other:9443");
            otherNode.setScope("otherScope");
            persistenceService.save(otherNode);
        });

        executionContextManager.executeAsSystem(() -> {
            clusterService.purge("testScope");

            assertNull(persistenceService.load("scoped-node", ClusterNode.class));
            assertNotNull(persistenceService.load("other-node", ClusterNode.class));
        });
    }

    private ClusterServiceImpl newNode(String nodeId, long heartbeatFrequency) {
        ClusterServiceImpl node = TestHelper.createClusterService(persistenceService, nodeId, PUBLIC_ADDRESS, INTERNAL_ADDRESS, bundleContext);
        node.setNodeStatisticsUpdateFrequency(heartbeatFrequency);
        return node;
    }

    private void saveNodeRecord(String nodeId, long startTime, long lastHeartbeat) {
        ClusterNode record = new ClusterNode();
        record.setItemId(nodeId);
        record.setStartTime(startTime);
        record.setLastHeartbeat(lastHeartbeat);
        persistenceService.save(record);
    }

    /** Runs {@code beat} every {@code period} ms while {@code body} executes. */
    private void whileBeating(Runnable beat, long period, Runnable body) throws InterruptedException {
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        heartbeat.scheduleAtFixedRate(beat, 0, period, TimeUnit.MILLISECONDS);
        try {
            body.run();
        } finally {
            heartbeat.shutdownNow();
            heartbeat.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(60)
    public void testInitFailsWhenNodeIdIsUsedByLiveNode() throws Exception {
        ClusterServiceImpl incumbent = newNode(TEST_NODE_ID, 300);
        incumbent.init();
        long incumbentStartTime = persistenceService.load(TEST_NODE_ID, ClusterNode.class).getStartTime();

        // The live node heartbeats at its real cadence, once per period
        whileBeating(incumbent::updateSystemStats, 300, () -> {
            ClusterServiceImpl duplicate = newNode(TEST_NODE_ID, 300);
            long start = System.nanoTime();
            IllegalStateException e = assertThrows(IllegalStateException.class, duplicate::init);
            assertTrue(e.getMessage().contains("already used by another live node"), e.getMessage());
            // It gave the other node six heartbeat periods to stop before giving up
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 1800, "should not fail on the first heartbeat seen");
        });

        assertEquals(incumbentStartTime, persistenceService.load(TEST_NODE_ID, ClusterNode.class).getStartTime());
    }

    @Test
    @Timeout(60)
    public void testInitWaitsForPreviousProcessToStopHeartbeating() throws Exception {
        // The previous process is still alive for a few heartbeats (slow shutdown, or hung then killed)
        ClusterServiceImpl previous = newNode(TEST_NODE_ID, 200);
        previous.init();
        long previousStartTime = persistenceService.load(TEST_NODE_ID, ClusterNode.class).getStartTime();

        AtomicInteger remainingBeats = new AtomicInteger(5);
        whileBeating(() -> {
            if (remainingBeats.getAndDecrement() > 0) {
                previous.updateSystemStats();
            }
        }, 20, () -> {
            newNode(TEST_NODE_ID, 200).init();
            assertTrue(remainingBeats.get() <= 0, "the previous process should have stopped by itself");
        });

        assertTrue(persistenceService.load(TEST_NODE_ID, ClusterNode.class).getStartTime() > previousStartTime);
    }

    @Test
    @Timeout(60)
    public void testInitTakesOverNodeIdLeftByCrashedNode() {
        // A crash leaves the record behind with a recent heartbeat that nobody updates anymore
        long crashedStartTime = System.currentTimeMillis() - 60000;
        saveNodeRecord(TEST_NODE_ID, crashedStartTime, System.currentTimeMillis());

        long start = System.nanoTime();
        newNode(TEST_NODE_ID, 300).init();

        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 600, "should watch the record for two periods first");
        assertTrue(persistenceService.load(TEST_NODE_ID, ClusterNode.class).getStartTime() > crashedStartTime);
    }

    @Test
    @Timeout(60)
    public void testInitIgnoresSchedulerBoundWhileWaiting() throws Exception {
        saveNodeRecord(TEST_NODE_ID, 1L, System.currentTimeMillis());
        ClusterServiceImpl restarted = newNode(TEST_NODE_ID, 200);

        // Binding the scheduler mid-check must not start this node's own heartbeat: it would keep the
        // predecessor's record moving and the node would reject itself.
        ScheduledExecutorService binder = Executors.newSingleThreadScheduledExecutor();
        binder.schedule(() -> restarted.setSchedulerService(schedulerService), 50, TimeUnit.MILLISECONDS);
        try {
            restarted.init();
        } finally {
            binder.shutdownNow();
        }

        assertTrue(persistenceService.load(TEST_NODE_ID, ClusterNode.class).getStartTime() > 1L);
    }

    @Test
    @Timeout(60)
    public void testInitStopsWaitingWhenRecordIsRemoved() throws Exception {
        // The previous process finishes its clean shutdown (or is cleaned up as stale) during the check
        saveNodeRecord(TEST_NODE_ID, 1L, System.currentTimeMillis());
        ScheduledExecutorService remover = Executors.newSingleThreadScheduledExecutor();
        remover.schedule(() -> persistenceService.remove(TEST_NODE_ID, ClusterNode.class), 100, TimeUnit.MILLISECONDS);
        try {
            long start = System.nanoTime();
            newNode(TEST_NODE_ID, 2000).init();
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 4000, "should not wait out the quiet period");
        } finally {
            remover.shutdownNow();
        }
    }

    @Test
    public void testInitFailsWhenInterruptedWhileWaiting() {
        saveNodeRecord(TEST_NODE_ID, 1L, System.currentTimeMillis());
        ClusterServiceImpl node = newNode(TEST_NODE_ID, 60000);

        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, node::init);
        } finally {
            assertTrue(Thread.interrupted(), "interrupt flag should be preserved");
        }
        assertEquals(1L, persistenceService.load(TEST_NODE_ID, ClusterNode.class).getStartTime());
    }

    @Test
    public void testInitFailsWhenNodeCannotBeRegistered() {
        // An unavailable search engine: reads return nothing and writes are refused
        PersistenceService failingPersistence = mock(PersistenceService.class);
        ClusterServiceImpl node = TestHelper.createClusterService(failingPersistence, TEST_NODE_ID, PUBLIC_ADDRESS, INTERNAL_ADDRESS, bundleContext);

        IllegalStateException e = assertThrows(IllegalStateException.class, node::init);
        assertTrue(e.getMessage().contains("could not register node"), e.getMessage());
    }

    @Test
    public void testDestroyKeepsRecordRegisteredByAnotherNode() {
        ClusterServiceImpl incumbent = newNode(TEST_NODE_ID, 60000);
        incumbent.init();

        // A node with the same id that never managed to start
        newNode(TEST_NODE_ID, 60000).destroy();
        assertNotNull(persistenceService.load(TEST_NODE_ID, ClusterNode.class));

        incumbent.destroy();
        assertNull(persistenceService.load(TEST_NODE_ID, ClusterNode.class));
    }

    @Test
    public void testHeartbeatRunsOnNonExecutorNode() {
        ClusterServiceImpl apiNode = newNode("api-node", 100);
        SchedulerServiceImpl apiScheduler = TestHelper.createSchedulerService(
            "api-node", persistenceService, executionContextManager, bundleContext, apiNode, -1, false, true);
        apiNode.setSchedulerService(apiScheduler);
        try {
            apiNode.init();
            long initialHeartbeat = persistenceService.load("api-node", ClusterNode.class).getLastHeartbeat();

            // Without a heartbeat the node would be removed as stale and its id would look free
            ClusterNode updated = TestHelper.retryUntil(
                () -> persistenceService.load("api-node", ClusterNode.class),
                node -> node != null && node.getLastHeartbeat() > initialHeartbeat
            );
            assertTrue(updated.getLastHeartbeat() > initialHeartbeat);
        } finally {
            apiScheduler.preDestroy();
        }
    }

    @Test
    public void testInitDoesNotWaitWhenNodeIdIsFree() {
        // Heartbeat period of one minute: any liveness wait would blow the timeout
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            // Stale record: crashed long enough ago to be considered gone
            saveNodeRecord(TEST_NODE_ID, 1L, System.currentTimeMillis() - (3 * 60000) - 1);
            ClusterServiceImpl restarted = newNode(TEST_NODE_ID, 60000);
            restarted.init();

            // Clean shutdown removes the record, so a plain restart is immediate too
            restarted.destroy();
            newNode(TEST_NODE_ID, 60000).init();

            // Growing the cluster: a node with its own id joins next to a live one
            newNode("test-node-2", 60000).init();
        });

        assertNotNull(persistenceService.load(TEST_NODE_ID, ClusterNode.class));
        assertNotNull(persistenceService.load("test-node-2", ClusterNode.class));
    }

    @Test
    public void testHeartbeatReclaimsRecordOverwrittenByDuplicateNode() {
        ClusterServiceImpl node = newNode(TEST_NODE_ID, 60000);
        node.init();
        long startTime = persistenceService.load(TEST_NODE_ID, ClusterNode.class).getStartTime();

        // Another JVM registered under the same id after the startup check
        saveNodeRecord(TEST_NODE_ID, startTime + 1000, System.currentTimeMillis());
        node.updateSystemStats();

        assertEquals(startTime, persistenceService.load(TEST_NODE_ID, ClusterNode.class).getStartTime());
    }
}
