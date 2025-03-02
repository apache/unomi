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
package org.apache.unomi.services.impl.scheduler;

import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.conditions.ConditionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages task locks to coordinate execution in a cluster environment.
 * This class ensures that tasks which don't allow parallel execution
 * only run on a single node at a time.
 * 
 * <p>Distributed Locking Strategy:</p>
 * 
 * <p>This implementation addresses the challenge of reliable distributed locking
 * with Elasticsearch, which is an eventually consistent system. The primary goal
 * is to ensure that only one node in the cluster acquires a lock at any time,
 * even if multiple nodes attempt to acquire it simultaneously.</p>
 * 
 * <p>Key features of the locking implementation:</p>
 * <ul>
 *   <li><b>Node Affinity</b>: Each task is assigned a primary node based on its ID hash,
 *       reducing contention by giving priority to specific nodes for specific tasks.
 *       Active nodes are detected using the ClusterService and fall back to task lock analysis
 *       if ClusterService is unavailable.</li>
 *   <li><b>Time Windows</b>: Primary nodes get an exclusive time window to acquire locks,
 *       after which backup nodes attempt in sequence.</li>
 *   <li><b>Optimistic Concurrency Control</b>: Uses Elasticsearch's sequence numbers and
 *       primary terms to ensure only one update succeeds when multiple nodes attempt
 *       simultaneous updates.</li>
 *   <li><b>Fencing Tokens</b>: Monotonically increasing version numbers prevent split-brain
 *       scenarios where multiple nodes believe they own a lock.</li>
 *   <li><b>Lock Verification</b>: Double-checking after acquiring a lock ensures it's
 *       still valid after changes have propagated through the cluster.</li>
 *   <li><b>Explicit Refreshes</b>: Forces immediate index refreshes to make lock
 *       information visible more quickly to other nodes.</li>
 * </ul>
 * 
 * <p>Different strategies are used for different task types:</p>
 * <ul>
 *   <li>Tasks that allow parallel execution: Simple locking without exclusivity</li>
 *   <li>Non-persistent tasks: Simple in-memory locking (these exist only on one node)</li>
 *   <li>Persistent tasks: Robust distributed locking with all safeguards</li>
 * </ul>
 */
