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
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.ClusterService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the persistence service interface
 */
public class ClusterServiceImpl implements ClusterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterServiceImpl.class.getName());

    private PersistenceService persistenceService;
    private String publicAddress;
    private String internalAddress;
    private SchedulerService schedulerService;
    private String nodeId;
    private long nodeStartTime;
    private long nodeStatisticsUpdateFrequency = 10000;
    private Map<String, Map<String, Serializable>> nodeSystemStatistics = new ConcurrentHashMap<>();

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
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

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
        
        // If we're already initialized, initialize scheduled tasks now
        // This handles the case when ClusterService was initialized before SchedulerService was set
        if (schedulerService != null && System.currentTimeMillis() > nodeStartTime && nodeStartTime > 0) {
            LOGGER.info("SchedulerService was set after ClusterService initialization, initializing scheduled tasks now");
            initializeScheduledTasks();
        }
    }

    /**
     * Unbind method for the scheduler service, called by the OSGi framework when the service is unregistered
     * @param schedulerService The scheduler service being unregistered
     */
    public void unsetSchedulerService(SchedulerService schedulerService) {
        if (this.schedulerService == schedulerService) {
            LOGGER.info("SchedulerService was unset");
            this.schedulerService = null;
        }
    }

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

        nodeStartTime = System.currentTimeMillis();

        // Register this node in the persistence service
        registerNodeInPersistence();

        // Only initialize scheduled tasks if scheduler service is available
        if (schedulerService != null) {
            initializeScheduledTasks();
        } else {
            LOGGER.warn("SchedulerService not available during ClusterService initialization. Scheduled tasks will not be registered. They will be registered when SchedulerService becomes available.");
        }

        LOGGER.info("Cluster service initialized with node ID: {}", nodeId);
    }

    /**
     * Initializes scheduled tasks for cluster management.
     * This method can be called later if schedulerService wasn't available during init.
     */
    public void initializeScheduledTasks() {
        if (schedulerService == null) {
            LOGGER.error("Cannot initialize scheduled tasks: SchedulerService is not set");
            return;
        }
        
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
        schedulerService.createRecurringTask("clusterNodeStatisticsUpdate", nodeStatisticsUpdateFrequency, TimeUnit.MILLISECONDS, statisticsTask, false);

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
        schedulerService.createRecurringTask("clusterStaleNodesCleanup", 60000, TimeUnit.MILLISECONDS, cleanupTask, false);
        
        LOGGER.info("Cluster service scheduled tasks initialized");
    }

    public void destroy() {
        // Remove this node from the persistence service
        try {
            persistenceService.remove(nodeId, ClusterNode.class);
            LOGGER.info("Node {} removed from cluster", nodeId);
        } catch (Exception e) {
            LOGGER.error("Error removing node from cluster", e);
        }
        LOGGER.info("Cluster service shutdown.");
    }

    /**
     * Register this node in the persistence service
     */
    private void registerNodeInPersistence() {
        ClusterNode clusterNode = new ClusterNode();
        clusterNode.setItemId(nodeId);
        clusterNode.setPublicHostAddress(publicAddress);
        clusterNode.setInternalHostAddress(internalAddress);
        clusterNode.setStartTime(nodeStartTime);
        clusterNode.setLastHeartbeat(System.currentTimeMillis());

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
            node.setLastHeartbeat(System.currentTimeMillis());

            // Save back to persistence
            boolean success = persistenceService.save(node);
            if (!success) {
                LOGGER.error("Failed to update node {} statistics", nodeId);
            }
        } catch (Exception e) {
            LOGGER.error("Error updating system statistics for node {}: {}", nodeId, e.getMessage(), e);
        }
    }

    /**
     * Removes stale nodes from the cluster
     */
    private void cleanupStaleNodes() {
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
        // Query all nodes from the persistence service
        return persistenceService.getAllItems(ClusterNode.class, 0, -1, null).getList();
    }

    @Override
    public void purge(Date date) {
        persistenceService.purge(date);
    }

    @Override
    public void purge(String scope) {
        persistenceService.purge(scope);
    }

}
