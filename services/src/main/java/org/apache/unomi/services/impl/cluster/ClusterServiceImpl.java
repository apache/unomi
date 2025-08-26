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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.ClusterNode;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.ServerInfo;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.ClusterService;
import org.apache.unomi.lifecycle.BundleWatcher;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.*;
import java.util.concurrent.*;

/**
 * Implementation of the persistence service interface
 */
public class ClusterServiceImpl implements ClusterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterServiceImpl.class.getName());

    private PersistenceService persistenceService;

    private String publicAddress;
    private String internalAddress;
    //private SchedulerService schedulerService; /* Wait for PR UNOMI-878 to reactivate that code
    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);
    private String nodeId;
    private long nodeStartTime;
    private long nodeStatisticsUpdateFrequency = 10000;
    private Map<String, Map<String, Serializable>> nodeSystemStatistics = new ConcurrentHashMap<>();
    private volatile boolean shutdownNow = false;
    private volatile List<ClusterNode> cachedClusterNodes = Collections.emptyList();

    private BundleWatcher bundleWatcher;
    private ScheduledFuture<?> updateSystemStatsFuture;
    private ScheduledFuture<?> cleanupStaleNodesFuture;

    /**
     * Max time to wait for persistence service (in milliseconds)
     */
    private static final long MAX_WAIT_TIME = 60000; // 60 seconds

    /**
     * Sets the bundle watcher used to retrieve server information
     *
     * @param bundleWatcher the bundle watcher
     */
    public void setBundleWatcher(BundleWatcher bundleWatcher) {
        this.bundleWatcher = bundleWatcher;
        LOGGER.info("BundleWatcher service set");
    }

    /**
     * Waits for the persistence service to become available.
     * This method will retry getting the persistence service with exponential backoff
     * until it's available or until the maximum wait time is reached.
     *
     * @throws IllegalStateException if the persistence service is not available after the maximum wait time
     */
    private void waitForPersistenceService() {
        if (shutdownNow) {
            return;
        }

        // If persistence service is directly set (e.g., in unit tests), no need to wait
        if (persistenceService != null) {
            LOGGER.debug("Persistence service is already available, no need to wait");
            return;
        }

        // Try to get the service with retries
        long startTime = System.currentTimeMillis();
        long waitTime = 50; // Start with 50ms wait time

        while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME) {
            if (persistenceService != null) {
                LOGGER.info("Persistence service is now available");
                return;
            }

            try {
                LOGGER.debug("Waiting for persistence service... ({}ms elapsed)", System.currentTimeMillis() - startTime);
                Thread.sleep(waitTime);
                // Exponential backoff with a maximum of 5 seconds
                waitTime = Math.min(waitTime * 2, 5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("Interrupted while waiting for persistence service", e);
                break;
            }
        }

        throw new IllegalStateException("PersistenceService not available after waiting " + MAX_WAIT_TIME + "ms");
    }

    /**
     * For unit tests and backward compatibility - directly sets the persistence service
     * @param persistenceService the persistence service to set
     */
    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
        LOGGER.info("PersistenceService set directly");
    }

    public void setPublicAddress(String publicAddress) {
        this.publicAddress = publicAddress;
    }

    public void setInternalAddress(String internalAddress) {
        this.internalAddress = internalAddress;
    }

    public void setNodeStatisticsUpdateFrequency(long nodeStatisticsUpdateFrequency) {
        this.nodeStatisticsUpdateFrequency = nodeStatisticsUpdateFrequency;
    }

    /* Wait for PR UNOMI-878 to reactivate that code
    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;

        // If we're already initialized, initialize scheduled tasks now
        // This handles the case when ClusterService was initialized before SchedulerService was set
        if (schedulerService != null && System.currentTimeMillis() > nodeStartTime && nodeStartTime > 0) {
            LOGGER.info("SchedulerService was set after ClusterService initialization, initializing scheduled tasks now");
            initializeScheduledTasks();
        }
    }
    */

    /* Wait for PR UNOMI-878 to reactivate that code
    /**
     * Unbind method for the scheduler service, called by the OSGi framework when the service is unregistered
     * @param schedulerService The scheduler service being unregistered
     */
    /*
    public void unsetSchedulerService(SchedulerService schedulerService) {
        if (this.schedulerService == schedulerService) {
            LOGGER.info("SchedulerService was unset");
            this.schedulerService = null;
        }
    }
    */

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public Map<String, Map<String, Serializable>> getNodeSystemStatistics() {
        return nodeSystemStatistics;
    }

    public void init() {
        // Validate that nodeId is provided
        if (StringUtils.isBlank(nodeId)) {
            String errorMessage = "CRITICAL: nodeId is not set. This is a required setting for cluster operation.";
            LOGGER.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        // Wait for persistence service to be available
        try {
            waitForPersistenceService();
        } catch (IllegalStateException e) {
            LOGGER.error("Failed to initialize cluster service: {}", e.getMessage());
            return;
        }

        nodeStartTime = System.currentTimeMillis();

        // Register this node in the persistence service
        registerNodeInPersistence();

        /* Wait for PR UNOMI-878 to reactivate that code
        /*
        // Only initialize scheduled tasks if scheduler service is available
        if (schedulerService != null) {
            initializeScheduledTasks();
        } else {
            LOGGER.warn("SchedulerService not available during ClusterService initialization. Scheduled tasks will not be registered. They will be registered when SchedulerService becomes available.");
        }
        */
        initializeScheduledTasks();

        LOGGER.info("Cluster service initialized with node ID: {}", nodeId);
    }

    /**
     * Initializes scheduled tasks for cluster management.
     * This method can be called later if schedulerService wasn't available during init.
     */
    public void initializeScheduledTasks() {
        /* Wait for PR UNOMI-878 to reactivate that code
        if (schedulerService == null) {
            LOGGER.error("Cannot initialize scheduled tasks: SchedulerService is not set");
            return;
        }
        */

        // Schedule regular updates of the node statistics
        TimerTask statisticsTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    updateSystemStats();
                } catch (Throwable t) {
                    LOGGER.error("Error updating system statistics", t);
                }
            }
        };
        /* Wait for PR UNOMI-878 to reactivate that code
        schedulerService.createRecurringTask("clusterNodeStatisticsUpdate", nodeStatisticsUpdateFrequency, TimeUnit.MILLISECONDS, statisticsTask, false);
        */
        updateSystemStatsFuture = scheduledExecutorService.scheduleAtFixedRate(statisticsTask, 100, nodeStatisticsUpdateFrequency, TimeUnit.MILLISECONDS);

        // Schedule cleanup of stale nodes
        TimerTask cleanupTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    cleanupStaleNodes();
                } catch (Throwable t) {
                    LOGGER.error("Error cleaning up stale nodes", t);
                }
            }
        };
        /* Wait for PR UNOMI-878 to reactivate that code
        schedulerService.createRecurringTask("clusterStaleNodesCleanup", 60000, TimeUnit.MILLISECONDS, cleanupTask, false);
        */
        cleanupStaleNodesFuture = scheduledExecutorService.scheduleAtFixedRate(cleanupTask, 100, 60000, TimeUnit.MILLISECONDS);

        LOGGER.info("Cluster service scheduled tasks initialized");
    }

    public void destroy() {
        LOGGER.info("Cluster service shutting down...");
        shutdownNow = true;

        // Cancel scheduled tasks
        if (updateSystemStatsFuture != null) {
            boolean successfullyCancelled = updateSystemStatsFuture.cancel(false);
            if (!successfullyCancelled) {
                LOGGER.warn("Failed to cancel scheduled task: clusterNodeStatisticsUpdate");
            } else {
                LOGGER.info("Scheduled task: clusterNodeStatisticsUpdate cancelled");
            }
        }
        if (cleanupStaleNodesFuture != null) {
            boolean successfullyCancelled = cleanupStaleNodesFuture.cancel(false);
            if (!successfullyCancelled) {
                LOGGER.warn("Failed to cancel scheduled task: cleanupStaleNodesFuture");
            } else {
                LOGGER.info("Scheduled task: cleanupStaleNodesFuture cancelled");
            }
        }
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdownNow();
            try {
                boolean successfullyTerminated = scheduledExecutorService.awaitTermination(10, TimeUnit.SECONDS);
                if (!successfullyTerminated) {
                    LOGGER.warn("Failed to terminate scheduled tasks after 10 seconds...");
                } else {
                    LOGGER.info("Scheduled tasks terminated");
                }
            } catch (InterruptedException e) {
                LOGGER.error("Error waiting for scheduled tasks to terminate", e);
            }
        }

        // Remove node from persistence service
        if (persistenceService != null) {
            try {
                persistenceService.remove(nodeId, ClusterNode.class);
                LOGGER.info("Node {} removed from cluster", nodeId);
            } catch (Exception e) {
                LOGGER.error("Error removing node from cluster", e);
            }
        }

        // Clear references
        persistenceService = null;
        bundleWatcher = null;

        LOGGER.info("Cluster service shutdown.");
    }

    /**
     * Register this node in the persistence service
     */
    private void registerNodeInPersistence() {
        if (persistenceService == null) {
            LOGGER.error("Cannot register node: PersistenceService not available");
            return;
        }

        ClusterNode clusterNode = new ClusterNode();
        clusterNode.setItemId(nodeId);
        clusterNode.setPublicHostAddress(publicAddress);
        clusterNode.setInternalHostAddress(internalAddress);
        clusterNode.setStartTime(nodeStartTime);
        clusterNode.setLastHeartbeat(System.currentTimeMillis());

        // Set server information if BundleWatcher is available
        if (bundleWatcher != null && !bundleWatcher.getServerInfos().isEmpty()) {
            ServerInfo serverInfo = bundleWatcher.getServerInfos().get(0);
            clusterNode.setServerInfo(serverInfo);
            LOGGER.info("Added server info to node: version={}, build={}",
                    serverInfo.getServerVersion(), serverInfo.getServerBuildNumber());
        } else {
            LOGGER.warn("BundleWatcher not available at registration time, server info will not be available");
        }

        updateSystemStatsForNode(clusterNode);

        boolean success = persistenceService.save(clusterNode);
        if (success) {
            LOGGER.info("Node {} registered in cluster", nodeId);
        } else {
            LOGGER.error("Failed to register node {} in cluster", nodeId);
        }
    }

    /**
     * Updates system stats for the given cluster node
     */
    private void updateSystemStatsForNode(ClusterNode node) {
        final RuntimeMXBean remoteRuntime = ManagementFactory.getRuntimeMXBean();
        long uptime = remoteRuntime.getUptime();

        double systemCpuLoad = 0.0;
        try {
            systemCpuLoad = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getSystemCpuLoad();
            // Check for NaN value which Elasticsearch and OpenSearch don't support for float fields
            if (Double.isNaN(systemCpuLoad)) {
                LOGGER.debug("System CPU load is NaN, setting to 0.0");
                systemCpuLoad = 0.0;
            }
        } catch (Exception e) {
            LOGGER.debug("Error retrieving system CPU load", e);
        }

        final OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        double systemLoadAverage = operatingSystemMXBean.getSystemLoadAverage();
        // Check for NaN value which Elasticsearch/OpenSearch doesn't support for float fields
        if (Double.isNaN(systemLoadAverage)) {
            LOGGER.debug("System load average is NaN, setting to 0.0");
            systemLoadAverage = 0.0;
        }

        node.setCpuLoad(systemCpuLoad);
        node.setUptime(uptime);

        ArrayList<Double> systemLoadAverageArray = new ArrayList<>();
        systemLoadAverageArray.add(systemLoadAverage);
        node.setLoadAverage(ArrayUtils.toPrimitive(systemLoadAverageArray.toArray(new Double[0])));

        // Store system statistics in memory as well
        Map<String, Serializable> systemStatistics = new TreeMap<>();
        systemStatistics.put("systemLoadAverage", systemLoadAverageArray);
        systemStatistics.put("systemCpuLoad", systemCpuLoad);
        systemStatistics.put("uptime", uptime);
        nodeSystemStatistics.put(nodeId, systemStatistics);
    }

    /**
     * Updates the system statistics for this node and stores them in the persistence service
     */
    private void updateSystemStats() {
        if (shutdownNow) {
            return;
        }

        if (persistenceService == null) {
            LOGGER.warn("Cannot update system stats: PersistenceService not available");
            return;
        }

        // Load node from persistence
        ClusterNode node = persistenceService.load(nodeId, ClusterNode.class);
        if (node == null) {
            LOGGER.warn("Node {} not found in persistence, re-registering", nodeId);
            registerNodeInPersistence();
            return;
        }

        try {
            // Update its stats
            updateSystemStatsForNode(node);

            // Update server info if needed
            if (bundleWatcher != null && !bundleWatcher.getServerInfos().isEmpty()) {
                ServerInfo currentInfo = bundleWatcher.getServerInfos().get(0);
                // Check if server info needs updating
                if (node.getServerInfo() == null ||
                        !currentInfo.getServerVersion().equals(node.getServerInfo().getServerVersion())) {

                    node.setServerInfo(currentInfo);
                    LOGGER.info("Updated server info for node {}: version={}, build={}",
                            nodeId, currentInfo.getServerVersion(), currentInfo.getServerBuildNumber());
                }
            }

            node.setLastHeartbeat(System.currentTimeMillis());

            // Save back to persistence
            boolean success = persistenceService.save(node);
            if (!success) {
                LOGGER.error("Failed to update node {} statistics", nodeId);
            }

            // Always refresh cluster nodes cache after attempting stats update
            try {
                List<ClusterNode> nodes = persistenceService.getAllItems(ClusterNode.class, 0, -1, null).getList();
                cachedClusterNodes = nodes;
            } catch (Exception e) {
                LOGGER.warn("Failed to refresh cluster nodes cache during stats update", e);
            }
        } catch (Exception e) {
            LOGGER.error("Error updating system statistics for node {}: {}", nodeId, e.getMessage(), e);
        }
    }

    /**
     * Removes stale nodes from the cluster
     */
    private void cleanupStaleNodes() {
        if (shutdownNow) {
            return;
        }

        if (persistenceService == null) {
            LOGGER.warn("Cannot cleanup stale nodes: PersistenceService not available");
            return;
        }

        long cutoffTime = System.currentTimeMillis() - (nodeStatisticsUpdateFrequency * 3); // Node is stale if no heartbeat for 3x the update frequency

        Condition staleNodesCondition = new Condition();
        ConditionType propertyConditionType = new ConditionType();
        propertyConditionType.setItemId("propertyCondition");
        propertyConditionType.setItemType(ConditionType.ITEM_TYPE);
        propertyConditionType.setConditionEvaluator("propertyConditionEvaluator");
        propertyConditionType.setQueryBuilder("propertyConditionESQueryBuilder");
        staleNodesCondition.setConditionType(propertyConditionType);
        staleNodesCondition.setConditionTypeId("propertyCondition");
        staleNodesCondition.setParameter("propertyName", "lastHeartbeat");
        staleNodesCondition.setParameter("comparisonOperator", "lessThan");
        staleNodesCondition.setParameter("propertyValueInteger", cutoffTime);

        PartialList<ClusterNode> staleNodes = persistenceService.query(staleNodesCondition, null, ClusterNode.class, 0, -1);

        for (ClusterNode staleNode : staleNodes.getList()) {
            LOGGER.info("Removing stale node: {}", staleNode.getItemId());
            persistenceService.remove(staleNode.getItemId(), ClusterNode.class);
            nodeSystemStatistics.remove(staleNode.getItemId());
        }
    }

    @Override
    public List<ClusterNode> getClusterNodes() {
        // Return cached cluster nodes, creating a defensive copy
        return cachedClusterNodes.isEmpty() ? Collections.emptyList() : new ArrayList<>(cachedClusterNodes);
    }

    @Override
    public void purge(Date date) {
        if (persistenceService == null) {
            LOGGER.warn("Cannot purge by date: PersistenceService not available");
            return;
        }

        persistenceService.purge(date);
    }

    @Override
    public void purge(String scope) {
        if (persistenceService == null) {
            LOGGER.warn("Cannot purge by scope: PersistenceService not available");
            return;
        }

        persistenceService.purge(scope);
    }

    /**
     * Check if a persistence service is available.
     * This can be used to quickly check before performing operations.
     *
     * @return true if a persistence service is available (either directly set or via tracker)
     */
    public boolean isPersistenceServiceAvailable() {
        return persistenceService != null;
    }
}