public class TaskLockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskLockManager.class);
    private static final String SEQ_NO = "_seq_no";
    private static final String PRIMARY_TERM = "_primary_term";
    private static final String LOCK_VERSION = "lockVersion";
    private static final long VERIFICATION_DELAY_MS = 100;
    private static final long PRIMARY_NODE_WINDOW_MS = 3000;
    private static final long BACKUP_NODE_WINDOW_MS = 500;

    private final String nodeId;
    private final long lockTimeout;
    private final TaskMetricsManager metricsManager;
    private final SchedulerServiceImpl schedulerService;

    public TaskLockManager(String nodeId, long lockTimeout,  
                          TaskMetricsManager metricsManager, SchedulerServiceImpl schedulerService) {
        this.nodeId = nodeId;
        this.lockTimeout = lockTimeout;
        this.metricsManager = metricsManager;
        this.schedulerService = schedulerService;
    }

    /**
     * Acquires a lock for the specified task.
     * Uses optimistic concurrency control to ensure only one node successfully acquires a lock.
     * 
     * Note: This implementation uses Elasticsearch/OpenSearch documents as distributed locks.
     * The refresh policy for ScheduledTask documents is configured to use WAIT_UNTIL/WaitForRefresh
     * to ensure that lock changes are immediately visible to all nodes without requiring 
     * explicit refresh calls.
     *
     * @param task The task to lock
     * @return true if the lock was successfully acquired, false otherwise
     */
    public boolean acquireLock(ScheduledTask task) {
        if (task == null) {
            return false;
        }

        // Always allow tasks that permit parallel execution
        if (task.isAllowParallelExecution()) {
            // Just set lock info but don't enforce exclusivity
            task.setLockOwner(nodeId);
            task.setLockDate(new Date());
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED);
            return true;
        }

        // For non-persistent tasks, use simple in-memory locking
        if (!task.isPersistent()) {
            return acquireInMemoryLock(task);
        }

        // For persistent tasks, use robust distributed locking
        return acquireDistributedLock(task);
    }

    /**
     * Simple in-memory locking for non-persistent tasks.
     * These tasks exist only on a single node, so we don't need
     * complex distributed locking.
     */
    private boolean acquireInMemoryLock(ScheduledTask task) {
        if (task.getLockOwner() != null && !nodeId.equals(task.getLockOwner())) {
            if (!isLockExpired(task)) {
                return false;
            }
        }
        
        task.setLockOwner(nodeId);
        task.setLockDate(new Date());
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED);
        
        // For non-persistent tasks, we just update the in-memory map
        schedulerService.saveTask(task);
        return true;
    }

    /**
     * Robust distributed locking for persistent tasks.
     * This handles the case where multiple nodes might try to
     * acquire the lock at the same time.
     */
    private boolean acquireDistributedLock(ScheduledTask task) {
        // Step 1: Check if this node should handle this task based on affinity
        if (!shouldHandleTask(task)) {
            return false;
        }
        
        // Step 2: Force a refresh to ensure we see the latest state
        schedulerService.refreshTasks();
        
        // Step 3: Get the latest version using GET by ID (not search)
        ScheduledTask latestTask = schedulerService.getTask(task.getItemId());
        if (latestTask == null) {
            LOGGER.warn("Task {} not found when attempting to lock", task.getItemId());
            return false;
        }
        
        // Step 4: Check if already locked by another node
        if (latestTask.getLockOwner() != null && 
            !nodeId.equals(latestTask.getLockOwner()) && 
            !isLockExpired(latestTask)) {
            LOGGER.debug("Task {} already locked by {}", task.getItemId(), latestTask.getLockOwner());
            return false;
        }
        
        // Step 5: Use optimistic concurrency control with sequence numbers
        task.setSystemMetadata(SEQ_NO, latestTask.getSystemMetadata(SEQ_NO));
        task.setSystemMetadata(PRIMARY_TERM, latestTask.getSystemMetadata(PRIMARY_TERM));
        
        // Step 6: Set lock information
        task.setLockOwner(nodeId);
        task.setLockDate(new Date());
        
        // Step 7: Add a monotonically increasing fencing token
        Long lockVersion = (Long) latestTask.getSystemMetadata(LOCK_VERSION);
        long newLockVersion = (lockVersion == null) ? 1L : lockVersion + 1L;
        task.setSystemMetadata(LOCK_VERSION, newLockVersion);
        
        // Step 8: Save with WAIT_UNTIL refresh policy
        boolean acquired = schedulerService.saveTaskWithRefresh(task);
        
        if (!acquired) {
            LOGGER.debug("Failed to acquire lock for task {} due to version conflict", task.getItemId());
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
            return false;
        }
        
        // Step 9: Double-check our lock after a delay to ensure it's still valid
        try {
            // Wait for a short time to allow any concurrent operations to complete
            Thread.sleep(VERIFICATION_DELAY_MS);
            
            // Force refresh again to ensure we see the latest state
            schedulerService.refreshTasks();
            
            // Get the task again to verify our lock
            ScheduledTask verifiedTask = schedulerService.getTask(task.getItemId());
            if (verifiedTask == null) {
                LOGGER.warn("Task {} disappeared after locking", task.getItemId());
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
                return false;
            }
            
            // Verify we're still the lock owner
            if (!nodeId.equals(verifiedTask.getLockOwner())) {
                LOGGER.warn("Lost lock ownership for task {} to {}", 
                          task.getItemId(), verifiedTask.getLockOwner());
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
                return false;
            }
            
            // Verify our fencing token is still the highest
            Long currentToken = (Long) verifiedTask.getSystemMetadata(LOCK_VERSION);
            if (currentToken == null || currentToken != newLockVersion) {
                LOGGER.warn("Lock version mismatch for task {}: expected {} but found {}", 
                          task.getItemId(), newLockVersion, currentToken);
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
                return false;
            }
            
            // Lock successfully verified
            LOGGER.debug("Successfully acquired and verified lock for task {}", task.getItemId());
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Attempt to release the lock since we're being interrupted
            releaseLock(task);
            return false;
        }
    }

    /**
     * Determines if this node should handle the given task based on node affinity.
     * This reduces contention by giving priority to a specific node for each task.
     */
    private boolean shouldHandleTask(ScheduledTask task) {
        // Check if this is a scheduled task
        Date scheduledTime = task.getNextScheduledExecution();
        if (scheduledTime == null) {
            // Not a scheduled task, any node can handle it
            return true;
        }
        
        // Get list of active nodes (sorted for consistency)
        List<String> activeNodes = schedulerService.getActiveNodes();
        if (activeNodes.isEmpty() || activeNodes.size() == 1) {
            // If we're the only node or can't determine active nodes, always handle the task
            return true;
        }
        Collections.sort(activeNodes);
        
        // Calculate primary node based on task hash
        int primaryIndex = Math.abs(task.getItemId().hashCode() % activeNodes.size());
        String primaryNode = activeNodes.get(primaryIndex);
        
        // If we're the primary node, always attempt
        if (nodeId.equals(primaryNode)) {
            return true;
        }
        
        // Check if enough time has passed to allow backup nodes
        long delayMs = System.currentTimeMillis() - scheduledTime.getTime();
        
        // Primary node gets exclusive window
        if (delayMs < PRIMARY_NODE_WINDOW_MS) {
            return false;
        }
        
        // Calculate our position as a backup node
        int ourIndex = activeNodes.indexOf(nodeId);
        if (ourIndex < 0) {
            return false; // Not in active nodes list
        }
        
        // Calculate backup order (relative position after primary)
        int backupOrder = (ourIndex - primaryIndex + activeNodes.size()) % activeNodes.size();
        
        // Each backup node gets a time window based on their order
        long ourWindowStart = PRIMARY_NODE_WINDOW_MS + ((backupOrder - 1) * BACKUP_NODE_WINDOW_MS);
        long ourWindowEnd = ourWindowStart + BACKUP_NODE_WINDOW_MS;
        
        return delayMs >= ourWindowStart && delayMs < ourWindowEnd;
    }

    /**
     * Releases a lock on the given task.
     *
     * @param task Task to unlock
     * @return true if unlock was successful
     */
    public boolean releaseLock(ScheduledTask task) {
        if (task == null) {
            return false;
        }

        // Only allow the lock owner to release the lock
        if (task.getLockOwner() != null && !nodeId.equals(task.getLockOwner())) {
            LOGGER.warn("Node {} attempted to release a lock owned by {}", nodeId, task.getLockOwner());
            return false;
        }

        try {
            task.setLockOwner(null);
            task.setLockDate(null);
            
            if (!schedulerService.saveTask(task)) {
                LOGGER.error("Failed to release lock for task {}", task.getItemId());
                return false;
            }
            
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_RELEASED);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error releasing lock for task {}: {}", task.getItemId(), e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a task's lock has expired based on timeout.
     *
     * @param task Task to check
     * @return true if lock has expired or if task has no lock
     */
    public boolean isLockExpired(ScheduledTask task) {
        if (task == null || task.getLockDate() == null) {
            return true;
        }

        long lockAge = System.currentTimeMillis() - task.getLockDate().getTime();
        return lockAge > lockTimeout;
    }
}
